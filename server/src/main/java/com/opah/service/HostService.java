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
import java.nio.charset.StandardCharsets;
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
    private final SettingService settingService;

    public HostService(HostRepository hosts, CredentialRepository credentials,
                       RuntimeContainerRepository containers, SshClientManager ssh,
                       CryptoService crypto, SettingService settingService) {
        this.hosts = hosts;
        this.credentials = credentials;
        this.containers = containers;
        this.ssh = ssh;
        this.crypto = crypto;
        this.settingService = settingService;
    }

    public record HostInput(String name, String ip, Integer sshPort, String username,
                            Long credentialId) {
    }

    public record TestResult(boolean ok, String message, String dockerVersion, String osInfo) {
    }

    public record SetupResult(boolean ok, String message, java.util.List<String> steps) {
    }

    /** 添加主机并测试连通性（HOST-01/02）。主机不区分角色，构建机通过「指定」动作标记。 */
    public HostEntity add(HostInput input) {
        HostEntity host = new HostEntity();
        host.setName(input.name());
        host.setIp(input.ip());
        host.setSshPort(input.sshPort() == null ? 22 : input.sshPort());
        host.setUsername(input.username());
        host.setAuthCredentialId(input.credentialId());
        host.setRole("deploy");
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

    /**
     * 设为构建机（BUILD-05）：标记 role=build（清掉其他主机的标记，单构建机）→
     * SSH 自动装 Docker → 开 TCP 2375 → 重启 → 验证 → 绑定为 Docker endpoint。
     * 一台主机可同时是构建机 + 部署目标（标记互不影响）。要求 SSH 用户为 root 或 sudo 免密。
     */
    public SetupResult setBuildMachine(Long hostId) {
        HostEntity host = hosts.findById(hostId)
                .orElseThrow(() -> new IllegalArgumentException("主机不存在"));
        // 标记：清掉其他主机的 build 标记，这台设为 build
        for (HostEntity h : hosts.findAll()) {
            if (h.getRole() != null && "build".equals(h.getRole()) && !h.getId().equals(hostId)) {
                h.setRole("deploy");
                hosts.save(h);
            }
        }
        host.setRole("build");
        hosts.save(host);

        SshClientManager.HostAuth auth = hostAuth(host);
        List<String> steps = new ArrayList<>();

        try {
            // 1. 检测环境
            SshClientManager.SshResult r0 = ssh.execute(auth,
                    "id -u; command -v docker >/dev/null 2>&1 && echo DOCKER_YES || echo DOCKER_NO; "
                            + "grep -E '^ID=' /etc/os-release 2>/dev/null | head -1",
                    Duration.ofSeconds(20));
            steps.add("[检测] " + r0.stdout().replace("\n", " | "));
            boolean isRoot = r0.stdout().trim().startsWith("0");
            boolean dockerInstalled = r0.stdout().contains("DOCKER_YES");
            String sudo = isRoot ? "" : "sudo ";
            if (!isRoot) {
                steps.add("[提示] 非 root 登录，后续命令用 sudo 执行（需免密或可交互输密码）");
            }

            // 2. 安装 Docker
            if (!dockerInstalled) {
                steps.add("[安装] 正在安装 Docker（约 1-3 分钟，请耐心等待）...");
                SshClientManager.SshResult r1 = ssh.execute(auth,
                        "curl -fsSL https://get.docker.com | " + sudo + "sh && "
                                + sudo + "systemctl enable --now docker",
                        Duration.ofMinutes(8));
                steps.add("[安装] " + (r1.ok() ? "Docker 安装完成" : "失败: " + r1.stderr()));
                if (!r1.ok()) {
                    return new SetupResult(false, "Docker 安装失败", steps);
                }
            } else {
                steps.add("[安装] Docker 已存在，跳过安装");
            }

            // 3. 配置 2375（SFTP 上传 override.conf，避免 shell 转义问题）
            steps.add("[配置] 开启 Docker TCP API (2375)...");
            String override = "[Service]\nExecStart=\n"
                    + "ExecStart=/usr/bin/dockerd -H fd:// -H tcp://0.0.0.0:2375\n";
            ssh.upload(auth, "/tmp/opah-docker-override.conf",
                    override.getBytes(StandardCharsets.UTF_8));
            SshClientManager.SshResult r2 = ssh.execute(auth,
                    sudo + "mkdir -p /etc/systemd/system/docker.service.d && "
                            + sudo + "cp /tmp/opah-docker-override.conf /etc/systemd/system/docker.service.d/override.conf && "
                            + sudo + "systemctl daemon-reload && " + sudo + "systemctl restart docker",
                    Duration.ofMinutes(2));
            steps.add("[配置] " + (r2.ok() ? "完成，dockerd 已重启" : "失败: " + r2.stderr()));
            if (!r2.ok()) {
                return new SetupResult(false, "Docker TCP 配置失败", steps);
            }

            // 4. 验证
            steps.add("[验证] 等待 dockerd 就绪...");
            SshClientManager.SshResult r3 = ssh.execute(auth,
                    "sleep 3 && docker version --format '{{.Server.Version}}' 2>&1",
                    Duration.ofSeconds(40));
            steps.add("[验证] Docker Server 版本: " + r3.stdout().trim());
            SshClientManager.SshResult r4 = ssh.execute(auth,
                    "curl -s http://127.0.0.1:2375/version 2>&1 | head -c 80",
                    Duration.ofSeconds(15));
            steps.add("[验证] 2375 API 探测: " + (r4.stdout().contains("Version") ? "正常" : r4.stdout()));

            // 5. 更新主机信息 + 绑定为 Docker endpoint
            String ver = r3.stdout().trim();
            if (!ver.isEmpty() && !ver.toLowerCase().startsWith("error") && !ver.contains("permission")) {
                host.setDockerVersion(ver);
            }
            host.setStatus("ONLINE");
            hosts.save(host);

            String endpoint = "tcp://" + host.getIp() + ":2375";
            settingService.saveDockerHost(endpoint);
            steps.add("[绑定] Docker endpoint 已设为 " + endpoint);

            return new SetupResult(true, "构建机就绪: " + endpoint, steps);
        } catch (Exception e) {
            log.warn("setup build machine {} failed: {}", host.getIp(), e.getMessage());
            String msg = e.getMessage() == null ? "未知错误" : e.getMessage();
            if (msg.contains("拒绝") || msg.contains("refused") || msg.contains("timeout")
                    || msg.contains("Connection") || msg.contains("connect")) {
                msg = "SSH 连接失败，请检查主机 IP、端口、用户名和凭据";
            }
            steps.add("[失败] " + msg);
            host.setRole("deploy");   // 安装失败则撤销构建机标记
            hosts.save(host);
            return new SetupResult(false, msg, steps);
        }
    }

    /** 当前被指定为构建机的主机（无则 null） */
    public HostEntity getBuildMachineHost() {
        List<HostEntity> list = hosts.findByRole("build");
        return list.isEmpty() ? null : list.get(0);
    }

    /** 取消构建机标记，Docker endpoint 恢复本机模式 */
    public void unsetBuildMachine() {
        for (HostEntity h : hosts.findByRole("build")) {
            h.setRole("deploy");
            hosts.save(h);
        }
        settingService.saveDockerHost("local");
    }

    private String now() {
        return LocalDateTime.now().format(TS);
    }
}
