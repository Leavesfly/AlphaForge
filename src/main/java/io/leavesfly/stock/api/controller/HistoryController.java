package io.leavesfly.stock.api.controller;

import io.leavesfly.stock.model.entity.AnalysisReport;
import io.leavesfly.stock.service.AnalysisHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 历史记录API控制器
 * 对应Python版本的 api/v1/endpoints/history.py
 */
@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final AnalysisHistoryService historyService;

    public HistoryController(AnalysisHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<List<AnalysisReport>> getHistory(
            @RequestParam(required = false) String stockCode,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(historyService.getRecentReports(stockCode, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetail(@PathVariable Long id) {
        return historyService.getReportById(id)
                .map(r -> ResponseEntity.ok((Object) r))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_analyses", historyService.getTotalAnalysisCount());
        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        historyService.deleteReport(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
