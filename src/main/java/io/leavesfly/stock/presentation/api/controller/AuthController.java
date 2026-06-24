package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.config.AppConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
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
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request, HttpSession session) {
        String password = request.get("password");
        String passwordConfirm = request.get("passwordConfirm");

        // 首次设置密码场景
        if ((config.getAuthPassword() == null || config.getAuthPassword().isEmpty()) && password != null && !password.isEmpty()) {
            if (passwordConfirm != null && password.equals(passwordConfirm)) {
                // 设置初始密码 (runtime only)
                config.setAuthPasswordRuntime(password);
            }
        }

        if (password == null || !password.equals(config.getAuthPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "密码错误"));
        }

        session.setAttribute("authenticated", true);
        String token = generateToken();
        return ResponseEntity.ok(Map.of("token", token, "expires_in", 86400, "logged_in", true));
    }

    /**
     * 登出
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("status", "logged_out"));
    }

    /**
     * 认证状态
     * GET /api/v1/auth/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpSession session) {
        boolean loggedIn = Boolean.TRUE.equals(session.getAttribute("authenticated"));
        boolean authEnabled = config.isAuthEnabled();
        boolean passwordSet = config.getAuthPassword() != null && !config.getAuthPassword().isEmpty();

        String setupState;
        if (!authEnabled) setupState = "enabled";
        else if (!passwordSet) setupState = "no_password";
        else setupState = "password_retained";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("auth_enabled", authEnabled);
        result.put("logged_in", loggedIn || !authEnabled);
        result.put("password_set", passwordSet);
        result.put("password_changeable", true);
        result.put("setup_state", setupState);
        return ResponseEntity.ok(result);
    }

    /**
     * 修改密码
     * POST /api/v1/auth/change-password
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> request) {
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");
        String newPasswordConfirm = request.get("newPasswordConfirm");

        if (config.getAuthPassword() != null && !config.getAuthPassword().isEmpty()) {
            if (!config.getAuthPassword().equals(currentPassword)) {
                return ResponseEntity.status(400).body(Map.of("error", "当前密码错误"));
            }
        }
        if (newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("error", "新密码不能为空"));
        }
        if (!newPassword.equals(newPasswordConfirm)) {
            return ResponseEntity.status(400).body(Map.of("error", "确认密码不一致"));
        }

        config.setAuthPasswordRuntime(newPassword);
        return ResponseEntity.ok(Map.of("status", "password_changed"));
    }

    /**
     * 更新认证设置
     * POST /api/v1/auth/settings
     */
    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> request) {
        Boolean authEnabled = (Boolean) request.get("authEnabled");
        if (authEnabled != null) {
            config.setAuthEnabledRuntime(authEnabled);
        }

        String password = (String) request.get("password");
        if (password != null && !password.isEmpty()) {
            config.setAuthPasswordRuntime(password);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("auth_enabled", config.isAuthEnabled());
        result.put("password_set", config.getAuthPassword() != null && !config.getAuthPassword().isEmpty());
        result.put("setup_state", config.isAuthEnabled() ? "password_retained" : "enabled");
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
