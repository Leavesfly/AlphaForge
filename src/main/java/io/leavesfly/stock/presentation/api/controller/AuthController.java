package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.config.AppConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 认证API控制器
 * 对应Python版本的 api/v1/endpoints/auth.py
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AppConfig config;

    public AuthController(AppConfig config) {
        this.config = config;
    }

    /**
     * 登录
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String password = request.get("password");
        if (password == null || !password.equals(config.getAuthPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "密码错误"));
        }

        String token = generateToken();
        return ResponseEntity.ok(Map.of("token", token, "expires_in", 86400));
    }

    /**
     * 认证状态
     * GET /api/v1/auth/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("auth_enabled", config.isAuthEnabled());
        result.put("setup_required", config.isAuthEnabled() && 
                (config.getAuthPassword() == null || config.getAuthPassword().isEmpty()));
        return ResponseEntity.ok(result);
    }

    private String generateToken() {
        byte[] keyBytes = config.getAuthSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Jwts.builder()
                .subject("user")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();
    }
}
