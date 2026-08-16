package com.opah.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildRepository extends JpaRepository<BuildEntity, Long> {

    List<BuildEntity> findByServiceIdOrderByIdDesc(Long serviceId);

    Optional<BuildEntity> findByServiceIdAndVersionTag(Long serviceId, String versionTag);

    Optional<BuildEntity> findFirstByServiceIdAndStatusOrderByIdDesc(Long serviceId, String status);

    List<BuildEntity> findByStatusInOrderByIdAsc(List<String> statuses);
}
