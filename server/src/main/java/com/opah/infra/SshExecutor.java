package com.opah.infra;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.stereotype.Component;

/** 目标主机 SSH 命令执行（M1：每次调用建立连接，后续迭代引入连接池） */
@Component
public class SshExecutor {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(60);

    public SshResult execute(String host, int port, String username, String password, String command) {
        SshClient client = SshClient.setUpDefaultClient();
        client.start();
        try {
            try (ClientSession session = client.connect(username, host, port)
                .verify((int) CONNECT_TIMEOUT.toMillis()).getSession()) {
                session.addPasswordIdentity(password);
                session.auth().verify((int) AUTH_TIMEOUT.toMillis());
                return execCommand(session, command);
            }
        } catch (Exception e) {
            throw new IllegalStateException("SSH 执行失败: " + e.getMessage(), e);
        } finally {
            client.stop();
        }
    }

    private SshResult execCommand(ClientSession session, String command) throws Exception {
        try (ClientChannel channel = session.createExecChannel(command)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setOut(out);
            channel.setErr(err);
            channel.open().verify(CONNECT_TIMEOUT.toMillis());
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), COMMAND_TIMEOUT.toMillis());
            Integer exit = channel.getExitStatus();
            return new SshResult(exit == null ? -1 : exit,
                out.toString(StandardCharsets.UTF_8).trim(),
                err.toString(StandardCharsets.UTF_8).trim());
        }
    }
}
