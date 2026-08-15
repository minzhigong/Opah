package com.opah.infra;

/** SSH 命令执行结果 */
public record SshResult(int exitCode, String stdout, String stderr) {

    public boolean isSuccess() {
        return exitCode == 0;
    }
}
