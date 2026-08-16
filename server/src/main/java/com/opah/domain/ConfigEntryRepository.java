package com.opah.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigEntryRepository extends JpaRepository<ConfigEntryEntity, Long> {

    List<ConfigEntryEntity> findByServiceId(Long serviceId);

    void deleteByServiceId(Long serviceId);
}
