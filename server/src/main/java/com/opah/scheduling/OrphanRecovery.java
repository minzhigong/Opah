package com.opah.scheduling;

import com.opah.domain.BuildRepository;
import com.opah.domain.DeploymentRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 启动时恢复：将上次异常退出遗留的 RUNNING 构建/部署标记为 FAILED（interrupted） */
@Component
public class OrphanRecovery {

    private static final Logger log = LoggerFactory.getLogger(OrphanRecovery.class);

    private final BuildRepository builds;
    private final DeploymentRepository deployments;

    public OrphanRecovery(BuildRepository builds, DeploymentRepository deployments) {
        this.builds = builds;
        this.deployments = deployments;
    }

    @PostConstruct
    public void recover() {
        builds.findByStatusInOrderByIdAsc(java.util.List.of("PENDING", "RUNNING")).forEach(b -> {
            b.setStatus("FAILED");
            b.setErrorMsg("Opah 重启中断");
            builds.save(b);
        });
        log.info("orphan builds recovered");
    }
}
