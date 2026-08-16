package com.opah.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostRepository extends JpaRepository<HostEntity, Long> {

    List<HostEntity> findByRole(String role);
}
