package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.stock.application.service.*;
import io.leavesfly.stock.domain.model.entity.AnalysisReport;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 分析 API
 * 分析触发、历史查询、实时行情、市场概况
 *
 * 已拆分到独立Controller：
 * - SignalController: 决策信号
 * - WatchlistController: 自选股
 * - BacktestController: 策略回测
 * - ScreeningController: 智能选股
 */
@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final StockAnalysisPipeline pipeline;
    private final AnalysisHistoryService historyService;
    private final DataFetcherManager dataFetcher;
    private final MarketAnalysisService marketService;
    private final MarketLightService marketLightService;

    public AnalysisController(StockAnalysisPipeline pipeline, AnalysisHistoryService historyService,
                             DataFetcherManager dataFetcher,
                             MarketAnalysisService marketService, MarketLightService marketLightService) {
        this.pipeline = pipeline;
        this.historyService = historyService;
        this.dataFetcher = dataFetcher;
        this.marketService = marketService;
        this.marketLightService = marketLightService;
    }

    // ==================== 分析 ====================

    /** 触发股票分析（异步） */
    @PostMapping("/analysis/run")
    public ResponseEntity<?> runAnalysis(@RequestBody Map<String, Object> body) {
        String stockCode = (String) body.get("stock_code");
        if (stockCode == null || stockCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock_code is required"));
        }
        boolean dryRun = Boolean.TRUE.equals(body.get("dry_run"));

        CompletableFuture.runAsync(() -> {
            try {
                pipeline.analyzeSingleStock(stockCode.trim(), dryRun, false);
            } catch (Exception e) {
                log.error("分析任务异常: {} - {}", stockCode, e.getMessage());
            }
        });

        return ResponseEntity.ok(Map.of(
                "status", "submitted",
                "stock_code", stockCode,
                "message", "分析任务已提交"));
    }

    // ==================== 分析历史 ====================

    /** 分析历史列表 */
    @GetMapping("/history")
    public ResponseEntity<List<AnalysisReport>> history(
            @RequestParam(required = false) String stockCode,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(historyService.getRecentReports(stockCode, limit));
    }

    /** 报告详情 */
    @GetMapping("/history/{id}")
    public ResponseEntity<?> historyDetail(@PathVariable Long id) {
        return historyService.getReportById(id)
                .map(r -> ResponseEntity.ok((Object) r))
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== 实时行情 ====================

    /** 获取股票实时行情 */
    @GetMapping("/stocks/{code}/quote")
    public ResponseEntity<Map<String, Object>> quote(@PathVariable String code) {
        Map<String, Object> quote = dataFetcher.getRealtimeQuote(code);
        if (quote == null || quote.isEmpty()) {
            quote = Map.of("stock_code", code, "status", "no_data");
        }
        return ResponseEntity.ok(quote);
    }

    // ==================== 市场 ====================

    /** 市场概况 */
    @GetMapping("/market/overview")
    public ResponseEntity<Map<String, Object>> marketOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", marketService.getMarketOverview());
        result.put("light", marketLightService.getMarketLight());
        return ResponseEntity.ok(result);
    }
}
