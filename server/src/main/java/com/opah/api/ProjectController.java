package com.opah.api;

import com.opah.domain.CredentialEntity;
import com.opah.domain.CredentialRepository;
import com.opah.domain.ProjectEntity;
import com.opah.domain.ProjectRepository;
import com.opah.domain.ServiceEntity;
import com.opah.domain.ServiceRepository;
import com.opah.infra.CryptoService;
import com.opah.infra.GitService;
import com.opah.service.UnitScanService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 项目管理 API（PROJ-01/02/03/04/06） */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectRepository projects;
    private final ServiceRepository services;
    private final CredentialRepository credentials;
    private final CryptoService crypto;
    private final GitService git;
    private final UnitScanService unitScan;

    public ProjectController(ProjectRepository projects, ServiceRepository services,
                             CredentialRepository credentials, CryptoService crypto,
                             GitService git, UnitScanService unitScan) {
        this.projects = projects;
        this.services = services;
        this.credentials = credentials;
        this.crypto = crypto;
        this.git = git;
        this.unitScan = unitScan;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectEntity p : projects.findAll()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("gitUrl", p.getGitUrl());
            row.put("defaultBranch", p.getDefaultBranch());
            row.put("serviceCount", services.findByProjectId(p.getId()).size());
            result.add(row);
        }
        return result;
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        String gitUrl = (String) body.get("gitUrl");
        Long credentialId = body.get("credentialId") == null ? null
                : Long.valueOf(String.valueOf(body.get("credentialId")));
        String defaultBranch = body.get("defaultBranch") == null ? "main" : (String) body.get("defaultBranch");

        // 连通性校验
        GitService.GitCredential cred = resolveCredential(credentialId);
        String head = git.lsRemoteHead(gitUrl, cred);

        ProjectEntity p = new ProjectEntity((String) body.get("name"), gitUrl, defaultBranch,
                credentialId, LocalDateTime.now().format(TS));
        p = projects.save(p);

        // 同步 bare 缓存（异步，克隆可能耗时）
        final Long pid = p.getId();
        Thread.ofVirtual().start(() -> {
            try {
                git.syncBare(pid, gitUrl, cred);
            } catch (Exception e) {
                // 克隆失败不影响项目创建，后续可重试
            }
        });

        return Map.of("id", p.getId(), "head", head == null ? "" : head);
    }

    @PostMapping("/{id}/scan")
    public List<Map<String, Object>> scan(@PathVariable Long id) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UnitScanService.Candidate c : unitScan.scan(id)) {
            Map<String, Object> row = new HashMap<>();
            row.put("subPath", c.subPath());
            row.put("type", c.type());
            row.put("detail", c.detail());
            row.put("recommended", c.recommended());
            result.add(row);
        }
        return result;
    }

    /** 确认创建部署单元（勾选向导结果落库） */
    @PostMapping("/{id}/services")
    public List<Map<String, Object>> confirmServices(@PathVariable Long id,
                                                     @RequestBody List<Map<String, Object>> units) {
        ProjectEntity project = projects.findById(id).orElseThrow();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> u : units) {
            ServiceEntity svc = new ServiceEntity();
            svc.setProjectId(id);
            svc.setName((String) u.get("name"));
            svc.setType((String) u.get("type"));
            svc.setSubPath(u.get("subPath") == null ? "." : (String) u.get("subPath"));
            svc.setCreatedAt(LocalDateTime.now().format(TS));
            svc = services.save(svc);
            result.add(Map.of("id", svc.getId(), "name", svc.getName(), "type", svc.getType()));
        }
        return result;
    }

    @GetMapping("/{id}/services")
    public List<ServiceEntity> services(@PathVariable Long id) {
        return services.findByProjectIdOrderByName(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        projects.deleteById(id);
        return Map.of("ok", true);
    }

    private GitService.GitCredential resolveCredential(Long credentialId) {
        if (credentialId == null) {
            return null;
        }
        CredentialEntity c = credentials.findById(credentialId).orElse(null);
        if (c == null) {
            return null;
        }
        String secret = crypto.decrypt(c.getSecretCipher());
        // USERNAME_TOKEN 格式：username:token
        if (secret != null && secret.contains(":")) {
            int idx = secret.indexOf(':');
            return new GitService.GitCredential(secret.substring(0, idx), secret.substring(idx + 1));
        }
        return new GitService.GitCredential("git", secret);
    }
}
