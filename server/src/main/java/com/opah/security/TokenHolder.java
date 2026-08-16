package com.opah.security;

import java.util.UUID;

/** 会话令牌（MVP：进程内随机 token，重启失效） */
public final class TokenHolder {

    private static String token = UUID.randomUUID().toString();

    public static String get() {
        return token;
    }

    public static synchronized void rotate() {
        token = UUID.randomUUID().toString();
    }
}
