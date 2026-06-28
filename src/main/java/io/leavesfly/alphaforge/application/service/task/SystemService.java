package io.leavesfly.alphaforge.application.service.task;

import io.leavesfly.alphaforge.application.service.report.AnalysisHistoryService;
import io.leavesfly.alphaforge.application.service.signal.DecisionSignalService;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.config.AppConfig;
import io.leavesfly.alphaforge.domain.service.port.LlmUsagePort;
import io.leavesfly.alphaforge.infrastructure.persistence.analysis.AnalysisTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统管理应用服务
 *
 * 封装健康检查、用量查询、仪表盘统计等系统级编排，
 * 避免表现层直接依赖基础设施（LlmUsageTracker/Repository/DataSource）。
 */
@Service
public class SystemService {

    private static final Logger log = LoggerFactory.getLogger(SystemService.class);

    private final LlmUsagePort usageTracker;
    private final AnalysisTaskRepository analysisTaskRepository;
    private final DataSource dataSource;
    private final StrategyCatalog strategyCatalog;
    private final AnalysisHistoryService historyService;
    private final DecisionSignalService signalService;
    private final AppConfig config;

    public SystemService(LlmUsagePort usageTracker, AnalysisTaskRepository analysisTaskRepository,
                         DataSource dataSource, StrategyCatalog strategyCatalog,
                         AnalysisHistoryService historyService, DecisionSignalService signalService,
                         AppConfig config) {
        this.usageTracker = usageTracker;
        this.analysisTaskRepository = analysisTaskRepository;
        this.dataSource = dataSource;
        this.strategyCatalog = strategyCatalog;
        this.historyService = historyService;
        this.signalService = signalService;
        this.config = config;
    }

    /**
     * 健康检查项
     */
    public Map<String, Object> healthChecks() {
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", checkDatabase());
        checks.put("strategies_loaded", strategyCatalog.listAll().size());
        checks.put("strategies_available", strategyCatalog.listAll().stream().filter(s -> s.isAvailable()).count());
        checks.put("llm_configured", config.getLlmApiKey() != null && !config.getLlmApiKey().isEmpty());
        checks.put("pending_tasks", analysisTaskRepository.findByStatus("running").size());
        return checks;
    }

    private String checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2) ? "ok" : "degraded";
        } catch (Exception e) {
            return "error";
        }
    }

    /**
     * LLM 用量统计
     */
    public Map<String, Object> getUsage() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", usageTracker.getTodayUsage());
        result.put("overall", usageTracker.getOverallStats());
        result.put("monthly_total", usageTracker.getMonthlyStats());
        result.put("by_model", usageTracker.getModelBreakdown());
        result.put("daily_detail", usageTracker.getDailyDetail(30));
        result.put("cost_trend", usageTracker.getCostTrend(14));
        result.put("model_distribution", usageTracker.getModelDistribution(30));
        return result;
    }

    /**
     * 仪表盘统计数据
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_reports", historyService.getTotalAnalysisCount());
        stats.put("active_signals", signalService.getActiveSignals().size());
        stats.put("recent_signals", signalService.getRecentSignals());
        stats.put("usage_today", usageTracker.getTodayUsage());
        return stats;
    }
}
