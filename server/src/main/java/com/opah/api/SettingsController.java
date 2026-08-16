package com.opah.api;

import com.opah.domain.HostEntity;
import com.opah.infra.DockerClientFactory;
import com.opah.infra.DockerStatus;
import com.opah.service.HostService;
import com.opah.service.SettingService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 全局设置 API：构建机指定（从主机列表选一台，无需手填 URL） */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingService settingService;
    private final DockerClientFactory dockerFactory;
    private final HostService hostService;

    public SettingsController(SettingService settingService, DockerClientFactory dockerFactory,
                              HostService hostService) {
        this.settingService = settingService;
        this.dockerFactory = dockerFactory;
        this.hostService = hostService;
    }

    /** 当前 Docker 环境状态 + 当前构建机主机 */
    @GetMapping("/docker")
    public Map<String, Object> getDocker() {
        Map<String, Object> r = new LinkedHashMap<>();
        HostEntity buildHost = hostService.getBuildMachineHost();
        r.put("endpoint", dockerFactory.currentEndpoint());
        r.put("remote", dockerFactory.isRemote());
        DockerStatus s = dockerFactory.ping();
        r.put("healthy", s.healthy());
        r.put("message", s.message());
        r.put("buildHostId", buildHost == null ? null : buildHost.getId());
        r.put("buildHostName", buildHost == null ? null : buildHost.getName());
        r.put("buildHostIp", buildHost == null ? null : buildHost.getIp());
        return r;
    }

    /** 指定构建机：body { "hostId": 1 } → 标记 + SSH 自动装 Docker + 绑定 */
    @PutMapping("/build-machine")
    public Map<String, Object> setBuildMachine(@RequestBody Map<String, Object> body) {
        Long hostId = Long.valueOf(String.valueOf(body.get("hostId")));
        HostService.SetupResult r = hostService.setBuildMachine(hostId);
        return Map.of("ok", r.ok(), "message", r.message(), "steps", r.steps());
    }

    /** 取消构建机，Docker 环境切回本机 */
    @DeleteMapping("/build-machine")
    public Map<String, Object> unsetBuildMachine() {
        hostService.unsetBuildMachine();
        return Map.of("ok", true);
    }
}
