package com.opah.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opah.domain.CredentialEntity;
import com.opah.domain.CredentialRepository;
import com.opah.domain.HostEntity;
import com.opah.domain.HostRepository;
import com.opah.domain.RuntimeContainerEntity;
import com.opah.domain.RuntimeContainerRepository;
import com.opah.infra.CryptoService;
import com.opah.infra.SshClientManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 主机管理（HOST-01/02/03/04）：接入检测（SSH + Docker）、容器概览、状态刷新。
 */
@Service
public class HostService {

    private static final Logger log = LoggerFactory.getLogger(HostService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HostRepository hosts;
    private final CredentialRepository credentials;
    private final RuntimeContainerRepository containers;
    private final SshClientManager ssh;
    private final CryptoService crypto;
    private final ObjectMapper mapper = new ObjectMapper();

    public HostService(HostRepository hosts, CredentialRepository credentials,
                       RuntimeContainerRepository containers, SshClientManager ssh,
                       CryptoService crypto) {
        this.hosts = hosts;
        this.credentials = credentials;
        this.containers = containers;
        this.ssh = ssh;
        this.crypto = crypto;
    }

    public record HostInput(String name, String ip, Integer sshPort, String username,
                            Long credentialId) {
    }

    public record TestResult(boolean ok, String message, String dockerVersion, String osInfo) {
    }

    /** 添加主机并测试连通性（HOST-01/02） */
    public HostEntity add(HostInput input) {
        HostEntity host = new HostEntity();
        host.setName(input.name());
        host.setIp(input.ip());
        host.setSshPort(input.sshPort() == null ? 22 : input.sshPort());
        host.setUsername(input.username());
        host.setAuthCredentialId(input.credentialId());
        host.setStatus("UNKNOWN");
        host.setCreatedAt(now());
        host = hosts.save(host);

        try {
            TestResult tr = test(host.getId());
            host.setStatus(tr.ok() ? "ONLINE" : "OFFLINE");
            host.setDockerVersion(tr.dockerVersion());
            host.setOsInfo(tr.osInfo());
            host.setLastSeenAt(now());
        } catch (Exception e) {
            host.setStatus("OFFLINE");
            host.setLastSeenAt(now());
            log.warn("host {} test failed: {}", input.ip(), e.getMessage());
        }
        return hosts.save(host);
    }

    /** 连通性 + Docker 检测（返回 docker version / os） */
    public TestResult test(Long hostId) {
        HostEntity host = hosts.findById(hostId)
                .orElseThrow(() -> new IllegalArgumentException("主机不存在"));
        SshClientManager.HostAuth auth = hostAuth(host);
        try {
            SshClientManager.SshResult r = ssh.execute(auth,
                    "docker version --format '{{.Server.Version}}' && uname -a",
                    Duration.ofSeconds(20));
            if (r.ok()) {
                String[] lines = r.stdout().split("\n", 2);
                String dockerVersion = lines.length > 0 ? lines[0].trim() : null;
                String osInfo = lines.length > 1 ? lines[1].trim() : null;
                return new TestResult(true, "OK", dockerVersion, osInfo);
            }
            return new TestResult(false, "Docker 检测失败: " + r.stderr(), null, null);
        } catch (Exception e) {
            return new TestResult(false, e.getMessage(), null, null);
        }
    }

    /** 容器概览：ssh docker ps 解析（HOST-04） */
    public List<Map<String, Object>> containers(Long hostId) {
        HostEntity host = hosts.findById(hostId).orElseThrow();
        SshClientManager.HostAuth auth = hostAuth(host);
        SshClientManager.SshResult r = ssh.execute(auth,
                "docker ps -a --format '{{json .}}'", Duration.ofSeconds(30));
        List<Map<String, Object>> result = new ArrayList<>();
        if (r.ok() && !r.stdout().isBlank()) {
            for (String line : r.stdout().split("\n")) {
                try {
                    JsonNode node = mapper.readTree(line);
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", node.path("ID").asText());
                    row.put("image", node.path("Image").asText());
                    row.put("name", node.path("Names").asText());
                    row.put("status", node.path("Status").asText());
                    row.put("state", node.path("State").asText());
                    result.add(row);
                } catch (Exception e) {
                    log.warn("parse container line failed: {}", line);
                }
            }
        }
        return result;
    }

    /** 刷新主机在线状态 + 资源（供定时任务调用） */
    public void refreshStatus(Long hostId) {
        hosts.findById(hostId).ifPresent(host -> {
            try {
                SshClientManager.HostAuth auth = hostAuth(host);
                SshClientManager.SshResult r = ssh.execute(auth, "echo ok", Duration.ofSeconds(10));
                host.setStatus(r.ok() ? "ONLINE" : "OFFLINE");
                host.setLastSeenAt(now());
                hosts.save(host);
            } catch (Exception e) {
                host.setStatus("OFFLINE");
                host.setLastSeenAt(now());
                hosts.save(host);
            }
        });
    }

    public SshClientManager.HostAuth hostAuth(HostEntity host) {
        if (host.getAuthCredentialId() == null) {
            return new SshClientManager.HostAuth(host.getIp(), host.getSshPort(),
                    host.getUsername(), new char[0], null);
        }
        CredentialEntity c = credentials.findById(host.getAuthCredentialId())
                .orElseThrow(() -> new IllegalStateException("主机凭据不存在"));
        String secret = crypto.decrypt(c.getSecretCipher());
        if ("SSH_KEY".equals(c.getType())) {
            return new SshClientManager.HostAuth(host.getIp(), host.getSshPort(),
                    host.getUsername(), null, secret);
        }
        return new SshClientManager.HostAuth(host.getIp(), host.getSshPort(),
                host.getUsername(), secret.toCharArray(), null);
    }

    private String now() {
        return LocalDateTime.now().format(TS);
    }
}
