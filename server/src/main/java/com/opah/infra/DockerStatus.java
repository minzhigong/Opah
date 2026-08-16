package com.opah.infra;

/** Docker daemon 状态探测结果 */
public record DockerStatus(boolean healthy, String message) {

    public static DockerStatus ok() {
        return new DockerStatus(true, "OK");
    }

    public static DockerStatus fail(String reason) {
        return new DockerStatus(false, reason);
    }
}
