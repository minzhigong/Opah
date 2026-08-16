package com.opah.infra;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 本机 Docker 客户端：Windows 走 named pipe（httpclient5 传输内置支持），
 * Linux 走 unix socket；DOCKER_HOST 环境变量可覆盖。
 *
 * Docker 探活采用「后台定时探测 + 结果缓存」：ping 连接本机 daemon 慢（无 Docker 时超时），
 * 不能让每个 dashboard 请求都实时 ping。探测每 30s 一次（架构文档 §7.1），接口只读缓存。
 */
@Component
public class DockerClientFactory {

    private static final Logger log = LoggerFactory.getLogger(DockerClientFactory.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private volatile DockerClient client;

    /** 最近一次探测结果（null = 尚未探测） */
    private volatile DockerStatus cachedStatus;

    /** 懒加载：仅在实际执行 docker 操作时建立连接，避免本机无 Docker 时阻断启动 */
    public synchronized DockerClient client() {
        if (client == null) {
            DefaultDockerClientConfig.Builder cfg = DefaultDockerClientConfig.createDefaultConfigBuilder();
            DockerClientConfig config = cfg.build();
            log.info("Docker daemon: {}", config.getDockerHost());
            ApacheDockerHttpClient.Builder httpClientBuilder = new ApacheDockerHttpClient.Builder()
                .maxConnections(10)
                .connectionTimeout(CONNECT_TIMEOUT)
                .responseTimeout(Duration.ofMinutes(5));
            DockerHttpClient httpClient = httpClientBuilder.dockerHost(config.getDockerHost()).build();
            client = DockerClientImpl.getInstance(config, httpClient);
        }
        return client;
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
            log.info("Docker 状态: healthy={}, message={}", s.healthy(), s.message());
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

    private String classify(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("pipe") || msg.contains("npipe") || msg.contains("not found") || msg.contains("404")) {
            return "Docker Desktop 未安装或 named pipe 不存在";
        }
        if (msg.contains("refused") || msg.contains("timeout") || msg.contains("timed out")) {
            return "Docker daemon 未启动或正在启动中";
        }
        return "Docker daemon 连接异常: " + e.getMessage();
    }
}
