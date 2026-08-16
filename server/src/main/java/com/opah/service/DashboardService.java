package com.opah.service;

import com.opah.domain.BuildEntity;
import com.opah.domain.BuildRepository;
import com.opah.domain.DeploymentEntity;
import com.opah.domain.DeploymentRepository;
import com.opah.domain.HostEntity;
import com.opah.domain.HostRepository;
import com.opah.domain.ProjectEntity;
import com.opah.domain.ProjectRepository;
import com.opah.domain.ServiceEntity;
import com.opah.domain.ServiceRepository;
import com.opah.infra.DockerClientFactory;
import com.opah.infra.DockerStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Dashboard 聚合（OPS-01） */
@Service
public class DashboardService {

    private final ServiceRepository services;
    private final BuildRepository builds;
    private final DeploymentRepository deployments;
    private final HostRepository hosts;
    private final ProjectRepository projects;
    private final DockerClientFactory dockerFactory;

    public DashboardService(ServiceRepository services, BuildRepository builds,
                            DeploymentRepository deployments, HostRepository hosts,
                            ProjectRepository projects, DockerClientFactory dockerFactory) {
        this.services = services;
        this.builds = builds;
        this.deployments = deployments;
        this.hosts = hosts;
        this.projects = projects;
        this.dockerFactory = dockerFactory;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Docker 状态
        DockerStatus docker = dockerFactory.ping();
        result.put("docker", Map.of("healthy", docker.healthy(), "message", docker.message()));

        // 主机健康
        List<HostEntity> hostList = hosts.findAll();
        long online = hostList.stream().filter(h -> "ONLINE".equals(h.getStatus())).count();
        result.put("hosts", Map.of("total", hostList.size(), "online", online));

        // 服务统计
        List<ServiceEntity> serviceList = services.findAll();
        int runningCount = 0;
        for (ServiceEntity svc : serviceList) {
            if (svc.getCurrentBuildId() != null) {
                runningCount++;
            }
        }
        result.put("services", Map.of("total", serviceList.size(), "running", runningCount));

        // 最近部署
        List<Map<String, Object>> recent = deployments.findTop10ByOrderByStartedAtDesc().stream()
                .limit(10)
                .map(d -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", d.getId());
                    row.put("serviceId", d.getServiceId());
                    row.put("status", d.getStatus());
                    row.put("startedAt", d.getStartedAt());
                    return row;
                })
                .toList();
        result.put("recentDeployments", recent);
        return result;
    }
}
