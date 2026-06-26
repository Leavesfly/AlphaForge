package io.leavesfly.stock.application.service;

import io.leavesfly.stock.application.strategy.StrategyCatalog;
import io.leavesfly.stock.application.strategy.StrategyCatalogLoader;
import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.infrastructure.llm.LlmUsageTracker;
import io.leavesfly.stock.infrastructure.persistence.AnalysisTaskRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统管理 API — 扩展健康检查。
 */
@RestController
@RequestMapping("/api/v1")
public class SystemController {

    private final AppConfig config;
    private final AnalysisHistoryService historyService;
    private final DecisionSignalService signalService;
    private final LlmUsageTracker usageTracker;
    private final DataSource dataSource;
    private final StrategyCatalog strategyCatalog;
    private final AnalysisTaskRepository analysisTaskRepository;

    public SystemController(AppConfig config, AnalysisHistoryService historyService,
                           DecisionSignalService signalService, LlmUsageTracker usageTracker,
                           DataSource dataSource, StrategyCatalog strategyCatalog,
                           AnalysisTaskRepository analysisTaskRepository) {
        this.config = config;
        this.historyService = historyService;
        this.signalService = signalService;
        this.usageTracker = usageTracker;
        this.dataSource = dataSource;
        this.strategyCatalog = strategyCatalog;
        this.analysisTaskRepository = analysisTaskRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("service", "daily-stock-analysis");
        result.put("timestamp", System.currentTimeMillis());

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", checkDatabase());
        checks.put("strategies_loaded", strategyCatalog.listAll().size());
        checks.put("strategies_available", strategyCatalog.listAll().stream().filter(s -> s.isAvailable()).count());
        checks.put("llm_configured", config.getLlmApiKey() != null && !config.getLlmApiKey().isEmpty());
        checks.put("pending_tasks", analysisTaskRepository.findByStatus("running").size());
        result.put("checks", checks);
        return ResponseEntity.ok(result);
    }

    private String checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2) ? "ok" : "degraded";
        } catch (Exception e) {
            return "error";
        }
    }

    @GetMapping("/system-config")
    public ResponseEntity<Map<String, Object>> systemConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("llm_model", config.getLlmModel());
        cfg.put("agent_mode", config.getAgentMode());
        cfg.put("market", config.getMarket());
        cfg.put("history_days", config.getHistoryDays());
        cfg.put("data_provider", config.getDataProvider());
        cfg.put("search_provider", config.getSearchProvider());
        cfg.put("stock_count", config.getStockList().size());
        cfg.put("notification_channels", config.getNotificationChannels());
        cfg.put("auth_enabled", config.isAuthEnabled());
        cfg.put("buy_score_threshold", config.getBuyScoreThreshold());
        cfg.put("sell_score_threshold", config.getSellScoreThreshold());
        return ResponseEntity.ok(cfg);
    }

    @GetMapping("/usage")
    public ResponseEntity<Map<String, Object>> usage() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", usageTracker.getTodayUsage());
        result.put("overall", usageTracker.getOverallStats());
        result.put("monthly_total", usageTracker.getMonthlyStats());
        result.put("by_model", usageTracker.getModelBreakdown());
        result.put("daily_detail", usageTracker.getDailyDetail(30));
        result.put("cost_trend", usageTracker.getCostTrend(14));
        result.put("model_distribution", usageTracker.getModelDistribution(30));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> dashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_reports", historyService.getTotalAnalysisCount());
        stats.put("active_signals", signalService.getActiveSignals().size());
        stats.put("recent_signals", signalService.getRecentSignals());
        stats.put("usage_today", usageTracker.getTodayUsage());
        return ResponseEntity.ok(stats);
    }
}
