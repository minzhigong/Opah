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
import org.springframework.stereotype.Component;

/**
 * 本机 Docker 客户端：Windows 走 named pipe（httpclient5 传输内置支持），
 * Linux 走 unix socket；DOCKER_HOST 环境变量可覆盖。
 */
@Component
public class DockerClientFactory {

    private static final Logger log = LoggerFactory.getLogger(DockerClientFactory.class);

    private volatile DockerClient client;

    /** 懒加载：仅在实际执行 docker 操作时建立连接，避免本机无 Docker 时阻断启动 */
    public synchronized DockerClient client() {
        if (client == null) {
            DefaultDockerClientConfig.Builder cfg = DefaultDockerClientConfig.createDefaultConfigBuilder();
            DockerClientConfig config = cfg.build();
            log.info("Docker daemon: {}", config.getDockerHost());
            ApacheDockerHttpClient.Builder httpClientBuilder = new ApacheDockerHttpClient.Builder()
                .maxConnections(10)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofMinutes(5));
            DockerHttpClient httpClient = httpClientBuilder.dockerHost(config.getDockerHost()).build();
            client = DockerClientImpl.getInstance(config, httpClient);
        }
        return client;
    }

    /** ping 探活，返回状态描述：OK / 具体失败原因（连接失败不抛异常） */
    public DockerStatus ping() {
        try {
            client().pingCmd().exec();
            return DockerStatus.ok();
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
            return "Docker daemon 未启动或正在启动中";
        }
        return "Docker daemon 连接异常: " + e.getMessage();
    }
}
