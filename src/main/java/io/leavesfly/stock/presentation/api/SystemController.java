package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.application.service.AnalysisHistoryService;
import io.leavesfly.stock.application.service.DecisionSignalService;
import io.leavesfly.stock.infrastructure.llm.LlmUsageTracker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 系统管理 API
 * 健康检查、系统配置、LLM用量统计、仪表盘数据
 */
@RestController
@RequestMapping("/api/v1")
public class SystemController {

    private final AppConfig config;
    private final AnalysisHistoryService historyService;
    private final DecisionSignalService signalService;
    private final LlmUsageTracker usageTracker;

    public SystemController(AppConfig config, AnalysisHistoryService historyService,
                           DecisionSignalService signalService, LlmUsageTracker usageTracker) {
        this.config = config;
        this.historyService = historyService;
        this.signalService = signalService;
        this.usageTracker = usageTracker;
    }

    /** 健康检查 */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("service", "daily-stock-analysis");
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    /** 系统配置概要 */
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
        return ResponseEntity.ok(cfg);
    }

    /** LLM用量统计 */
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

    /** 仪表盘统计数据 */
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
