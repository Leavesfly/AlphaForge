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
        usage.put("total_analyses", historyService.getTotalAnalysisCount());
        usage.put("llm_model", config.getLlmModel());
        usage.put("agent_mode", config.getAgentMode());
        // Token统计(简化版)
        usage.put("estimated_tokens_today", 0);
        usage.put("estimated_cost_today", "$0.00");
        return ResponseEntity.ok(usage);
    }
}
