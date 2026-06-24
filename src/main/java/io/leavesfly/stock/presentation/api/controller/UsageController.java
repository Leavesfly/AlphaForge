package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.application.service.AnalysisHistoryService;
import io.leavesfly.stock.infrastructure.llm.LlmUsageTracker;
import io.leavesfly.stock.infrastructure.persistence.LlmUsageDailyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

/**
 * 使用量统计API控制器 (对齐 dsa-web usageApi)
 * 统计LLM Token用量、分析次数、费用等
 */
@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final AnalysisHistoryService historyService;
    private final AppConfig config;
    private final LlmUsageTracker usageTracker;
    private final LlmUsageDailyRepository usageRepo;

    public UsageController(AnalysisHistoryService historyService, AppConfig config,
                           LlmUsageTracker usageTracker, LlmUsageDailyRepository usageRepo) {
        this.historyService = historyService;
        this.config = config;
        this.usageTracker = usageTracker;
        this.usageRepo = usageRepo;
    }

    /** 用量仪表盘 (对齐 dsa-web usageApi.getDashboard) */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "50") int limit) {

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate;
        switch (period) {
            case "today": fromDate = toDate; break;
            case "week": fromDate = toDate.minusDays(7); break;
            case "all": fromDate = toDate.minusYears(10); break;
            default: fromDate = toDate.minusDays(30); break; // "month"
        }

        // 从DB查询总量
        Map<String, Object> totals = usageRepo.getTotalStats(fromDate, toDate);
        if (totals == null) totals = Map.of("total_calls", 0, "total_prompt_tokens", 0, "total_completion_tokens", 0, "total_tokens", 0, "total_cost", 0.0);

        // 按模型聚合
        List<Map<String, Object>> byModel = usageRepo.aggregateByModel(fromDate, toDate);
        if (byModel == null) byModel = List.of();

        // 按日期聚合
        List<Map<String, Object>> byDate = usageRepo.aggregateByDate(fromDate, toDate);
        if (byDate == null) byDate = List.of();

        // 构建响应
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("period", period);
        dashboard.put("from_date", fromDate.toString());
        dashboard.put("to_date", toDate.toString());
        dashboard.put("total_calls", totals.getOrDefault("total_calls", 0));
        dashboard.put("total_prompt_tokens", totals.getOrDefault("total_prompt_tokens", 0));
        dashboard.put("total_completion_tokens", totals.getOrDefault("total_completion_tokens", 0));
        dashboard.put("total_tokens", totals.getOrDefault("total_tokens", 0));
        dashboard.put("total_cost", totals.getOrDefault("total_cost", 0.0));
        dashboard.put("by_model", byModel);
        dashboard.put("by_date", byDate);
        dashboard.put("analysis_count", historyService.getTotalAnalysisCount());
        return ResponseEntity.ok(dashboard);
    }

    /** 旧端点兼容 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getUsage() {
        Map<String, Object> stats = usageTracker.getOverallStats();
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("total_requests", stats.get("total_calls"));
        usage.put("total_tokens", stats.get("total_tokens"));
        usage.put("total_cost", 0.0);
        usage.put("model_count", config.getLlmChannels().size());
        usage.put("llm_model", config.getLlmModel());
        usage.put("agent_mode", config.getAgentMode());
        usage.put("today", usageTracker.getTodayUsage());
        usage.put("model_breakdown", usageTracker.getModelBreakdown());
        return ResponseEntity.ok(usage);
    }

    /** 每日用量统计 (支持日期范围) */
    @GetMapping("/daily")
    public ResponseEntity<Map<String, Object>> getDailyUsage(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate toDate = to != null ? LocalDate.parse(to) : LocalDate.now();
        LocalDate fromDate = from != null ? LocalDate.parse(from) : toDate.minusDays(30);
        List<Map<String, Object>> items = usageRepo.aggregateByDate(fromDate, toDate);
        return ResponseEntity.ok(Map.of("items", items != null ? items : List.of(), "from", fromDate.toString(), "to", toDate.toString()));
    }

    /** 今日用量 */
    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> getTodayUsage() {
        return ResponseEntity.ok(usageTracker.getTodayUsage());
    }
}
