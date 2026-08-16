package com.opah.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigFileRepository extends JpaRepository<ConfigFileEntity, Long> {

    List<ConfigFileEntity> findByServiceId(Long serviceId);

    void deleteByServiceId(Long serviceId);
}
