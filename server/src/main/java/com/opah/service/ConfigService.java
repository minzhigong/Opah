package com.opah.service;

import com.opah.domain.ConfigEntryEntity;
import com.opah.domain.ConfigEntryRepository;
import com.opah.domain.ConfigFileEntity;
import com.opah.domain.ConfigFileRepository;
import com.opah.domain.ServiceEntity;
import com.opah.domain.ServiceRepository;
import com.opah.infra.CryptoService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置中心（CONF-01/02）：环境变量 + 配置文件，按单元管理。
 * 敏感项 AES-256-GCM 加密落库；部署时随 snapshot 注入容器。
 */
@Service
public class ConfigService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConfigEntryRepository entries;
    private final ConfigFileRepository files;
    private final ServiceRepository services;
    private final CryptoService crypto;

    public ConfigService(ConfigEntryRepository entries, ConfigFileRepository files,
                         ServiceRepository services, CryptoService crypto) {
        this.entries = entries;
        this.files = files;
        this.services = services;
        this.crypto = crypto;
    }

    /** 环境变量条目（含明文值，用于回显时打码） */
    public record EntryView(String key, String maskedValue, boolean sensitive) {
    }

    /** 保存环境变量（整组覆盖，value 为 null 表示跳过敏感项更新） */
    @Transactional
    public List<EntryView> saveEntries(Long serviceId, List<EntryInput> input) {
        entries.deleteByServiceId(serviceId);
        String now = now();
        List<EntryView> views = new ArrayList<>();
        for (EntryInput in : input) {
            ConfigEntryEntity e = new ConfigEntryEntity();
            e.setServiceId(serviceId);
            e.setKey(in.key());
            e.setIsSensitive(in.sensitive() ? 1 : 0);
            e.setValueCipher(crypto.encrypt(in.value() == null ? "" : in.value()));
            e.setUpdatedAt(now);
            entries.save(e);
            views.add(new EntryView(in.key(), mask(in.value(), in.sensitive()), in.sensitive()));
        }
        return views;
    }

    public record EntryInput(String key, String value, boolean sensitive) {
    }

    /** 列出环境变量（敏感值打码） */
    public List<EntryView> listEntries(Long serviceId) {
        List<EntryView> result = new ArrayList<>();
        for (ConfigEntryEntity e : entries.findByServiceId(serviceId)) {
            String plain = crypto.decrypt(e.getValueCipher());
            boolean sensitive = e.getIsSensitive() != null && e.getIsSensitive() == 1;
            result.add(new EntryView(e.getKey(), mask(plain, sensitive), sensitive));
        }
        return result;
    }

    /** 解密后的完整环境变量（仅部署链路使用，勿对外暴露） */
    public Map<String, String> resolveEntries(Long serviceId) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ConfigEntryEntity e : entries.findByServiceId(serviceId)) {
            map.put(e.getKey(), crypto.decrypt(e.getValueCipher()));
        }
        return map;
    }

    /** 配置文件整组覆盖 */
    @Transactional
    public void saveFiles(Long serviceId, Map<String, String> pathToContent) {
        files.deleteByServiceId(serviceId);
        String now = now();
        for (Map.Entry<String, String> e : pathToContent.entrySet()) {
            ConfigFileEntity f = new ConfigFileEntity();
            f.setServiceId(serviceId);
            f.setPath(e.getKey());
            f.setContent(e.getValue());   // 整体加密内容
            f.setUpdatedAt(now);
            files.save(f);
        }
    }

    public List<ConfigFileEntity> listFiles(Long serviceId) {
        return files.findByServiceId(serviceId);
    }

    /** 读取 + 更新 Nginx 配置（CONF-02，前端单元） */
    public ServiceEntity updateNginxConfig(Long serviceId, Map<String, Object> nginxConfig) {
        ServiceEntity svc = services.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("单元不存在"));
        svc.setNginxConfig(toJson(nginxConfig));
        return services.save(svc);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("nginx 配置序列化失败", e);
        }
    }

    private String mask(String value, boolean sensitive) {
        if (!sensitive) {
            return value;
        }
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private String now() {
        return LocalDateTime.now().format(TS);
    }
}
