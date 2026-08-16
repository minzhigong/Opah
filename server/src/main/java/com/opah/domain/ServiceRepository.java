package com.opah.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

    List<ServiceEntity> findByProjectIdOrderByName(Long projectId);

    List<ServiceEntity> findByProjectId(Long projectId);
}
