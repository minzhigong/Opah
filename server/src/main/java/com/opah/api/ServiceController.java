package com.opah.api;

import com.opah.domain.BuildEntity;
import com.opah.domain.BuildLogEntity;
import com.opah.domain.BuildLogRepository;
import com.opah.domain.BuildRepository;
import com.opah.domain.ConfigFileEntity;
import com.opah.domain.DeploymentEntity;
import com.opah.domain.DeploymentRepository;
import com.opah.domain.ServiceEntity;
import com.opah.domain.ServiceRepository;
import com.opah.service.BuildService;
import com.opah.service.ConfigService;
import com.opah.service.DeployService;
import com.opah.service.RollbackService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 部署单元 API（服务详情：构建/版本/配置/部署/回滚） */
@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {

    private final ServiceRepository services;
    private final BuildRepository builds;
    private final BuildLogRepository buildLogs;
    private final DeploymentRepository deployments;
    private final BuildService buildService;
    private final DeployService deployService;
    private final RollbackService rollbackService;
    private final ConfigService configService;

    public ServiceController(ServiceRepository services, BuildRepository builds,
                             BuildLogRepository buildLogs, DeploymentRepository deployments,
                             BuildService buildService, DeployService deployService,
                             RollbackService rollbackService, ConfigService configService) {
        this.services = services;
        this.builds = builds;
        this.buildLogs = buildLogs;
        this.deployments = deployments;
        this.buildService = buildService;
        this.deployService = deployService;
        this.rollbackService = rollbackService;
        this.configService = configService;
    }

    @GetMapping("/{id}")
    public ServiceEntity detail(@PathVariable Long id) {
        return services.findById(id).orElseThrow();
    }

    @PutMapping("/{id}")
    public ServiceEntity update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ServiceEntity svc = services.findById(id).orElseThrow();
        if (body.containsKey("name")) svc.setName((String) body.get("name"));
        if (body.containsKey("buildConfig")) svc.setBuildConfig(String.valueOf(body.get("buildConfig")));
        return services.save(svc);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        services.deleteById(id);
        return Map.of("ok", true);
    }

    // ---- 构建 ----
    @PostMapping("/{id}/builds")
    public Map<String, Object> triggerBuild(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        String ref = (String) body.getOrDefault("ref", "main");
        BuildEntity b = buildService.trigger(id, ref, "admin");
        return Map.of("id", b.getId(), "status", b.getStatus());
    }

    @GetMapping("/{id}/builds")
    public List<BuildEntity> builds(@PathVariable Long id) {
        return builds.findByServiceIdOrderByIdDesc(id);
    }

    @GetMapping("/{id}/builds/{buildId}/logs")
    public List<Map<String, Object>> logs(@PathVariable Long buildId,
                                          @RequestParam(defaultValue = "0") int afterLine) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (BuildLogEntity l : buildLogs.findByBuildIdAndLineNoGreaterThanOrderByLineNoAsc(buildId, afterLine)) {
            result.add(Map.of("lineNo", l.getLineNo(), "content", l.getContent(), "isStderr", l.getIsStderr() == 1));
        }
        return result;
    }

    // ---- 配置中心 ----
    @GetMapping("/{id}/config-entries")
    public List<ConfigService.EntryView> entries(@PathVariable Long id) {
        return configService.listEntries(id);
    }

    @PutMapping("/{id}/config-entries")
    public List<ConfigService.EntryView> saveEntries(@PathVariable Long id,
                                                     @RequestBody List<Map<String, Object>> entries) {
        List<ConfigService.EntryInput> input = new ArrayList<>();
        for (Map<String, Object> e : entries) {
            input.add(new ConfigService.EntryInput(
                    (String) e.get("key"),
                    (String) e.get("value"),
                    Boolean.TRUE.equals(e.get("sensitive"))));
        }
        return configService.saveEntries(id, input);
    }

    @GetMapping("/{id}/config-files")
    public List<Map<String, Object>> files(@PathVariable Long id) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ConfigFileEntity f : configService.listFiles(id)) {
            result.add(Map.of("path", f.getPath(), "content", f.getContent()));
        }
        return result;
    }

    @PutMapping("/{id}/config-files")
    public Map<String, Object> saveFiles(@PathVariable Long id,
                                         @RequestBody Map<String, String> files) {
        configService.saveFiles(id, files);
        return Map.of("ok", true);
    }

    @PutMapping("/{id}/nginx-config")
    public ServiceEntity nginxConfig(@PathVariable Long id, @RequestBody Map<String, Object> cfg) {
        return configService.updateNginxConfig(id, cfg);
    }

    // ---- 部署 / 回滚 ----
    @PostMapping("/{id}/deployments")
    public Map<String, Object> deploy(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long buildId = Long.valueOf(String.valueOf(body.get("buildId")));
        Long hostId = Long.valueOf(String.valueOf(body.get("hostId")));
        Map<String, String> env = new HashMap<>();
        if (body.get("env") instanceof Map<?, ?> envMap) {
            envMap.forEach((k, v) -> env.put(String.valueOf(k), String.valueOf(v)));
        }
        List<DeployService.PortMapping> ports = new ArrayList<>();
        if (body.get("ports") instanceof List<?> portList) {
            for (Object p : portList) {
                Map<String, String> pm = (Map<String, String>) p;
                ports.add(new DeployService.PortMapping(pm.get("hostPort"), pm.get("containerPort")));
            }
        }
        DeploymentEntity d = deployService.deploy(id, new DeployService.DeployRequest(
                buildId, hostId, env, ports, (String) body.get("restartPolicy")), "admin");
        return Map.of("id", d.getId(), "status", d.getStatus());
    }

    @GetMapping("/{id}/deployments")
    public List<DeploymentEntity> deployments(@PathVariable Long id) {
        return deployments.findByServiceIdOrderByIdDesc(id);
    }

    @PostMapping("/deployments/{deploymentId}/rollback")
    public Map<String, Object> rollback(@PathVariable Long deploymentId) {
        DeploymentEntity d = rollbackService.rollback(deploymentId, "admin");
        return Map.of("id", d.getId(), "status", d.getStatus());
    }
}
