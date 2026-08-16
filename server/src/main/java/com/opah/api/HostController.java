package com.opah.api;

import com.opah.domain.CredentialEntity;
import com.opah.domain.CredentialRepository;
import com.opah.domain.HostEntity;
import com.opah.domain.HostRepository;
import com.opah.infra.CryptoService;
import com.opah.service.HostService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 主机管理 API（HOST-01/02/03/04） */
@RestController
@RequestMapping("/api/v1/hosts")
public class HostController {

    private final HostRepository hosts;
    private final HostService hostService;
    private final CredentialRepository credentials;
    private final CryptoService crypto;

    public HostController(HostRepository hosts, HostService hostService,
                          CredentialRepository credentials, CryptoService crypto) {
        this.hosts = hosts;
        this.hostService = hostService;
        this.credentials = credentials;
        this.crypto = crypto;
    }

    @GetMapping
    public List<HostEntity> list() {
        return hosts.findAll();
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        // 内联凭据（password 或 privateKey）→ 自动建 credential
        Long credentialId = body.get("credentialId") == null ? null
                : Long.valueOf(String.valueOf(body.get("credentialId")));
        String inlinePassword = (String) body.get("password");
        String inlineKey = (String) body.get("privateKey");
        if (credentialId == null && (inlinePassword != null || inlineKey != null)) {
            CredentialEntity c = new CredentialEntity();
            c.setName("host-" + body.get("name"));
            c.setType(inlineKey != null ? "SSH_KEY" : "PASSWORD");
            c.setSecretCipher(crypto.encrypt(inlineKey != null ? inlineKey : inlinePassword));
            c.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            credentialId = credentials.save(c).getId();
        }
        HostService.HostInput input = new HostService.HostInput(
                (String) body.get("name"),
                (String) body.get("ip"),
                body.get("sshPort") == null ? 22 : Integer.valueOf(String.valueOf(body.get("sshPort"))),
                (String) body.get("username"),
                credentialId);
        HostEntity host = hostService.add(input);
        return Map.of("id", host.getId(), "status", host.getStatus(), "role", host.getRole(),
                "dockerVersion", host.getDockerVersion() == null ? "" : host.getDockerVersion(),
                "osInfo", host.getOsInfo() == null ? "" : host.getOsInfo());
    }

    /** 设为构建机：SSH 自动装 Docker + 开 2375 + 绑定 endpoint */
    @PostMapping("/{id}/set-build-machine")
    public Map<String, Object> setBuildMachine(@PathVariable Long id) {
        HostService.SetupResult r = hostService.setBuildMachine(id);
        return Map.of("ok", r.ok(), "message", r.message(), "steps", r.steps());
    }

    /** 取消构建机标记 */
    @PostMapping("/{id}/unset-build-machine")
    public Map<String, Object> unsetBuildMachine(@PathVariable Long id) {
        hostService.unsetBuildMachine(id);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable Long id) {
        HostService.TestResult r = hostService.test(id);
        hosts.findById(id).ifPresent(h -> {
            h.setStatus(r.ok() ? "ONLINE" : "OFFLINE");
            h.setDockerVersion(r.dockerVersion());
            h.setOsInfo(r.osInfo());
            h.setLastSeenAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            hosts.save(h);
        });
        return Map.of("ok", r.ok(), "message", r.message(),
                "dockerVersion", r.dockerVersion() == null ? "" : r.dockerVersion(),
                "osInfo", r.osInfo() == null ? "" : r.osInfo());
    }

    @GetMapping("/{id}/containers")
    public List<Map<String, Object>> containers(@PathVariable Long id) {
        return hostService.containers(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        hosts.deleteById(id);
        return Map.of("ok", true);
    }
}
