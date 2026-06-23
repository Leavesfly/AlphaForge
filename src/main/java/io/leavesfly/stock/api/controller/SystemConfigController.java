package io.leavesfly.stock.api.controller;

import io.leavesfly.stock.config.AppConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 系统配置API控制器
 * 对应Python版本的 api/v1/endpoints/system_config.py
 */
@RestController
@RequestMapping("/api/v1/system-config")
public class SystemConfigController {

    private final AppConfig config;

    public SystemConfigController(AppConfig config) {
        this.config = config;
    }

    /** 获取系统配置(脱敏) */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
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
        return ResponseEntity.ok(cfg);
    }

    /** 获取支持的通知渠道列表 */
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

    /** 获取支持的市场列表 */
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
