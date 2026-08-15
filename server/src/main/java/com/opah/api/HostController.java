package com.opah.api;

import com.opah.domain.HostEntity;
import com.opah.domain.HostRepository;
import com.opah.infra.CryptoService;
import com.opah.infra.SshExecutor;
import com.opah.infra.SshResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hosts")
public class HostController {

    private final HostRepository hostRepository;
    private final CryptoService cryptoService;
    private final SshExecutor sshExecutor;

    public HostController(HostRepository hostRepository, CryptoService cryptoService,
                          SshExecutor sshExecutor) {
        this.hostRepository = hostRepository;
        this.cryptoService = cryptoService;
        this.sshExecutor = sshExecutor;
    }

    public record CreateHostRequest(
        @NotBlank String name,
        @NotBlank String ip,
        @Min(1) @Max(65535) int sshPort,
        @NotBlank String username,
        @NotBlank String password) {
    }

    public record HostResponse(Long id, String name, String ip, int sshPort, String username,
                               String status, String dockerVersion, String osInfo,
                               LocalDateTime lastSeenAt, LocalDateTime createdAt) {

        static HostResponse from(HostEntity h) {
            return new HostResponse(h.getId(), h.getName(), h.getIp(), h.getSshPort(),
                h.getUsername(), h.getStatus(), h.getDockerVersion(), h.getOsInfo(),
                h.getLastSeenAt(), h.getCreatedAt());
        }
    }

    public record CheckResponse(boolean ok, String dockerVersion, String osInfo, String error) {
    }

    @GetMapping
    public List<HostResponse> list() {
        return hostRepository.findAll().stream().map(HostResponse::from).toList();
    }

    @PostMapping
    public HostResponse create(@RequestBody @Valid CreateHostRequest request) {
        HostEntity host = new HostEntity();
        host.setName(request.name());
        host.setIp(request.ip());
        host.setSshPort(request.sshPort() > 0 ? request.sshPort() : 22);
        host.setUsername(request.username());
        host.setSecretCipher(cryptoService.encrypt(request.password()));
        return HostResponse.from(hostRepository.save(host));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!hostRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        hostRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    /** SSH 连通性 + Docker 环境检测（HOST-01） */
    @PostMapping("/{id}/check")
    public CheckResponse check(@PathVariable Long id) {
        HostEntity host = hostRepository.findById(id).orElseThrow();
        try {
            String password = cryptoService.decrypt(host.getSecretCipher());
            SshResult docker = sshExecutor.execute(host.getIp(), host.getSshPort(),
                host.getUsername(), password, "docker version --format '{{.Server.Version}}'");
            if (!docker.isSuccess()) {
                host.setStatus("SSH_OK_DOCKER_MISSING");
                hostRepository.save(host);
                return new CheckResponse(false, null, null,
                    "SSH 连接成功，但 Docker 不可用: " + docker.stderr());
            }
            SshResult os = sshExecutor.execute(host.getIp(), host.getSshPort(),
                host.getUsername(), password, "uname -sr");
            host.setStatus("ONLINE");
            host.setDockerVersion(docker.stdout());
            host.setOsInfo(os.isSuccess() ? os.stdout() : null);
            host.setLastSeenAt(LocalDateTime.now());
            hostRepository.save(host);
            return new CheckResponse(true, docker.stdout(), os.stdout(), null);
        } catch (Exception e) {
            host.setStatus("OFFLINE");
            hostRepository.save(host);
            return new CheckResponse(false, null, null, "SSH 连接失败: " + e.getMessage());
        }
    }
}
