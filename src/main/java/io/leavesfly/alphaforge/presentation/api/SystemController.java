package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.service.task.SystemService;
import io.leavesfly.alphaforge.application.service.loop.LoopStateManager;
import io.leavesfly.alphaforge.application.service.system.DataSourceHealthService;
import io.leavesfly.alphaforge.config.LlmConfig;
import io.leavesfly.alphaforge.config.AppConfig;

import io.leavesfly.alphaforge.config.DataProviderConfig;
import io.leavesfly.alphaforge.config.NotificationConfig;
import io.leavesfly.alphaforge.config.SchedulerAuthConfig;
import io.leavesfly.alphaforge.config.ScoringConfig;
import io.leavesfly.alphaforge.config.SearchConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统管理 API — 健康检查、用量、仪表盘。
 * 业务逻辑下沉至 {@link SystemService}，Controller 仅处理 HTTP 协议。
 */
@RestController
@RequestMapping("/api/v1")
public class SystemController {

    private final AppConfig config;
    private final LlmConfig llmConfig;
    private final DataProviderConfig dataProviderConfig;
    private final SchedulerAuthConfig schedulerAuthConfig;
    private final NotificationConfig notificationConfig;
    private final SearchConfig searchConfig;
    private final ScoringConfig scoringConfig;
    private final SystemService systemService;
    private final LoopStateManager loopStateManager;
    private final DataSourceHealthService dataSourceHealthService;

    public SystemController(AppConfig config, LlmConfig llmConfig,
                            DataProviderConfig dataProviderConfig,
                            SchedulerAuthConfig schedulerAuthConfig,
                            NotificationConfig notificationConfig,
                            SearchConfig searchConfig,
                            ScoringConfig scoringConfig,
                            SystemService systemService,
                            LoopStateManager loopStateManager,
                            DataSourceHealthService dataSourceHealthService) {
        this.config = config;
        this.llmConfig = llmConfig;
        this.dataProviderConfig = dataProviderConfig;
        this.schedulerAuthConfig = schedulerAuthConfig;
        this.notificationConfig = notificationConfig;
        this.searchConfig = searchConfig;
        this.scoringConfig = scoringConfig;
        this.systemService = systemService;
        this.loopStateManager = loopStateManager;
        this.dataSourceHealthService = dataSourceHealthService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("service", "daily-alphaforge-analysis");
        result.put("timestamp", System.currentTimeMillis());
        result.put("checks", systemService.healthChecks());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/system-config")
    public ResponseEntity<Map<String, Object>> systemConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("llm_model", llmConfig.getLlmModel());
        cfg.put("agent_mode", schedulerAuthConfig.getAgentMode());
        cfg.put("market", config.getMarket());
        cfg.put("history_days", config.getHistoryDays());
        cfg.put("data_provider", dataProviderConfig.getDataProvider());
        cfg.put("search_provider", searchConfig.getSearchProvider());
        cfg.put("stock_count", config.getStockList().size());
        cfg.put("notification_channels", notificationConfig.getNotificationChannels());
        cfg.put("auth_enabled", schedulerAuthConfig.isAuthEnabled());
        cfg.put("buy_score_threshold", scoringConfig.getBuyScoreThreshold());
        cfg.put("sell_score_threshold", scoringConfig.getSellScoreThreshold());
        return ResponseEntity.ok(cfg);
    }

    @GetMapping("/usage")
    public ResponseEntity<Map<String, Object>> usage() {
        return ResponseEntity.ok(systemService.getUsage());
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> dashboardStats() {
        return ResponseEntity.ok(systemService.getDashboardStats());
    }

    /**
     * Loop 循环健康状态 - 展示系统自我感知数据
     * 包含: 准确率、Verifier调整率、循环次数、健康模式等
     */
    @GetMapping("/loop/status")
    public ResponseEntity<Map<String, Object>> loopStatus() {
        return ResponseEntity.ok(loopStateManager.getHealthReport());
    }

    /**
     * 数据源主动体检 — 逐源真实拉取探测，区分环境问题 vs 代码问题。
     *
     * @param force true 绕过 TTL 缓存手动触发（入口层已限流保护，避免体检加剧上游 429）
     */
    @GetMapping("/health/datasources")
    public ResponseEntity<Map<String, Object>> dataSourceHealth(
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        return ResponseEntity.ok(dataSourceHealthService.getCachedOrProbe(force));
    }
}
