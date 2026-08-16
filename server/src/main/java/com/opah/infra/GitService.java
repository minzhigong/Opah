package com.opah.infra;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Git 操作（JGit）：bare 缓存 clone + 增量 fetch + 工作区 checkout。
 * 缓存布局：{dataDir}/repos/{projectId}/bare.git 与 {dataDir}/repos/{projectId}/work/{serviceId}
 */
@Component
public class GitService {

    public record GitCredential(String username, String token) {
    }

    public record CheckoutResult(String commitSha, String shortMessage, String fullMessage) {
    }

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private final Path reposDir;

    public GitService(@Value("${opah.data-dir:./data}") String dataDir) {
        this.reposDir = Path.of(dataDir, "repos");
    }

    /** 连通性校验：ls-remote 取默认分支最新 commit */
    public String lsRemoteHead(String url, GitCredential cred) {
        try {
            org.eclipse.jgit.api.LsRemoteCommand cmd =
                    Git.lsRemoteRepository().setRemote(url).setHeads(true).setTags(false);
            applyCred(cmd, cred);
            Iterable<Ref> refs = cmd.call();
            for (Ref ref : refs) {
                String name = ref.getName();
                if (name.endsWith("/main") || name.endsWith("/master")) {
                    return ref.getObjectId().name();
                }
            }
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("仓库不可达: " + e.getMessage(), e);
        }
    }

    private void applyCred(Object cmd, GitCredential cred) {
        if (cred != null && cmd instanceof org.eclipse.jgit.api.TransportCommand<?, ?> tc) {
            tc.setCredentialsProvider(
                    new UsernamePasswordCredentialsProvider(cred.username(), cred.token()));
        }
    }

    /** bare clone（已存在则 fetch --all --prune） */
    public synchronized void syncBare(Long projectId, String url, GitCredential cred) {
        Path bare = reposDir.resolve(projectId + "/bare.git");
        try {
            if (Files.exists(bare)) {
                try (Git git = Git.open(bare.toFile())) {
                    var fetch = git.fetch().setRemoveDeletedRefs(true);
                    if (cred != null) {
                        fetch.setCredentialsProvider(
                                new UsernamePasswordCredentialsProvider(cred.username(), cred.token()));
                    }
                    fetch.call();
                }
                return;
            }
            Files.createDirectories(bare.getParent());
            var cloneCmd = Git.cloneRepository()
                    .setURI(url)
                    .setBare(true)
                    .setCloneAllBranches(true)
                    .setDirectory(bare.toFile());
            if (cred != null) {
                cloneCmd.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(cred.username(), cred.token()));
            }
            cloneCmd.call();
            log.info("bare cloned: {} -> {}", url, bare);
        } catch (Exception e) {
            throw new IllegalStateException("Git 同步失败: " + e.getMessage(), e);
        }
    }

    /** 将指定 ref 检出到单元工作区，返回 commit 信息 */
    public CheckoutResult checkoutWork(Long projectId, Long serviceId, String ref) {
        Path bare = reposDir.resolve(projectId + "/bare.git");
        Path work = reposDir.resolve(projectId + "/work/" + serviceId);
        try {
            Files.createDirectories(work.getParent());
            try (Repository bareRepo = new FileRepositoryBuilder()
                    .setGitDir(bare.toFile())
                    .readEnvironment()
                    .findGitDir()
                    .build();
                 Git git = new Git(bareRepo)) {

                String commitSha = resolveRef(bareRepo, ref);

                // 工作区以独立 clone 一次性生成，保证干净
                deleteRecursive(work);
                try (Git workGit = Git.cloneRepository()
                        .setURI(bare.toUri().toString())
                        .setDirectory(work.toFile())
                        .setNoCheckout(true)
                        .call()) {
                    workGit.checkout().setName(commitSha).call();
                }

                String shortMsg;
                String fullMsg;
                try (Repository workRepo = new FileRepositoryBuilder()
                        .setGitDir(work.resolve(".git").toFile())
                        .readEnvironment()
                        .findGitDir()
                        .build()) {
                    var commit = workRepo.parseCommit(org.eclipse.jgit.lib.ObjectId.fromString(commitSha));
                    shortMsg = commit.getShortMessage();
                    fullMsg = commit.getFullMessage();
                }
                return new CheckoutResult(commitSha, shortMsg, fullMsg);
            }
        } catch (Exception e) {
            throw new IllegalStateException("checkout 失败: " + e.getMessage(), e);
        }
    }

    private String resolveRef(Repository repo, String ref) throws Exception {
        Ref r = repo.getRefDatabase().findRef(ref);
        if (r != null) {
            return r.getObjectId().name();
        }
        return ref;
    }

    /** 单元工作区根目录（构建上下文用） */
    public Path unitWorkDir(Long projectId, Long serviceId) {
        return reposDir.resolve(projectId + "/work/" + serviceId);
    }

    private void deleteRecursive(Path p) throws Exception {
        if (Files.exists(p)) {
            try (var walk = Files.walk(p)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }
}
