package com.opah.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeContainerRepository extends JpaRepository<RuntimeContainerEntity, Long> {

    List<RuntimeContainerEntity> findByHostId(Long hostId);

    Optional<RuntimeContainerEntity> findByHostIdAndDockerContainerId(Long hostId, String dockerContainerId);

    void deleteByHostId(Long hostId);
}
