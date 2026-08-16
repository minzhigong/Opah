package com.opah.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service")
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    private String name;

    /** JAVA / REACT / VUE / COMPOSE / CUSTOM */
    private String type;

    @Column(name = "sub_path")
    private String subPath;

    @Column(name = "build_config")
    private String buildConfig;

    @Column(name = "nginx_config")
    private String nginxConfig;

    @Column(name = "current_build_id")
    private Long currentBuildId;

    @Column(name = "created_at")
    private String createdAt;

    public ServiceEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSubPath() { return subPath; }
    public void setSubPath(String subPath) { this.subPath = subPath; }
    public String getBuildConfig() { return buildConfig; }
    public void setBuildConfig(String buildConfig) { this.buildConfig = buildConfig; }
    public String getNginxConfig() { return nginxConfig; }
    public void setNginxConfig(String nginxConfig) { this.nginxConfig = nginxConfig; }
    public Long getCurrentBuildId() { return currentBuildId; }
    public void setCurrentBuildId(Long currentBuildId) { this.currentBuildId = currentBuildId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
