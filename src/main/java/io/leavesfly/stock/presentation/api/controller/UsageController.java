package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.application.service.AnalysisHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 使用量统计API控制器
 * 对应Python版本的 api/v1/endpoints/usage.py
 * 统计LLM Token用量、分析次数等
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

    @GetMapping
    public ResponseEntity<Map<String, Object>> getUsage() {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("total_requests", historyService.getTotalAnalysisCount());
        usage.put("total_tokens", 0);
        usage.put("total_cost", 0.0);
        usage.put("model_count", config.getLlmChannels().size());
        usage.put("llm_model", config.getLlmModel());
        usage.put("agent_mode", config.getAgentMode());
        // 按模型统计
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
