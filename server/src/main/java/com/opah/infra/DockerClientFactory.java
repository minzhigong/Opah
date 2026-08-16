package com.opah.infra;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Docker 客户端工厂：支持两种模式（setting 表 docker.host 配置）：
 *  - local（默认）：Windows named pipe / Linux unix socket
 *  - remote：tcp://host:2375（远程 Linux 构建机，daemon 需开 TCP 或由用户自行加 TLS）
 * docker-java 的 build/save 均走 daemon API，构建上下文打包为 tar 上传，
 * 因此远程模式下构建、save|load 分发等链路代码无需任何改动。
 *
 * Docker 探活采用「后台定时探测 + 结果缓存」：接口只读缓存，绝不阻塞请求。
 */
@Component
public class DockerClientFactory {

    private static final Logger log = LoggerFactory.getLogger(DockerClientFactory.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private volatile DockerClient client;
    /** 当前 client 对应的 endpoint（null = 尚未创建） */
    private volatile String clientEndpoint;

    /** 最近一次探测结果（null = 尚未探测） */
    private volatile DockerStatus cachedStatus;

    /** 当前 Docker host 设置：null/blank/"local" = 本机；"tcp://..." = 远程 */
    private volatile String dockerHostSetting;

    /** 用户是否显式保存过 docker host 配置（区分「未配置」与「配置为本机」） */
    private volatile boolean explicitConfigured;

    /** 启动时由 SettingService 注入持久化配置（晚于构造，避免循环依赖） */
    public void initFromSetting(String host) {
        dockerHostSetting = normalize(host);
        log.info("Docker host setting: {}", dockerHostSetting == null ? "local" : dockerHostSetting);
    }

    private String normalize(String host) {
        if (host == null) {
            return null;
        }
        String h = host.trim();
        if (h.isEmpty() || "local".equalsIgnoreCase(h)) {
            return null;
        }
        return h;
    }

    /** 懒加载：仅在实际执行 docker 操作时建立连接；endpoint 变化时自动重建 */
    public synchronized DockerClient client() {
        String desired = dockerHostSetting;
        if (client == null || clientEndpoint == null || !clientEndpoint.equals(desired == null ? "" : desired)) {
            closeQuietly();
            DockerClientConfig config = buildConfig(desired);
            log.info("Docker daemon endpoint: {}", config.getDockerHost());
            ApacheDockerHttpClient.Builder httpClientBuilder = new ApacheDockerHttpClient.Builder()
                .maxConnections(10)
                .connectionTimeout(CONNECT_TIMEOUT)
                .responseTimeout(Duration.ofMinutes(5));
            DockerHttpClient httpClient = httpClientBuilder.dockerHost(config.getDockerHost()).build();
            client = DockerClientImpl.getInstance(config, httpClient);
            clientEndpoint = desired == null ? "" : desired;
            cachedStatus = null;   // endpoint 变了，旧探测结果作废
        }
        return client;
    }

    private DockerClientConfig buildConfig(String host) {
        DefaultDockerClientConfig.Builder cfg = DefaultDockerClientConfig.createDefaultConfigBuilder();
        if (host != null) {
            cfg.withDockerHost(host);
        }
        return cfg.build();
    }

    /** 更新 Docker host 设置并立即失效缓存（下次 client() 重建连接） */
    public synchronized void updateDockerHost(String host) {
        this.dockerHostSetting = normalize(host);
        closeQuietly();
        cachedStatus = null;
        log.info("Docker host updated: {}", this.dockerHostSetting == null ? "local" : this.dockerHostSetting);
    }

    /** 当前生效的 endpoint 描述（UI 展示用） */
    public String currentEndpoint() {
        String h = dockerHostSetting;
        return h == null ? "local" : h;
    }

    public boolean isRemote() {
        return dockerHostSetting != null;
    }

    /** 用户是否显式配置过 docker host（首启向导判断用） */
    public boolean hasExplicitConfig() {
        return explicitConfigured;
    }

    /** 由 SettingService 在加载持久化配置时标记 */
    public void markConfigured() {
        this.explicitConfigured = true;
    }

    private void closeQuietly() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
            client = null;
            clientEndpoint = null;
        }
    }

    /** 后台探测：启动即测一次，之后每 30s 刷新缓存 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 0)
    public void refreshStatus() {
        DockerStatus s;
        try {
            client().pingCmd().exec();
            s = DockerStatus.ok();
        } catch (Exception e) {
            s = DockerStatus.fail(classify(e));
        }
        DockerStatus old = cachedStatus;
        cachedStatus = s;
        if (old == null || old.healthy() != s.healthy()) {
            log.info("Docker 状态: healthy={}, endpoint={}, message={}",
                    s.healthy(), currentEndpoint(), s.message());
        }
    }

    /** 返回缓存的 Docker 状态；尚未探测完成时立即返回「检测中」，绝不阻塞请求 */
    public DockerStatus ping() {
        DockerStatus s = cachedStatus;
        if (s == null) {
            return DockerStatus.fail("正在检测 Docker 环境，请稍候刷新…");
        }
        return s;
    }

    /** 同步连通性测试（配置向导「测试连接」按钮用，允许阻塞数秒） */
    public DockerStatus testConnection(String host) {
        try {
            DockerClientConfig config = buildConfig(normalize(host));
            try (ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .maxConnections(1)
                    .connectionTimeout(Duration.ofSeconds(5))
                    .responseTimeout(Duration.ofSeconds(5))
                    .dockerHost(config.getDockerHost())
                    .build()) {
                DockerClient probe = DockerClientImpl.getInstance(config, httpClient);
                probe.pingCmd().exec();
                return DockerStatus.ok();
            }
        } catch (Exception e) {
            return DockerStatus.fail(classify(e));
        }
    }

    private String classify(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("pipe") || msg.contains("npipe") || msg.contains("not found") || msg.contains("404")) {
            return "Docker Desktop 未安装或 named pipe 不存在";
        }
        if (msg.contains("refused") || msg.contains("timeout") || msg.contains("timed out")) {
            return isRemote()
                    ? "远程 Docker daemon 不可达：检查服务器 docker 服务、2375 端口开放与防火墙"
                    : "Docker daemon 未启动或正在启动中";
        }
        if (msg.contains("ssl") || msg.contains("tls") || msg.contains("certificate")) {
            return "远程 Docker daemon 要求 TLS（当前版本暂不支持 TLS，请开放 2375 或使用隧道）";
        }
        return "Docker daemon 连接异常: " + e.getMessage();
    }
}
