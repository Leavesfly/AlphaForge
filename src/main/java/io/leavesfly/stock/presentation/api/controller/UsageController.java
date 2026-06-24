package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.application.service.AnalysisHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

/**
 * 使用量统计API控制器 (对齐 dsa-web usageApi)
 * 对应Python版本的 api/v1/endpoints/usage.py
 * 统议LLM Token用量、分析次数等
 */
@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final AnalysisHistoryService historyService;
    private final AppConfig config;

    public UsageController(AnalysisHistoryService historyService, AppConfig config) {
        this.historyService = historyService;
        this.config = config;
    }

    /** 用量仪表盘 (对齐 dsa-web usageApi.getDashboard) */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "50") int limit) {
        long totalCalls = historyService.getTotalAnalysisCount();

        // 计算日期范围
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate;
        switch (period) {
            case "today":
                fromDate = toDate;
                break;
            case "all":
                fromDate = toDate.minusYears(10);
                break;
            default: // "month"
                fromDate = toDate.minusDays(30);
                break;
        }

        // 按调用类型统计
        List<Map<String, Object>> byCallType = List.of(
            Map.of("call_type", "analysis", "calls", totalCalls, "prompt_tokens", 0, "completion_tokens", 0, "total_tokens", 0),
            Map.of("call_type", "chat", "calls", 0, "prompt_tokens", 0, "completion_tokens", 0, "total_tokens", 0),
            Map.of("call_type", "screening", "calls", 0, "prompt_tokens", 0, "completion_tokens", 0, "total_tokens", 0)
        );

        // 按模型统计
        List<Map<String, Object>> byModel = new ArrayList<>();
        for (var ch : config.getLlmChannels()) {
            byModel.add(Map.of(
                "model", ch.getModel(),
                "calls", 0,
                "prompt_tokens", 0,
                "completion_tokens", 0,
                "total_tokens", 0,
                "max_total_tokens", 0
            ));
        }

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("period", period);
        dashboard.put("from_date", fromDate.toString());
        dashboard.put("to_date", toDate.toString());
        dashboard.put("total_calls", totalCalls);
        dashboard.put("total_prompt_tokens", 0);
        dashboard.put("total_completion_tokens", 0);
        dashboard.put("total_tokens", 0);
        dashboard.put("by_call_type", byCallType);
        dashboard.put("by_model", byModel);
        dashboard.put("recent_calls", List.of());
        return ResponseEntity.ok(dashboard);
    }

    /** 旧端点兼容 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getUsage() {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("total_requests", historyService.getTotalAnalysisCount());
        usage.put("total_tokens", 0);
        usage.put("total_cost", 0.0);
        usage.put("model_count", config.getLlmChannels().size());
        usage.put("llm_model", config.getLlmModel());
        usage.put("agent_mode", config.getAgentMode());
        List<Map<String, Object>> byModel = new ArrayList<>();
        for (var ch : config.getLlmChannels()) {
            byModel.add(Map.of("model", ch.getModel(), "total_tokens", 0, "request_count", 0));
        }
        usage.put("by_model", byModel);
        return ResponseEntity.ok(usage);
    }

    /** 每日用量统计 */
    @GetMapping("/daily")
    public ResponseEntity<Map<String, Object>> getDailyUsage() {
        return ResponseEntity.ok(Map.of("items", List.of()));
    }
}
