package com.opah.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opah.domain.BuildEntity;
import com.opah.domain.BuildRepository;
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
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 回滚（DEPLOY-04）：指向历史 deployment 的一次重放（复用其 config_snapshot）。
 * 优先复用目标主机本地已 load 的历史镜像（docker image inspect 命中 → 零传输秒级回滚）；
 * 镜像已被清理则从构建机本地历史 tag 重新 save/load。不重新构建。
 */
@Service
public class RollbackService {

    private static final Logger log = LoggerFactory.getLogger(RollbackService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeploymentRepository deployments;
    private final BuildRepository builds;
    private final ServiceRepository services;
    private final HostRepository hosts;
    private final DeployService deployService;
    private final DockerClientFactory dockerFactory;
    private final SshClientManager ssh;
    private final CryptoService crypto;
    private final AuditService audit;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long healthTimeoutSeconds;

    public RollbackService(DeploymentRepository deployments, BuildRepository builds,
                           ServiceRepository services, HostRepository hosts,
                           DeployService deployService, DockerClientFactory dockerFactory,
                           SshClientManager ssh, CryptoService crypto, AuditService audit,
                           @Value("${opah.deploy.health-timeout-seconds:30}") long healthTimeoutSeconds) {
        this.deployments = deployments;
        this.builds = builds;
        this.services = services;
        this.hosts = hosts;
        this.deployService = deployService;
        this.dockerFactory = dockerFactory;
        this.ssh = ssh;
        this.crypto = crypto;
        this.audit = audit;
        this.healthTimeoutSeconds = healthTimeoutSeconds;
    }

    /** 回滚到指定历史 deployment（异步） */
    public DeploymentEntity rollback(Long deploymentId, String username) {
        DeploymentEntity target = deployments.findById(deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("部署记录不存在"));
        if ("FAILED".equals(target.getStatus())) {
            throw new IllegalStateException("FAILED 部署不可作为回滚目标");
        }
        ServiceEntity svc = services.findById(target.getServiceId()).orElseThrow();
        BuildEntity build = builds.findById(target.getBuildId())
                .orElseThrow(() -> new IllegalArgumentException("目标构建不存在"));

        DeploymentEntity rollback = new DeploymentEntity();
        rollback.setServiceId(target.getServiceId());
        rollback.setBuildId(target.getBuildId());
        rollback.setHostId(target.getHostId());
        rollback.setStatus("RUNNING");
        rollback.setStartedBy(username);
        rollback.setStartedAt(now());
        rollback.setConfigSnapshot(target.getConfigSnapshot());   // 复用历史 snapshot
        rollback = deployments.save(rollback);

        // 标记被替换的部署为 ROLLED_BACK（当前生效的那条）
        List<DeploymentEntity> history = deployments.findByServiceIdOrderByIdDesc(svc.getId());
        for (DeploymentEntity d : history) {
            if ("SUCCESS".equals(d.getStatus())) {
                d.setStatus("ROLLED_BACK");
                deployments.save(d);
                break;
            }
        }

        audit.record("ROLLBACK", "service", String.valueOf(svc.getId()),
                "from=" + deploymentId + " to=" + rollback.getId());
        final Long rollbackId = rollback.getId();
        Thread.ofVirtual().name("opah-rollback-" + rollbackId).start(() -> {
            try {
                executeRollback(rollbackId);
            } catch (Exception e) {
                log.error("rollback {} failed", rollbackId, e);
                fail(rollbackId, e.getMessage());
            }
        });
        return rollback;
    }

    private void executeRollback(Long rollbackId) throws Exception {
        DeploymentEntity d = deployments.findById(rollbackId).orElseThrow();
        ServiceEntity svc = services.findById(d.getServiceId()).orElseThrow();
        BuildEntity build = builds.findById(d.getBuildId()).orElseThrow();
        HostEntity host = hosts.findById(d.getHostId()).orElseThrow();
        SshClientManager.HostAuth auth = deployService.hostAuthFor(host);

        String image = deployService.imageRef(svc, build.getVersionTag());
        String containerName = deployService.containerName(svc);

        // 远端镜像探测：命中则零传输
        boolean loaded = ssh.execute(auth,
                "docker image inspect " + image + " >/dev/null 2>&1 && echo yes || echo no",
                Duration.ofSeconds(30)).stdout().equals("yes");
        if (!loaded) {
            try (java.io.InputStream tar = dockerFactory.client().saveImageCmd(image).exec()) {
                SshClientManager.SshResult r = ssh.pipe(auth, "docker load", tar, Duration.ofMinutes(30));
                if (!r.ok()) {
                    throw new IllegalStateException("回滚镜像分发失败: " + r.stderr());
                }
            }
        }

        Map<String, Object> snapshot = mapper.readValue(d.getConfigSnapshot(), Map.class);
        String oldContainerId = deployService.findContainer(auth, containerName);
        String newContainerId = deployService.startContainer(auth, image, containerName, svc, snapshot);

        boolean healthy = deployService.waitRunning(auth, newContainerId,
                Duration.ofSeconds(healthTimeoutSeconds));
        if (!healthy) {
            deployService.rollbackContainers(auth, containerName, oldContainerId, newContainerId);
            fail(rollbackId, "回滚后容器健康检查失败，已恢复原容器");
            return;
        }

        d.setStatus("SUCCESS");
        d.setFinishedAt(now());
        deployments.save(d);
        svc.setCurrentBuildId(build.getId());
        services.save(svc);
        audit.record("ROLLBACK_SUCCESS", "service", String.valueOf(svc.getId()),
                "deploymentId=" + rollbackId);
    }

    private void fail(Long id, String msg) {
        deployments.findById(id).ifPresent(d -> {
            d.setStatus("FAILED");
            d.setErrorMsg(msg == null ? null : (msg.length() <= 800 ? msg : msg.substring(0, 800)));
            d.setFinishedAt(now());
            deployments.save(d);
        });
    }

    private String now() {
        return LocalDateTime.now().format(TS);
    }
}
