package com.opah.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.NameParser;
import com.opah.domain.BuildEntity;
import com.opah.domain.BuildLogEntity;
import com.opah.domain.BuildLogRepository;
import com.opah.domain.BuildRepository;
import com.opah.domain.ServiceEntity;
import com.opah.domain.ServiceRepository;
import com.opah.infra.DockerClientFactory;
import com.opah.infra.GitService;
import com.opah.api.ws.BuildLogPublisher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 构建服务（BUILD-01/02/03/04）：
 * PENDING 入队 → 虚拟线程 worker 消费 → JGit checkout → 模板渲染 → docker build
 * 日志逐行落库 + WebSocket 推送；versionTag = {yyyyMMddHHmmss}-{commit短hash}。
 */
@Service
public class BuildService {

    private static final Logger log = LoggerFactory.getLogger(BuildService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter VTS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BuildRepository builds;
    private final BuildLogRepository logs;
    private final ServiceRepository services;
    private final GitService git;
    private final DockerClientFactory dockerFactory;
    private final TemplateService templates;
    private final BuildLogPublisher publisher;
    private final AuditService audit;
    private final Semaphore slots;
    private final Set<Long> runningServices = ConcurrentHashMap.newKeySet();
    private final Map<Long, AtomicInteger> lineCounters = new ConcurrentHashMap<>();

    public BuildService(BuildRepository builds, BuildLogRepository logs,
                        ServiceRepository services, GitService git,
                        DockerClientFactory dockerFactory, TemplateService templates,
                        BuildLogPublisher publisher, AuditService audit,
                        @Value("${opah.build.concurrency:2}") int concurrency) {
        this.builds = builds;
        this.logs = logs;
        this.services = services;
        this.git = git;
        this.dockerFactory = dockerFactory;
        this.templates = templates;
        this.publisher = publisher;
        this.audit = audit;
        this.slots = new Semaphore(concurrency);
    }

    /** 触发构建（同单元进行中则直接返回该构建） */
    public BuildEntity trigger(Long serviceId, String ref, String username) {
        ServiceEntity svc = services.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("部署单元不存在: " + serviceId));
        synchronized (this) {
            if (runningServices.contains(serviceId)) {
                return builds.findFirstByServiceIdAndStatusOrderByIdDesc(serviceId, "RUNNING")
                        .orElseThrow(() -> new IllegalStateException("构建进行中但记录缺失"));
            }
            var conflict = builds.findFirstByServiceIdAndStatusOrderByIdDesc(serviceId, "PENDING").orElse(null);
            if (conflict != null) {
                return conflict;
            }
        }
        BuildEntity b = new BuildEntity();
        b.setServiceId(serviceId);
        b.setStatus("PENDING");
        b.setTriggeredBy(username);
        b.setQueuedAt(now());
        b = builds.save(b);
        audit.record("BUILD_TRIGGER", "service", String.valueOf(serviceId), "buildId=" + b.getId());
        final Long buildId = b.getId();
        final String refToUse = ref != null ? ref : "main";
        Thread.ofVirtual().name("opah-build-" + buildId).start(() -> execute(buildId, refToUse));
        return b;
    }

    private void execute(Long buildId, String ref) {
        BuildEntity b = builds.findById(buildId).orElse(null);
        if (b == null) return;
        Long serviceId = b.getServiceId();
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        runningServices.add(serviceId);
        long started = System.currentTimeMillis();
        try {
            b.setStatus("RUNNING");
            b.setStartedAt(now());
            b = builds.save(b);
            publisher.publishState(b);

            ServiceEntity svc = services.findById(serviceId).orElseThrow();
            appendLog(buildId, ">>> Opah build started: unit=" + svc.getName() + " ref=" + ref);

            // 1. Git fetch + checkout
            appendLog(buildId, "[git] fetching " + ref + " ...");
            GitService.CheckoutResult checkout = git.checkoutWork(
                    svc.getProjectId(), serviceId, ref);
            appendLog(buildId, "[git] checked out " + checkout.commitSha().substring(0, 8)
                    + " (" + checkout.shortMessage() + ")");
            b.setCommitSha(checkout.commitSha());
            b.setCommitMsg(checkout.shortMessage());
            b.setVersionTag(buildVersionTag(checkout.commitSha()));
            b = builds.save(b);

            // 2. 渲染构建上下文（Dockerfile / nginx.conf）
            Path unitDir = git.unitWorkDir(svc.getProjectId(), serviceId).resolve(svc.getSubPath().equals(".") ? "" : svc.getSubPath());
            appendLog(buildId, "[template] preparing build context at " + unitDir);
            renderContext(svc, unitDir, buildId);

            // 3. docker build
            appendLog(buildId, "[docker] building image " + imageRepo(svc) + ":" + b.getVersionTag());
            String imageId = dockerBuild(unitDir, imageRepo(svc), b.getVersionTag(), buildId);

            appendLog(buildId, ">>> build succeeded, image=" + imageRepo(svc) + ":" + b.getVersionTag());
            b.setStatus("SUCCESS");
            b.setDurationMs(System.currentTimeMillis() - started);
            b.setFinishedAt(now());
            b.setLogExcerpt(lastLines(buildId, 3));
            builds.save(b);
            publisher.publishState(b);
            audit.record("BUILD_SUCCESS", "service", String.valueOf(serviceId),
                    "buildId=" + buildId + " tag=" + b.getVersionTag());
        } catch (Exception e) {
            log.error("build {} failed", buildId, e);
            appendLog(buildId, ">>> build FAILED: " + e.getMessage());
            b.setStatus("FAILED");
            b.setErrorMsg(truncate(e.getMessage(), 500));
            b.setDurationMs(System.currentTimeMillis() - started);
            b.setFinishedAt(now());
            b.setLogExcerpt(lastLines(buildId, 3));
            builds.save(b);
            publisher.publishState(b);
            audit.record("BUILD_FAILED", "service", String.valueOf(serviceId), "buildId=" + buildId);
        } finally {
            runningServices.remove(serviceId);
            lineCounters.remove(buildId);
            slots.release();
        }
    }

    private void renderContext(ServiceEntity svc, Path unitDir, Long buildId) throws Exception {
        String type = svc.getType();
        if ("CUSTOM".equals(type) || "COMPOSE".equals(type)) {
            appendLog(buildId, "[template] unit type " + type + ", using project files as-is");
            return;
        }
        if (Files.exists(unitDir.resolve("Dockerfile"))) {
            appendLog(buildId, "[template] unit has its own Dockerfile, skip rendering");
            return;
        }
        Map<String, Object> params = new HashMap<>();
        switch (type) {
            case "JAVA" -> {
                params.put("baseImage", "maven:3.9-eclipse-temurin-21");
                params.put("runtimeImage", "eclipse-temurin:21-jre");
                params.put("port", "8080");
                params.put("useWrapper", Files.exists(unitDir.resolve("mvnw")));
                templates.renderToFile("java-springboot.df.hbs", params, unitDir.resolve("Dockerfile"));
                appendLog(buildId, "[template] rendered java-springboot Dockerfile");
            }
            case "REACT", "VUE" -> {
                params.put("framework", type.toLowerCase());
                params.put("nodeVersion", "20");
                params.put("nginxVersion", "1.27");
                params.put("buildCommand", "npm run build");
                params.put("distDir", "REACT".equals(type) ? "dist" : "dist");
                params.put("hasPackageLock", Files.exists(unitDir.resolve("package-lock.json")));
                params.put("npmRegistry", registryFor(unitDir));
                templates.renderToFile("node-nginx.df.hbs", params, unitDir.resolve("Dockerfile"));
                // nginx.conf（CONF-02 配置渲染进镜像，T3 决策）
                renderNginxConf(svc, unitDir, buildId);
            }
            default -> throw new IllegalStateException("不支持的单元类型: " + type);
        }
    }

    private String registryFor(Path unitDir) {
        try {
            if (Files.exists(unitDir.resolve(".npmrc"))) {
                for (String line : Files.readAllLines(unitDir.resolve(".npmrc"))) {
                    if (line.startsWith("registry=")) {
                        return line.substring("registry=".length()).trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "https://registry.npmmirror.com";
    }

    private void renderNginxConf(ServiceEntity svc, Path unitDir, Long buildId) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("historyMode", true);   // 默认 history 路由
        params.put("proxies", java.util.List.of());  // CONF-02 反代配置后续从 nginx_config 读取
        templates.renderToFile("nginx.conf.hbs", params, unitDir.resolve("nginx.conf"));
        appendLog(buildId, "[template] rendered nginx.conf");
    }

    private String dockerBuild(Path context, String repo, String tag, Long buildId) throws Exception {
        DockerClient client = dockerFactory.client();
        String fullImage = repo + ":" + tag;
        AtomicInteger errorCount = new AtomicInteger();
        String imageId = client.buildImageCmd()
                .withDockerfile(context.resolve("Dockerfile").toFile())
                .withBaseDirectory(context.toFile())
                .withTag(fullImage)
                .exec(new BuildImageResultCallback() {
                    @Override
                    public void onNext(BuildResponseItem item) {
                        super.onNext(item);
                        if (item.getStream() != null) {
                            String line = item.getStream().stripTrailing();
                            if (!line.isEmpty()) {
                                appendLog(buildId, line);
                            }
                        }
                    }
                })
                .awaitImageId(30, java.util.concurrent.TimeUnit.MINUTES);
        if (imageId == null) {
            throw new IllegalStateException("docker build 未返回镜像 ID（可能失败）");
        }
        return imageId;
    }

    public String imageRepo(ServiceEntity svc) {
        return "opah/" + svc.getProjectId() + "-" + svc.getId() + "-" + slug(svc.getName());
    }

    private String buildVersionTag(String commitSha) {
        String shortSha = commitSha != null && commitSha.length() >= 8
                ? commitSha.substring(0, 8) : "nosha";
        return LocalDateTime.now().format(VTS) + "-" + shortSha;
    }

    private void appendLog(Long buildId, String line) {
        try {
            int lineNo = lineCounters.computeIfAbsent(buildId, k -> new AtomicInteger(0)).incrementAndGet();
            BuildLogEntity e = new BuildLogEntity();
            e.setBuildId(buildId);
            e.setLineNo(lineNo);
            e.setContent(truncate(line, 4000));
            e.setIsStderr(line.contains("ERROR") ? 1 : 0);
            e.setCreatedAt(now());
            logs.save(e);
            publisher.publishLog(buildId, lineNo, line);
        } catch (Exception ex) {
            log.warn("append log failed: {}", ex.getMessage());
        }
    }

    private String lastLines(Long buildId, int n) {
        var list = logs.findByBuildIdOrderByLineNoAsc(buildId);
        if (list.isEmpty()) return "";
        return list.subList(Math.max(0, list.size() - n), list.size()).stream()
                .map(BuildLogEntity::getContent)
                .reduce((a, c) -> a + "\n" + c)
                .orElse("");
    }

    private String slug(String name) {
        return name == null ? "svc" : name.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }

    private String now() {
        return LocalDateTime.now().format(TS);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
