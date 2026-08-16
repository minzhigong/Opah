package com.opah.api;

import com.opah.domain.UserEntity;
import com.opah.domain.UserRepository;
import com.opah.infra.DockerClientFactory;
import com.opah.infra.DockerStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统初始化 / Docker 状态（INST-03，SYS-01） */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final DockerClientFactory dockerFactory;

    public SystemController(UserRepository users, PasswordEncoder encoder, DockerClientFactory dockerFactory) {
        this.users = users;
        this.encoder = encoder;
        this.dockerFactory = dockerFactory;
    }

    /** 首次设置管理员密码 */
    @PostMapping("/setup")
    public ResponseEntity<?> setup(@RequestBody Map<String, String> body) {
        if (users.count() > 0) {
            return ResponseEntity.status(409).body(Map.of("error", "已初始化，无需重复设置"));
        }
        String username = body.getOrDefault("username", "admin");
        String password = body.get("password");
        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码至少 6 位"));
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setRole("ADMIN");
        user.setCreatedAt(LocalDateTime.now().format(TS));
        users.save(user);
        com.opah.security.TokenHolder.rotate();
        return ResponseEntity.ok(Map.of("ok", true, "token", com.opah.security.TokenHolder.get()));
    }

    /** 是否已初始化 */
    @GetMapping("/setup-status")
    public Map<String, Object> setupStatus() {
        return Map.of("initialized", users.count() > 0);
    }

    /** Docker Desktop 检测（INST-03 引导用） */
    @GetMapping("/docker-status")
    public Map<String, Object> dockerStatus() {
        DockerStatus s = dockerFactory.ping();
        return Map.of("healthy", s.healthy(), "message", s.message());
    }
}
