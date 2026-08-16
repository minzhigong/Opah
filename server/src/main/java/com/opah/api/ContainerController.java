package com.opah.api;

import com.opah.service.ContainerService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 容器操作 API（OPS-02/03） */
@RestController
@RequestMapping("/api/v1")
public class ContainerController {

    private final ContainerService containerService;

    public ContainerController(ContainerService containerService) {
        this.containerService = containerService;
    }

    @PostMapping("/hosts/{hostId}/containers/{containerId}/actions")
    public Map<String, Object> action(@PathVariable Long hostId,
                                      @PathVariable String containerId,
                                      @RequestBody Map<String, String> body) {
        containerService.action(hostId, containerId, body.get("action"));
        return Map.of("ok", true);
    }

    @PostMapping("/hosts/{hostId}/containers/{containerId}/logs")
    public Map<String, Object> logs(@PathVariable Long hostId,
                                    @PathVariable String containerId,
                                    @RequestBody Map<String, Object> body) {
        int lines = body.get("lines") == null ? 200 : Integer.parseInt(String.valueOf(body.get("lines")));
        return Map.of("logs", containerService.tail(hostId, containerId, lines));
    }
}
