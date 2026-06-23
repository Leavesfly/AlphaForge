package io.leavesfly.stock.api.controller;

import io.leavesfly.stock.core.StockAnalysisPipeline;
import io.leavesfly.stock.model.entity.AnalysisReport;
import io.leavesfly.stock.service.AnalysisHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 分析API控制器
 * 
 * 对应Python版本的 api/v1/endpoints/analysis.py
 * 提供股票分析的触发、查询等REST接口
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    private final StockAnalysisPipeline pipeline;
    private final AnalysisHistoryService historyService;

    public AnalysisController(StockAnalysisPipeline pipeline, AnalysisHistoryService historyService) {
        this.pipeline = pipeline;
        this.historyService = historyService;
    }

    /**
     * 触发股票分析
     * POST /api/v1/analysis/run
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runAnalysis(@RequestBody Map<String, Object> request) {
        String stocks = (String) request.getOrDefault("stocks", "");
        boolean dryRun = (boolean) request.getOrDefault("dry_run", false);
        
        if (stocks.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供stocks参数"));
        }

        // 异步执行分析
        CompletableFuture.runAsync(() -> {
            pipeline.runFullAnalysis(stocks, dryRun, false);
        });

        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "message", "分析任务已提交",
                "stocks", stocks
        ));
    }

    /**
     * 分析单只股票(同步)
     * POST /api/v1/analysis/single
     */
    @PostMapping("/single")
    public ResponseEntity<?> analyzeSingle(@RequestBody Map<String, Object> request) {
        String stockCode = (String) request.getOrDefault("stock_code", "");
        if (stockCode.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供stock_code参数"));
        }

        try {
            AnalysisReport report = pipeline.analyzeSingleStock(stockCode, false, false);
            if (report != null) {
                return ResponseEntity.ok(report);
            } else {
                return ResponseEntity.internalServerError().body(Map.of("error", "分析失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取分析历史
     * GET /api/v1/analysis/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<AnalysisReport>> getHistory(
            @RequestParam(required = false) String stockCode,
            @RequestParam(defaultValue = "20") int limit) {
        List<AnalysisReport> reports = historyService.getRecentReports(stockCode, limit);
        return ResponseEntity.ok(reports);
    }

    /**
     * 获取报告详情
     * GET /api/v1/analysis/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getReport(@PathVariable Long id) {
        return historyService.getReportById(id)
                .map(report -> ResponseEntity.ok((Object) report))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除报告
     * DELETE /api/v1/analysis/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteReport(@PathVariable Long id) {
        historyService.deleteReport(id);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }
}
