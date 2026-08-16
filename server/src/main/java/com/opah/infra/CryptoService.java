package com.opah.infra;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 凭据加密：AES-256-GCM。
 * 主密钥来源优先级：环境变量 OPAH_SECRET_KEY > ./data/secret.key（首启自动生成，Base64 32字节）。
 */
@Component
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final Path dataDir;
    private SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${opah.data-dir:./data}") String dataDir) {
        this.dataDir = Path.of(dataDir);
    }

    @PostConstruct
    void init() throws Exception {
        String env = System.getenv("OPAH_SECRET_KEY");
        byte[] keyBytes;
        if (env != null && !env.isBlank()) {
            keyBytes = MessageDigest.getInstance("SHA-256").digest(env.getBytes(StandardCharsets.UTF_8));
            log.info("crypto key loaded from env OPAH_SECRET_KEY");
        } else {
            Path keyFile = dataDir.resolve("secret.key");
            Files.createDirectories(dataDir);
            if (Files.exists(keyFile)) {
                keyBytes = Base64.getDecoder().decode(Files.readString(keyFile).trim());
                if (keyBytes.length != 32) {
                    // 兼容任意长度旧密钥：派生
                    keyBytes = MessageDigest.getInstance("SHA-256").digest(keyBytes);
                }
            } else {
                keyBytes = new byte[32];
                random.nextBytes(keyBytes);
                Files.writeString(keyFile, Base64.getEncoder().encodeToString(keyBytes));
                log.info("generated new master key at {}", keyFile.toAbsolutePath());
            }
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] input = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, input, 0, IV_LEN));
            byte[] plain = cipher.doFinal(input, IV_LEN, input.length - IV_LEN);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }
}
