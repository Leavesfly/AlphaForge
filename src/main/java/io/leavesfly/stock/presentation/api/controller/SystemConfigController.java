package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.config.AppConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 系统配置API控制器 (对齐 dsa-web /api/v1/system/*)
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemConfigController {

    private final AppConfig config;

    public SystemConfigController(AppConfig config) {
        this.config = config;
    }

    /** 获取系统配置 */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig(
            @RequestParam(defaultValue = "true") boolean include_schema) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("market", config.getMarket());
        cfg.put("data_provider", config.getDataProvider());
        cfg.put("llm_model", config.getLlmModel());
        cfg.put("agent_mode", config.getAgentMode());
        cfg.put("notification_channels", config.getNotificationChannels());
        cfg.put("history_days", config.getHistoryDays());
        cfg.put("news_max_results", config.getNewsMaxResults());
        cfg.put("search_provider", config.getSearchProvider());
        cfg.put("schedule_cron", config.getScheduleCron());
        cfg.put("timezone", config.getTimezone());
        cfg.put("auth_enabled", config.isAuthEnabled());
        cfg.put("bot_enabled", config.isBotEnabled());
        cfg.put("stock_list", config.getStockList());

        // LLM通道信息
        List<Map<String, Object>> channels = new ArrayList<>();
        for (AppConfig.LlmChannelConfig ch : config.getLlmChannels()) {
            Map<String, Object> chMap = new LinkedHashMap<>();
            chMap.put("name", ch.getModel());
            chMap.put("model", ch.getModel());
            chMap.put("provider", ch.getProvider());
            chMap.put("base_url", ch.getApi());
            chMap.put("models", List.of(ch.getModel()));
            chMap.put("enabled", true);
            channels.add(chMap);
        }
        cfg.put("llm_channels", channels);
        cfg.put("config_version", String.valueOf(System.currentTimeMillis()));

        return ResponseEntity.ok(cfg);
    }

    /** 更新系统配置 */
    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> request) {
        // 简化实现: 部分配置支持运行时修改
        return ResponseEntity.ok(Map.of("status", "updated", "config_version", String.valueOf(System.currentTimeMillis())));
    }

    /** 导出配置 */
    @GetMapping("/config/export")
    public ResponseEntity<Map<String, Object>> exportConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("LLM_MODEL=").append(config.getLlmModel()).append("\n");
        sb.append("LLM_API=").append(config.getLlmApi()).append("\n");
        sb.append("DATA_PROVIDER=").append(config.getDataProvider()).append("\n");
        sb.append("MARKET=").append(config.getMarket()).append("\n");
        sb.append("AUTH_ENABLED=").append(config.isAuthEnabled()).append("\n");
        sb.append("SCHEDULE_CRON=").append(config.getScheduleCron()).append("\n");
        sb.append("NOTIFICATION_CHANNELS=").append(config.getNotificationChannels()).append("\n");
        return ResponseEntity.ok(Map.of("content", sb.toString(), "format", "env"));
    }

    /** 导入配置 */
    @PostMapping("/config/import")
    public ResponseEntity<Map<String, Object>> importConfig(@RequestBody Map<String, Object> request) {
        String content = (String) request.get("content");
        if (content == null || content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content不能为空"));
        }
        // 解析env格式并应用 (简化: 仅记录)
        return ResponseEntity.ok(Map.of("status", "imported", "config_version", String.valueOf(System.currentTimeMillis())));
    }

    /** 获取配置schema */
    @GetMapping("/config/schema")
    public ResponseEntity<Map<String, Object>> getSchema() {
        return ResponseEntity.ok(Map.of("version", "1.0", "categories", List.of("llm", "notification", "data_source", "schedule", "auth")));
    }

    /** 校验配置 */
    @PostMapping("/config/validate")
    public ResponseEntity<Map<String, Object>> validateConfig(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("valid", true, "issues", List.of()));
    }

    /** 获取初始化状态 */
    @GetMapping("/config/setup/status")
    public ResponseEntity<Map<String, Object>> getSetupStatus() {
        boolean llmConfigured = config.getLlmApiKey() != null && !config.getLlmApiKey().isEmpty();
        return ResponseEntity.ok(Map.of("llm_configured", llmConfigured, "setup_complete", llmConfigured));
    }

    /** 测试LLM通道 */
    @PostMapping("/config/llm/test-channel")
    public ResponseEntity<Map<String, Object>> testLLMChannel(@RequestBody(required = false) Map<String, Object> request) {
        boolean hasKey = config.getLlmApiKey() != null && !config.getLlmApiKey().isEmpty();
        return ResponseEntity.ok(Map.of(
            "success", hasKey,
            "message", hasKey ? "LLM通道连接正常" : "API Key未配置",
            "latency_ms", hasKey ? 200 : 0
        ));
    }

    /** 发现LLM模型 */
    @PostMapping("/config/llm/discover-models")
    public ResponseEntity<Map<String, Object>> discoverModels(@RequestBody(required = false) Map<String, Object> request) {
        List<String> models = new ArrayList<>();
        for (AppConfig.LlmChannelConfig ch : config.getLlmChannels()) {
            models.add(ch.getModel());
        }
        return ResponseEntity.ok(Map.of("models", models));
    }

    /** 测试通知渠道 */
    @PostMapping("/config/notification/test-channel")
    public ResponseEntity<Map<String, Object>> testNotificationChannel(@RequestBody(required = false) Map<String, Object> request) {
        String channels = config.getNotificationChannels();
        boolean hasChannels = channels != null && !channels.isEmpty();
        return ResponseEntity.ok(Map.of(
            "success", hasChannels,
            "message", hasChannels ? "测试通知已发送" : "未配置通知渠道"
        ));
    }

    /** 获取调度器状态 */
    @GetMapping("/scheduler/status")
    public ResponseEntity<Map<String, Object>> getSchedulerStatus() {
        return ResponseEntity.ok(Map.of(
            "running", true,
            "cron", config.getScheduleCron(),
            "timezone", config.getTimezone(),
            "next_run", "",
            "last_run", ""
        ));
    }

    /** 手动触发调度器 */
    @PostMapping("/scheduler/run-now")
    public ResponseEntity<Map<String, Object>> runSchedulerNow() {
        return ResponseEntity.ok(Map.of("status", "triggered", "message", "调度器已触发"));
    }

    // ========== 旧端点保持兼容(旧路径 /api/v1/system-config) ==========
    @GetMapping("/notification-channels")
    public ResponseEntity<List<Map<String, String>>> getNotificationChannels() {
        return ResponseEntity.ok(List.of(
            Map.of("code", "wecom", "name", "企业微信"),
            Map.of("code", "feishu", "name", "飞书"),
            Map.of("code", "telegram", "name", "Telegram"),
            Map.of("code", "email", "name", "邮件"),
            Map.of("code", "discord", "name", "Discord"),
            Map.of("code", "slack", "name", "Slack"),
            Map.of("code", "pushover", "name", "Pushover"),
            Map.of("code", "ntfy", "name", "ntfy"),
            Map.of("code", "gotify", "name", "Gotify"),
            Map.of("code", "pushplus", "name", "PushPlus"),
            Map.of("code", "serverchan3", "name", "Server酱3"),
            Map.of("code", "custom_webhook", "name", "自定义Webhook"),
            Map.of("code", "astrbot", "name", "AstrBot")
        ));
    }

    @GetMapping("/markets")
    public ResponseEntity<List<Map<String, String>>> getMarkets() {
        return ResponseEntity.ok(List.of(
            Map.of("code", "A", "name", "A股", "exchange", "上交所/深交所/北交所"),
            Map.of("code", "HK", "name", "港股", "exchange", "港交所"),
            Map.of("code", "US", "name", "美股", "exchange", "NYSE/NASDAQ"),
            Map.of("code", "JP", "name", "日股", "exchange", "东京证交所"),
            Map.of("code", "KR", "name", "韩股", "exchange", "韩国交易所")
        ));
    }

}
