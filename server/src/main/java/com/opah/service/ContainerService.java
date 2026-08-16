package com.opah.service;

import com.opah.domain.HostEntity;
import com.opah.domain.HostRepository;
import com.opah.infra.SshClientManager;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** 容器启停 / 日志（OPS-02/03） */
@Service
public class ContainerService {

    private final HostRepository hosts;
    private final HostService hostService;
    private final SshClientManager ssh;

    public ContainerService(HostRepository hosts, HostService hostService, SshClientManager ssh) {
        this.hosts = hosts;
        this.hostService = hostService;
        this.ssh = ssh;
    }

    public void action(Long hostId, String containerId, String action) {
        HostEntity host = hosts.findById(hostId).orElseThrow();
        SshClientManager.HostAuth auth = hostService.hostAuth(host);
        String cmd = switch (action) {
            case "start" -> "docker start " + containerId;
            case "stop" -> "docker stop " + containerId;
            case "restart" -> "docker restart " + containerId;
            default -> throw new IllegalArgumentException("未知操作: " + action);
        };
        SshClientManager.SshResult r = ssh.execute(auth, cmd, Duration.ofSeconds(60));
        if (!r.ok()) {
            throw new IllegalStateException("容器操作失败: " + r.stderr());
        }
    }

    /** 拉取最近 N 行日志 */
    public String tail(Long hostId, String containerId, int lines) {
        HostEntity host = hosts.findById(hostId).orElseThrow();
        SshClientManager.HostAuth auth = hostService.hostAuth(host);
        SshClientManager.SshResult r = ssh.execute(auth,
                "docker logs --tail " + Math.max(1, lines) + " " + containerId,
                Duration.ofSeconds(30));
        return r.stdout();
    }
}
