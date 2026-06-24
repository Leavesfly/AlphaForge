package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.application.service.IntelligenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 智能情报API控制器
 * 对应Python版本的 api/v1/endpoints/intelligence.py
 */
@RestController
@RequestMapping("/api/v1/intelligence")
public class IntelligenceController {

    private final IntelligenceService intelligenceService;

    public IntelligenceController(IntelligenceService intelligenceService) {
        this.intelligenceService = intelligenceService;
    }

    @GetMapping("/{stockCode}")
    public ResponseEntity<Map<String, Object>> getIntelligence(
            @PathVariable String stockCode, @RequestParam(required = false) String name) {
        return ResponseEntity.ok(intelligenceService.getIntelligence(stockCode, name != null ? name : stockCode));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Map<String, Object>>> batchIntelligence(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) request.getOrDefault("stock_codes", List.of());
        return ResponseEntity.ok(intelligenceService.batchIntelligence(codes));
    }
}
