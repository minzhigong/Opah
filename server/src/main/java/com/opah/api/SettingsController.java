package com.opah.api;

import com.opah.infra.DockerClientFactory;
import com.opah.infra.DockerStatus;
import com.opah.service.SettingService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 全局设置 API：Docker host 配置（本机 / 远程构建机） */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingService settingService;
    private final DockerClientFactory dockerFactory;

    public SettingsController(SettingService settingService, DockerClientFactory dockerFactory) {
        this.settingService = settingService;
        this.dockerFactory = dockerFactory;
    }

    @GetMapping("/docker")
    public Map<String, Object> getDocker() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("host", settingService.getDockerHost());
        r.put("endpoint", dockerFactory.currentEndpoint());
        r.put("remote", dockerFactory.isRemote());
        DockerStatus s = dockerFactory.ping();
        r.put("healthy", s.healthy());
        r.put("message", s.message());
        return r;
    }

    /** body: { "host": "tcp://1.2.3.4:2375" } 或 { "host": "local" } */
    @PutMapping("/docker")
    public Map<String, Object> saveDocker(@RequestBody Map<String, String> body) {
        String host = body.get("host");
        String saved = settingService.saveDockerHost(host);
        return Map.of("ok", true, "host", saved, "endpoint", dockerFactory.currentEndpoint());
    }

    /** 连通性测试：body: { "host": "tcp://1.2.3.4:2375" }，不落库 */
    @PostMapping("/docker/test")
    public Map<String, Object> testDocker(@RequestBody Map<String, String> body) {
        DockerStatus s = dockerFactory.testConnection(body.get("host"));
        return Map.of("healthy", s.healthy(), "message", s.message());
    }
}
