package io.leavesfly.alphaforge.presentation.api;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.leavesfly.alphaforge.config.AppConfig;
import io.leavesfly.alphaforge.presentation.api.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * 认证 API：登录获取 JWT。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final long TOKEN_TTL_MS = 24 * 60 * 60 * 1000L;

    private final AppConfig appConfig;

    public AuthController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        String configured = appConfig.getAuthPassword();
        if (configured == null || configured.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "AUTH_PASSWORD 未配置"));
        }
        if (!configured.equals(password)) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "密码错误"));
        }
        SecretKey key = Keys.hmacShaKeyFor(
                appConfig.getAuthSecret().getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TOKEN_TTL_MS))
                .signWith(key)
                .compact();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("token", token, "expires_in", TOKEN_TTL_MS / 1000)));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "auth_enabled", appConfig.isAuthEnabled(),
                "password_configured", appConfig.getAuthPassword() != null && !appConfig.getAuthPassword().isEmpty()
        )));
    }
}
