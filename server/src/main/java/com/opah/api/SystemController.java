package com.opah.api;

import com.opah.infra.DockerClientFactory;
import com.opah.infra.DockerClientFactory.DockerProbe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final DockerClientFactory dockerClientFactory;

    public SystemController(DockerClientFactory dockerClientFactory) {
        this.dockerClientFactory = dockerClientFactory;
    }

    /** 本机 Docker daemon 连通性探测（恒 200，以 connected 字段表达状态） */
    @GetMapping("/docker")
    public DockerProbe docker() {
        return dockerClientFactory.probe();
    }
}
