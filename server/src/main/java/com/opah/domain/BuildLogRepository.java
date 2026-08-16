package com.opah.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildLogRepository extends JpaRepository<BuildLogEntity, Long> {

    List<BuildLogEntity> findByBuildIdOrderByLineNoAsc(Long buildId);

    List<BuildLogEntity> findByBuildIdAndLineNoGreaterThanOrderByLineNoAsc(Long buildId, Integer lineNo);
}
