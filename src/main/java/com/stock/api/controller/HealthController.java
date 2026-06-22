package com.stock.api.controller;

import com.stock.config.AppConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 系统健康检查和状态API
 * 
 * 对应Python版本的 api/v1/endpoints/health.py
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final AppConfig config;
    private final LocalDateTime startTime = LocalDateTime.now();

    public HealthController(AppConfig config) {
        this.config = config;
    }

    /**
     * 健康检查
     * GET /api/v1/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "healthy");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("uptime", java.time.Duration.between(startTime, LocalDateTime.now()).toSeconds() + "s");
        status.put("version", "1.0.0");
        return ResponseEntity.ok(status);
    }

    /**
     * 系统信息
     * GET /api/v1/system
     */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> systemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("java_version", System.getProperty("java.version"));
        info.put("os", System.getProperty("os.name"));
        info.put("market", config.getMarket());
        info.put("data_provider", config.getDataProvider());
        info.put("llm_model", config.getLlmModel());
        info.put("agent_mode", config.getAgentMode());
        info.put("notification_channels", config.getNotificationChannels());
        info.put("bot_enabled", config.isBotEnabled());
        info.put("auth_enabled", config.isAuthEnabled());
        info.put("start_time", startTime.toString());
        return ResponseEntity.ok(info);
    }
}
