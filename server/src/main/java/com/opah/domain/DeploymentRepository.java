package com.opah.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, Long> {

    List<DeploymentEntity> findByServiceIdOrderByIdDesc(Long serviceId);

    List<DeploymentEntity> findTop10ByOrderByStartedAtDesc();
}
