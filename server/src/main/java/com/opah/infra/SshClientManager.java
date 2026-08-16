package com.opah.infra;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SSH 执行器（MINA sshd）：连接池 + exec + 流式管道 + SFTP。
 */
@Component
public class SshClientManager {

    private static final Logger log = LoggerFactory.getLogger(SshClientManager.class);
    private static final long CONNECT_TIMEOUT_MS = 10_000;
    private static final long AUTH_TIMEOUT_MS = 15_000;
    private static final long CHANNEL_TIMEOUT_MS = 15_000;

    public record SshResult(int exitCode, String stdout, String stderr) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    public record HostAuth(String host, int port, String username, char[] password, String privateKey) {
    }

    private final Map<String, ClientSession> pool = new ConcurrentHashMap<>();
    private volatile SshClient client;

    private synchronized SshClient sshClient() {
        if (client == null) {
            SshClient c = SshClient.setUpDefaultClient();
            c.start();
            client = c;
        }
        return client;
    }

    private String key(HostAuth a) {
        return a.host() + ":" + a.port() + ":" + a.username();
    }

    private ClientSession acquire(HostAuth a) throws Exception {
        String k = key(a);
        ClientSession pooled = pool.get(k);
        if (pooled != null && pooled.isOpen()) {
            return pooled;
        }
        ClientSession session = sshClient().connect(a.username(), a.host(), a.port())
                .verify(CONNECT_TIMEOUT_MS)
                .getSession();
        if (a.password() != null && a.password().length > 0) {
            session.addPasswordIdentity(new String(a.password()));
        }
        if (a.privateKey() != null && !a.privateKey().isBlank()) {
            Path keyFile = Files.createTempFile("opah-key", ".pem");
            try {
                Files.writeString(keyFile, a.privateKey());
                KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();
                List<KeyPair> pairs = new java.util.ArrayList<>(
                        loader.loadKeyPairs(null, keyFile, null));
                if (!pairs.isEmpty()) {
                    session.addPublicKeyIdentity(pairs.get(0));
                }
            } finally {
                Files.deleteIfExists(keyFile);
            }
        }
        session.auth().verify(AUTH_TIMEOUT_MS);
        pool.put(k, session);
        log.debug("ssh session established {}@{}:{}", a.username(), a.host(), a.port());
        return session;
    }

    /** 普通命令执行 */
    public SshResult execute(HostAuth a, String command, Duration timeout) {
        try {
            ClientSession session = acquire(a);
            return runChannel(session, command, null, timeout);
        } catch (Exception e) {
            evict(a);
            throw new IllegalStateException("SSH 命令执行失败: " + e.getMessage(), e);
        }
    }

    /** 流式管道：stdin 写入远端命令（docker save | ssh docker load） */
    public SshResult pipe(HostAuth a, String command, InputStream stdinData, Duration timeout) {
        try {
            ClientSession session = acquire(a);
            return runChannel(session, command, stdinData, timeout);
        } catch (Exception e) {
            evict(a);
            throw new IllegalStateException("SSH 管道执行失败: " + e.getMessage(), e);
        }
    }

    private SshResult runChannel(ClientSession session, String command, InputStream stdinData, Duration timeout)
            throws Exception {
        try (ClientChannel channel = session.createExecChannel(command)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setOut(out);
            channel.setErr(err);
            org.apache.sshd.common.io.IoOutputStream chIn = stdinData != null ? channel.getAsyncIn() : null;
            channel.open().verify(CHANNEL_TIMEOUT_MS);
            if (stdinData != null && chIn != null) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = stdinData.read(buf)) >= 0) {
                    if (n > 0) {
                        org.apache.sshd.common.util.buffer.Buffer buffer =
                                new org.apache.sshd.common.util.buffer.ByteArrayBuffer(buf, 0, n);
                        chIn.writeBuffer(buffer);
                    }
                }
                chIn.close();
            }
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout.toMillis());
            Integer exit = channel.getExitStatus();
            return new SshResult(exit == null ? -1 : exit,
                    out.toString(StandardCharsets.UTF_8).trim(),
                    err.toString(StandardCharsets.UTF_8).trim());
        }
    }

    /** SFTP 上传（自动创建父目录） */
    public void upload(HostAuth a, String remotePath, byte[] content) {
        try {
            ClientSession session = acquire(a);
            SftpClient sftp = SftpClientFactory.instance().createSftpClient(session);
            try (sftp) {
                int slash = remotePath.lastIndexOf('/');
                if (slash > 0) {
                    mkdirsSftp(sftp, remotePath.substring(0, slash));
                }
                try (OutputStream os = sftp.write(remotePath)) {
                    os.write(content);
                }
            }
        } catch (Exception e) {
            evict(a);
            throw new IllegalStateException("SFTP 上传失败: " + e.getMessage(), e);
        }
    }

    private void mkdirsSftp(SftpClient sftp, String path) throws Exception {
        String[] parts = path.split("/");
        StringBuilder cur = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            cur.append('/').append(part);
            String dir = cur.toString();
            try {
                sftp.stat(dir);
            } catch (Exception notExist) {
                sftp.mkdir(dir);
            }
        }
    }

    private void evict(HostAuth a) {
        ClientSession s = pool.remove(key(a));
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }
}
