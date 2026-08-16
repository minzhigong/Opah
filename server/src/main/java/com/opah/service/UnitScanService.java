package com.opah.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opah.infra.GitService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 部署单元扫描（PROJ-06）：遍历仓库工作区，
 * pom.xml/build.gradle → JAVA；package.json → REACT/VUE；docker-compose.yml → COMPOSE；Dockerfile → CUSTOM。
 * 扫描结果仅为建议，向导中人工确认。
 */
@Service
public class UnitScanService {

    private static final Logger log = LoggerFactory.getLogger(UnitScanService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path reposDir;
    private final GitService git;

    public UnitScanService(@Value("${opah.data-dir:./data}") String dataDir, GitService git) {
        this.reposDir = Path.of(dataDir, "repos");
        this.git = git;
    }

    public record Candidate(String subPath, String type, String detail, boolean recommended) {
    }

    /** 扫描指定项目仓库（使用最新 checkout 的工作区） */
    public List<Candidate> scan(Long projectId) {
        Path workRoot = reposDir.resolve(projectId + "/work");
        List<Candidate> result = new ArrayList<>();
        try {
            // 找任一单元工作区即可代表仓库结构（向导阶段用第一个存在的 work 目录或 bare 检出）
            Path scanRoot = findScanRoot(projectId);
            if (scanRoot == null) {
                return result;
            }
            scanDirectory(scanRoot, scanRoot, result, 0);
        } catch (Exception e) {
            throw new IllegalStateException("单元扫描失败: " + e.getMessage(), e);
        }
        return result;
    }

    private Path findScanRoot(Long projectId) throws IOException {
        Path workRoot = reposDir.resolve(projectId + "/work");
        if (Files.isDirectory(workRoot)) {
            try (var stream = Files.list(workRoot)) {
                var first = stream.filter(Files::isDirectory)
                        .filter(p -> !"_scan".equals(p.getFileName().toString()))
                        .findFirst();
                if (first.isPresent()) {
                    return first.get();
                }
            }
        }
        // 兜底：还没有任何单元工作区时，从 bare 仓库临时检出默认分支作为扫描根
        return git.checkoutScanWork(projectId);
    }

    private void scanDirectory(Path root, Path dir, List<Candidate> result, int depth) throws IOException {
        if (depth > 4) {
            return;
        }
        boolean hasPom = Files.exists(dir.resolve("pom.xml"));
        boolean hasGradle = Files.exists(dir.resolve("build.gradle"));
        boolean hasPackage = Files.exists(dir.resolve("package.json"));
        boolean hasCompose = Files.exists(dir.resolve("docker-compose.yml"));
        boolean hasDockerfile = Files.exists(dir.resolve("Dockerfile"));

        String subPath = root.equals(dir) ? "." : root.relativize(dir).toString().replace('\\', '/');

        if (hasCompose && depth == 0) {
            result.add(new Candidate(subPath, "COMPOSE", "根目录含 docker-compose.yml", true));
        }
        if (hasPom || hasGradle) {
            result.add(new Candidate(subPath, "JAVA", hasPom ? "pom.xml" : "build.gradle", true));
        }
        if (hasPackage) {
            String framework = detectFramework(dir.resolve("package.json"));
            if (framework != null) {
                result.add(new Candidate(subPath, framework, "package.json (" + framework + ")", true));
            }
        }
        if (hasDockerfile && !hasPom && !hasGradle && !hasPackage) {
            result.add(new Candidate(subPath, "CUSTOM", "自带 Dockerfile", true));
        }

        // 递归子目录（跳过 node_modules / target / .git / dist）
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String name = child.getFileName().toString();
                if (name.equals("node_modules") || name.equals("target") || name.equals(".git")
                        || name.equals("dist") || name.equals("build") || name.equals(".mvn")) {
                    continue;
                }
                scanDirectory(root, child, result, depth + 1);
            }
        }
    }

    private String detectFramework(Path packageJson) {
        try {
            JsonNode root = mapper.readTree(packageJson.toFile());
            JsonNode deps = root.get("dependencies");
            if (deps != null) {
                if (deps.has("react")) {
                    return "REACT";
                }
                if (deps.has("vue")) {
                    return "VUE";
                }
            }
            JsonNode devDeps = root.get("devDependencies");
            if (devDeps != null) {
                if (devDeps.has("react")) {
                    return "REACT";
                }
                if (devDeps.has("vue")) {
                    return "VUE";
                }
        }
        } catch (Exception e) {
            log.warn("parse package.json failed: {}", packageJson, e);
        }
        return null;
    }
}
