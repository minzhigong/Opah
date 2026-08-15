package com.opah.infra;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 本机 Docker daemon 连接（Windows: named pipe / Linux: unix socket），构建与镜像分发在 M2 实现 */
@Component
public class DockerClientFactory implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerClientFactory.class);
    private static final String WINDOWS_DEFAULT_HOST = "npipe:////./pipe/docker_engine";
    private static final String UNIX_DEFAULT_HOST = "unix:///var/run/docker.sock";

    private final DockerClient client;
    private final String dockerHost;

    public DockerClientFactory(@Value("${opah.docker.host:}") String configuredHost) {
        this.dockerHost = resolveHost(configuredHost);
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(this.dockerHost)
            .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(URI.create(this.dockerHost))
            .sslConfig(config.getSSLConfig())
            .maxConnections(10)
            .connectionTimeout(Duration.ofSeconds(5))
            .build();
        this.client = DockerClientImpl.getInstance(config, httpClient);
    }

    private static String resolveHost(String configuredHost) {
        if (configuredHost != null && !configuredHost.isBlank()) {
            return configuredHost;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? WINDOWS_DEFAULT_HOST : UNIX_DEFAULT_HOST;
    }

    public DockerClient get() {
        return client;
    }

    public DockerProbe probe() {
        try {
            Version version = client.versionCmd().exec();
            Info info = client.infoCmd().exec();
            return new DockerProbe(true, dockerHost, version.getVersion(),
                info.getOsType(), version.getApiVersion(), null);
        } catch (Exception e) {
            return new DockerProbe(false, dockerHost, null, null, null, e.getMessage());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        DockerProbe probe = probe();
        if (probe.connected()) {
            log.info("Docker 连接成功: {} (server {}, api {})",
                probe.dockerHost(), probe.serverVersion(), probe.apiVersion());
        } else {
            log.warn("Docker 不可用（{}）：{}。镜像构建/分发功能将无法使用，主机管理不受影响。",
                probe.dockerHost(), probe.error());
        }
    }

    @PreDestroy
    public void close() {
        try {
            client.close();
        } catch (java.io.IOException e) {
            log.warn("关闭 Docker 客户端失败: {}", e.getMessage());
        }
    }

    public record DockerProbe(boolean connected, String dockerHost, String serverVersion,
                              String osType, String apiVersion, String error) {
    }
}
