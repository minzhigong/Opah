package com.opah.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "host")
public class HostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String ip;

    @Column(name = "ssh_port")
    private Integer sshPort;

    private String username;

    @Column(name = "auth_credential_id")
    private Long authCredentialId;

    /** 角色：deploy=部署目标机(默认) / build=构建机 */
    private String role;

    private String status;

    @Column(name = "docker_version")
    private String dockerVersion;

    @Column(name = "os_info")
    private String osInfo;

    @Column(name = "last_seen_at")
    private String lastSeenAt;

    @Column(name = "created_at")
    private String createdAt;

    public HostEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public Integer getSshPort() { return sshPort; }
    public void setSshPort(Integer sshPort) { this.sshPort = sshPort; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getAuthCredentialId() { return authCredentialId; }
    public void setAuthCredentialId(Long authCredentialId) { this.authCredentialId = authCredentialId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDockerVersion() { return dockerVersion; }
    public void setDockerVersion(String dockerVersion) { this.dockerVersion = dockerVersion; }
    public String getOsInfo() { return osInfo; }
    public void setOsInfo(String osInfo) { this.osInfo = osInfo; }
    public String getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(String lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
