package com.opah.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.opah.domain.BuildEntity;
import com.opah.domain.BuildRepository;
import com.opah.domain.ConfigEntryEntity;
import com.opah.domain.ConfigEntryRepository;
import com.opah.domain.ConfigFileEntity;
import com.opah.domain.ConfigFileRepository;
import com.opah.domain.DeploymentEntity;
import com.opah.domain.DeploymentRepository;
import com.opah.domain.HostEntity;
import com.opah.domain.HostRepository;
import com.opah.domain.ServiceEntity;
import com.opah.domain.ServiceRepository;
import com.opah.infra.CryptoService;
import com.opah.infra.DockerClientFactory;
import com.opah.infra.SshClientManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 部署服务（DEPLOY-01/02/03/04）：
 * ① 冻结 config_snapshot → ② save/load 分发（主机已有同 tag 镜像则跳过）
 * → ③ 配置文件 SFTP 上传 → ④ 停旧起新 → ⑤ 等待 running，失败自动回退旧容器。
 */
@Service
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeploymentRepository deployments;
    private final BuildRepository builds;
    private final ServiceRepository services;
    private final HostRepository hosts;
    private final ConfigEntryRepository configEntries;
    private final ConfigFileRepository configFiles;
    private final com.opah.domain.CredentialRepository credentials;
    private final DockerClientFactory dockerFactory;
    private final SshClientManager ssh;
    private final CryptoService crypto;
    private final AuditService audit;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long healthTimeoutSeconds;

    public DeployService(DeploymentRepository deployments, BuildRepository builds,
                         ServiceRepository services, HostRepository hosts,
                         ConfigEntryRepository configEntries, ConfigFileRepository configFiles,
                         com.opah.domain.CredentialRepository credentials,
                         DockerClientFactory dockerFactory, SshClientManager ssh,
                         CryptoService crypto, AuditService audit,
                         @Value("${opah.deploy.health-timeout-seconds:30}") long healthTimeoutSeconds) {
        this.deployments = deployments;
        this.builds = builds;
        this.services = services;
        this.hosts = hosts;
        this.configEntries = configEntries;
        this.configFiles = configFiles;
        this.credentials = credentials;
        this.dockerFactory = dockerFactory;
        this.ssh = ssh;
        this.crypto = crypto;
        this.audit = audit;
        this.healthTimeoutSeconds = healthTimeoutSeconds;
    }

    /** 部署请求参数 */
    public record DeployRequest(Long buildId, Long hostId, Map<String, String> envOverrides,
                                List<PortMapping> ports, String restartPolicy) {
    }

    public record PortMapping(String hostPort, String containerPort) {
    }

    /** 执行部署（异步，立即返回 deployment 记录） */
    public DeploymentEntity deploy(Long serviceId, DeployRequest req, String username) {
        ServiceEntity svc = services.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("单元不存在"));
        BuildEntity build = builds.findById(req.buildId())
                .orElseThrow(() -> new IllegalArgumentException("构建不存在"));
        if (!"SUCCESS".equals(build.getStatus())) {
            throw new IllegalStateException("仅 SUCCESS 构建可部署");
        }
        HostEntity host = hosts.findById(req.hostId())
                .orElseThrow(() -> new IllegalArgumentException("主机不存在"));

        DeploymentEntity d = new DeploymentEntity();
        d.setServiceId(serviceId);
        d.setBuildId(req.buildId());
        d.setHostId(req.hostId());
        d.setStatus("RUNNING");
        d.setStartedBy(username);
        d.setStartedAt(now());
        d.setConfigSnapshot(freezeSnapshot(svc, req));
        d = deployments.save(d);
        audit.record("DEPLOY", "service", String.valueOf(serviceId),
                "deploymentId=" + d.getId() + " host=" + host.getName());

        final Long depId = d.getId();
        Thread.ofVirtual().name("opah-deploy-" + depId).start(() -> {
            try {
                execute(depId);
            } catch (Exception e) {
                log.error("deployment {} failed", depId, e);
                failDeployment(depId, e.getMessage());
            }
        });
        return d;
    }

    private void execute(Long depId) throws Exception {
        DeploymentEntity d = deployments.findById(depId).orElseThrow();
        ServiceEntity svc = services.findById(d.getServiceId()).orElseThrow();
        BuildEntity build = builds.findById(d.getBuildId()).orElseThrow();
        HostEntity host = hosts.findById(d.getHostId()).orElseThrow();
        SshClientManager.HostAuth auth = hostAuthFor(host);

        String image = "opah/" + svc.getProjectId() + "-" + svc.getId() + "-" + slug(svc.getName())
                + ":" + build.getVersionTag();
        String containerName = "opah-" + svc.getProjectId() + "-" + svc.getId() + "-" + slug(svc.getName());

        // ① 镜像探测：主机已有同 tag 则跳过分发
        boolean alreadyLoaded = ssh.execute(auth,
                "docker image inspect " + image + " >/dev/null 2>&1 && echo yes || echo no",
                Duration.ofSeconds(30)).stdout().equals("yes");

        if (!alreadyLoaded) {
            // ② docker save | ssh docker load 流式分发
            distribute(auth, image);
        }

        // ③ 配置文件上传
        Map<String, Object> snapshot = mapper.readValue(d.getConfigSnapshot(), Map.class);
        uploadConfigFiles(auth, svc, snapshot);

        // ④ 停旧起新（记录旧容器 id 用于失败回退）
        String oldContainerId = findContainer(auth, containerName);
        String newContainerId = startContainer(auth, image, containerName, svc, snapshot);

        // ⑤ 等待 running
        boolean healthy = waitRunning(auth, newContainerId, Duration.ofSeconds(healthTimeoutSeconds));
        if (!healthy) {
            appendError(d, "新容器未在 " + healthTimeoutSeconds + "s 内进入 running");
            rollbackContainers(auth, containerName, oldContainerId, newContainerId);
            failDeployment(depId, "health check failed");
            return;
        }

        d.setStatus("SUCCESS");
        d.setFinishedAt(now());
        deployments.save(d);
        svc.setCurrentBuildId(build.getId());
        services.save(svc);
        audit.record("DEPLOY_SUCCESS", "service", String.valueOf(svc.getId()),
                "deploymentId=" + depId + " image=" + image);
    }

    /** docker save 流式导出 → SSH 管道 docker load（不落地完整 tar） */
    private void distribute(SshClientManager.HostAuth auth, String image) throws Exception {
        try (java.io.InputStream imageTar = dockerFactory.client().saveImageCmd(image).exec()) {
            SshClientManager.SshResult r = ssh.pipe(auth, "docker load", imageTar, Duration.ofMinutes(30));
            if (!r.ok()) {
                throw new IllegalStateException("docker load 失败: " + r.stderr());
            }
        }
    }

    private void uploadConfigFiles(SshClientManager.HostAuth auth, ServiceEntity svc,
                                   Map<String, Object> snapshot) throws Exception {
        Object files = snapshot.get("configFiles");
        if (!(files instanceof Map<?, ?> fileMap) || fileMap.isEmpty()) {
            return;
        }
        String baseDir = "/opt/opah/projects/" + svc.getProjectId() + "/" + svc.getId() + "/config";
        for (Map.Entry<?, ?> e : fileMap.entrySet()) {
            String path = String.valueOf(e.getKey());
            String content = String.valueOf(e.getValue());
            ssh.upload(auth, baseDir + "/" + path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @SuppressWarnings("unchecked")
    public String startContainer(SshClientManager.HostAuth auth, String image, String containerName,
                                  ServiceEntity svc, Map<String, Object> snapshot) throws Exception {
        StringBuilder cmd = new StringBuilder("docker run -d");
        cmd.append(" --name ").append(containerName);
        cmd.append(" --label opah.managed=true");
        cmd.append(" --label opah.serviceId=").append(svc.getId());
        cmd.append(" --label opah.projectId=").append(svc.getProjectId());
        String restart = snapshot.get("restartPolicy") == null ? "unless-stopped" : String.valueOf(snapshot.get("restartPolicy"));
        cmd.append(" --restart ").append(restart);

        Object ports = snapshot.get("ports");
        if (ports instanceof List<?> portList) {
            for (Object p : portList) {
                Map<String, String> pm = (Map<String, String>) p;
                cmd.append(" -p ").append(pm.get("hostPort")).append(":").append(pm.get("containerPort"));
            }
        }
        Map<String, Object> env = (Map<String, Object>) snapshot.get("env");
        if (env != null) {
            for (Map.Entry<String, Object> e : env.entrySet()) {
                cmd.append(" -e '").append(e.getKey()).append("=").append(e.getValue()).append("'");
            }
        }
        boolean hasConfigFiles = snapshot.get("configFiles") instanceof Map<?, ?> m && !m.isEmpty();
        if (hasConfigFiles) {
            String baseDir = "/opt/opah/projects/" + svc.getProjectId() + "/" + svc.getId() + "/config";
            cmd.append(" -v ").append(baseDir).append(":/opah/config");
            if ("JAVA".equals(svc.getType())) {
                cmd.append(" -e SPRING_CONFIG_ADDITIONAL_LOCATION=/opah/config/");
            }
        }
        cmd.append(" ").append(image);
        SshClientManager.SshResult r = ssh.execute(auth, cmd.toString(), Duration.ofMinutes(2));
        if (!r.ok()) {
            throw new IllegalStateException("docker run 失败: " + r.stderr());
        }
        return r.stdout().trim();
    }

    public String findContainer(SshClientManager.HostAuth auth, String containerName) {
        SshClientManager.SshResult r = ssh.execute(auth,
                "docker ps -a --filter name=^/" + containerName + "$ --format {{.ID}}",
                Duration.ofSeconds(30));
        return r.stdout().isBlank() ? null : r.stdout().trim();
    }

    public boolean waitRunning(SshClientManager.HostAuth auth, String containerId, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            SshClientManager.SshResult r = ssh.execute(auth,
                    "docker inspect -f {{.State.Running}} " + containerId, Duration.ofSeconds(15));
            if ("true".equals(r.stdout().trim())) {
                return true;
            }
            // 容器已退出则提前失败
            SshClientManager.SshResult state = ssh.execute(auth,
                    "docker inspect -f {{.State.Status}} " + containerId, Duration.ofSeconds(15));
            if ("exited".equals(state.stdout().trim()) || "dead".equals(state.stdout().trim())) {
                return false;
            }
        }
        return false;
        // 旧容器处理在调用方
    }

    public void rollbackContainers(SshClientManager.HostAuth auth, String containerName,
                                    String oldContainerId, String newContainerId) {
        try {
            if (newContainerId != null) {
                ssh.execute(auth, "docker rm -f " + newContainerId, Duration.ofSeconds(30));
            }
            if (oldContainerId != null) {
                ssh.execute(auth, "docker start " + oldContainerId, Duration.ofSeconds(60));
            }
        } catch (Exception e) {
            log.error("rollback containers failed for {}", containerName, e);
        }
    }

    private String freezeSnapshot(ServiceEntity svc, DeployRequest req) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Map<String, String> env = new LinkedHashMap<>();
        for (ConfigEntryEntity c : configEntries.findByServiceId(svc.getId())) {
            env.put(c.getKey(), crypto.decrypt(c.getValueCipher()));
        }
        if (req.envOverrides() != null) {
            env.putAll(req.envOverrides());
        }
        snapshot.put("env", env);
        snapshot.put("ports", req.ports() == null ? List.of() : req.ports());
        snapshot.put("restartPolicy", req.restartPolicy() == null ? "unless-stopped" : req.restartPolicy());

        Map<String, String> files = new LinkedHashMap<>();
        for (ConfigFileEntity f : configFiles.findByServiceId(svc.getId())) {
            files.put(f.getPath(), f.getContent());
        }
        snapshot.put("configFiles", files);
        try {
            return mapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("snapshot 序列化失败", e);
        }
    }

    public SshClientManager.HostAuth hostAuthFor(HostEntity host) {
        String secret = null;
        if (host.getAuthCredentialId() != null) {
            secret = credentials.findById(host.getAuthCredentialId())
                    .map(c -> crypto.decrypt(c.getSecretCipher()))
                    .orElseThrow(() -> new IllegalStateException("主机凭据不存在"));
        }
        boolean isKey = credentials.findById(host.getAuthCredentialId())
                .map(c -> "SSH_KEY".equals(c.getType())).orElse(false);
        if (isKey) {
            return new SshClientManager.HostAuth(host.getIp(), host.getSshPort(),
                    host.getUsername(), null, secret);
        }
        return new SshClientManager.HostAuth(host.getIp(), host.getSshPort(),
                host.getUsername(),
                secret == null ? new char[0] : secret.toCharArray(), null);
    }

    private void appendError(DeploymentEntity d, String msg) {
        d.setErrorMsg(msg);
        deployments.save(d);
    }

    private void failDeployment(Long depId, String msg) {
        deployments.findById(depId).ifPresent(d -> {
            d.setStatus("FAILED");
            d.setErrorMsg(truncate(msg, 800));
            d.setFinishedAt(now());
            deployments.save(d);
        });
    }

    /** 镜像全名（repo:tag），供部署/回滚共用 */
    public String imageRef(ServiceEntity svc, String versionTag) {
        return imageRepo(svc) + ":" + versionTag;
    }

    /** 容器名（每单元唯一，按 opah.serviceId label 检索） */
    public String containerName(ServiceEntity svc) {
        return "opah-" + svc.getProjectId() + "-" + svc.getId() + "-" + slug(svc.getName());
    }

    /** 镜像仓库名（不带 tag） */
    public String imageRepo(ServiceEntity svc) {
        return "opah/" + svc.getProjectId() + "-" + svc.getId() + "-" + slug(svc.getName());
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
