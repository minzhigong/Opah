package com.opah.service;

import com.opah.domain.SettingEntity;
import com.opah.domain.SettingRepository;
import com.opah.infra.DockerClientFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** 全局设置：自定义 Docker host 等（SETTING-KV） */
@Service
public class SettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final String KEY_DOCKER_HOST = "docker.host";

    private final SettingRepository settings;
    private final DockerClientFactory dockerFactory;

    public SettingService(SettingRepository settings, DockerClientFactory dockerFactory) {
        this.settings = settings;
        this.dockerFactory = dockerFactory;
    }

    /** 应用就绪后把持久化的 docker.host 注入 DockerClientFactory */
    @EventListener(ApplicationReadyEvent.class)
    public void applyPersisted() {
        settings.findById(KEY_DOCKER_HOST).ifPresent(s -> {
            dockerFactory.markConfigured();
            String v = s.getValue();
            if (v != null && !v.isBlank() && !"local".equalsIgnoreCase(v.trim())) {
                dockerFactory.initFromSetting(v);
            }
        });
    }

    public String getDockerHost() {
        return settings.findById(KEY_DOCKER_HOST).map(SettingEntity::getValue).orElse(null);
    }

    /** 保存 docker host（null/空/"local" = 本机模式） */
    public String saveDockerHost(String host) {
        String normalized = (host == null || host.isBlank() || "local".equalsIgnoreCase(host.trim()))
                ? "local" : host.trim();
        settings.save(new SettingEntity(KEY_DOCKER_HOST, normalized, LocalDateTime.now().format(TS)));
        dockerFactory.markConfigured();
        dockerFactory.updateDockerHost(normalized);
        return normalized;
    }
}
