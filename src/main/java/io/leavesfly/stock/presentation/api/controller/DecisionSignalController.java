package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.domain.model.entity.DecisionSignal;
import io.leavesfly.stock.application.service.DecisionSignalService;
import io.leavesfly.stock.application.service.DecisionSignalOutcomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 决策信号API控制器 (对齐 dsa-web)
 */
@RestController
@RequestMapping("/api/v1/decision-signals")
public class DecisionSignalController {

    private final DecisionSignalService signalService;
    private final DecisionSignalOutcomeService outcomeService;

    public DecisionSignalController(DecisionSignalService signalService, DecisionSignalOutcomeService outcomeService) {
        this.signalService = signalService;
        this.outcomeService = outcomeService;
    }

    /** 信号列表(支持多条件筛选+分页) */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listSignals(
            @RequestParam(required = false) String market,
            @RequestParam(required = false, name = "stock_code") String stockCode,
            @RequestParam(required = false) String action,
            @RequestParam(required = false, name = "market_phase") String marketPhase,
            @RequestParam(required = false, name = "source_type") String sourceType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20", name = "page_size") int pageSize) {
        List<DecisionSignal> all = signalService.getRecentSignals();
        // 简单过滤
        if (stockCode != null && !stockCode.isEmpty()) all.removeIf(s -> !stockCode.equals(s.getStockCode()));
        if (status != null && !status.isEmpty()) all.removeIf(s -> !status.equals(s.getStatus()));
        int total = all.size();
        int start = (page - 1) * pageSize;
        List<DecisionSignal> items = start < total ? all.subList(start, Math.min(start + pageSize, total)) : List.of();
        return ResponseEntity.ok(Map.of("items", items, "total", total, "page", page));
    }

    /** 获取信号详情 */
    @GetMapping("/{id}")
    public ResponseEntity<?> getSignal(@PathVariable Long id) {
        return signalService.getSignalById(id)
                .map(s -> ResponseEntity.ok((Object) s))
                .orElse(ResponseEntity.notFound().build());
    }

    /** 创建信号 */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createSignal(@RequestBody DecisionSignal signal) {
        DecisionSignal created = signalService.createSignal(signal);
        return ResponseEntity.ok(Map.of("status", "created", "item", created));
    }

    /** 获取最新信号 */
    @GetMapping("/latest/{stockCode}")
    public ResponseEntity<Map<String, Object>> getLatestSignals(@PathVariable String stockCode, @RequestParam(defaultValue = "5") int limit) {
        List<DecisionSignal> signals = signalService.getSignalsByStock(stockCode);
        if (signals.size() > limit) signals = signals.subList(0, limit);
        return ResponseEntity.ok(Map.of("items", signals, "total", signals.size()));
    }

    /** 更新信号状态 */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        String status = (String) request.get("status");
        if (status == null) return ResponseEntity.badRequest().body(Map.of("error", "status必填"));
        DecisionSignal updated = signalService.updateSignalStatus(id, status);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    /** 运行信号结果评估 */
    @PostMapping("/outcomes/run")
    public ResponseEntity<Map<String, Object>> runOutcomes(@RequestBody(required = false) Map<String, Object> request) {
        List<Map<String, Object>> results = outcomeService.runEvaluation(request);
        return ResponseEntity.ok(Map.of("items", results, "evaluated", results.size()));
    }

    /** 获取结果列表 */
    @GetMapping("/outcomes")
    public ResponseEntity<Map<String, Object>> listOutcomes(
            @RequestParam(required = false, name = "signal_id") Long signalId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20", name = "page_size") int pageSize) {
        List<Map<String, Object>> items = outcomeService.listOutcomes(signalId, page, pageSize);
        return ResponseEntity.ok(Map.of("items", items, "total", items.size(), "page", page));
    }

    /** 获取信号统计 */
    @GetMapping("/outcomes/stats")
    public ResponseEntity<Map<String, Object>> getOutcomeStats() {
        return ResponseEntity.ok(outcomeService.getStats());
    }

    /** 获取特定信号的结果 */
    @GetMapping("/{id}/outcomes")
    public ResponseEntity<Map<String, Object>> getSignalOutcomes(@PathVariable Long id) {
        List<Map<String, Object>> items = outcomeService.listOutcomes(id, 1, 50);
        return ResponseEntity.ok(Map.of("items", items, "total", items.size()));
    }

    /** 获取信号反馈 */
    @GetMapping("/{id}/feedback")
    public ResponseEntity<Map<String, Object>> getFeedback(@PathVariable Long id) {
        return ResponseEntity.ok(outcomeService.getFeedback(id));
    }

    /** 提交信号反馈 */
    @PutMapping("/{id}/feedback")
    public ResponseEntity<Map<String, Object>> putFeedback(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        Map<String, Object> saved = outcomeService.saveFeedback(id, request);
        return ResponseEntity.ok(saved);
    }

    // ========== 旧端点兼容 ==========
    @PostMapping("/{id}/execute")
    public ResponseEntity<?> executeSignal(@PathVariable Long id) {
        DecisionSignal s = signalService.executeSignal(id);
        return s != null ? ResponseEntity.ok(s) : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelSignal(@PathVariable Long id) {
        DecisionSignal s = signalService.cancelSignal(id);
        return s != null ? ResponseEntity.ok(s) : ResponseEntity.notFound().build();
    }
}
