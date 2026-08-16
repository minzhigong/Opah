package com.opah.service;

import com.opah.domain.BuildEntity;
import com.opah.domain.DeploymentEntity;
import com.opah.domain.AuditLogEntity;
import com.opah.domain.AuditLogRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

/** 操作审计（SYS-02，P1 → MVP 简化实现） */
@Service
public class AuditService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(String action, String targetType, String targetId, String detail) {
        AuditLogEntity e = new AuditLogEntity();
        e.setAction(action);
        e.setTargetType(targetType);
        e.setTargetId(targetId);
        e.setDetail(detail);
        e.setCreatedAt(LocalDateTime.now().format(TS));
        repo.save(e);
    }
}
