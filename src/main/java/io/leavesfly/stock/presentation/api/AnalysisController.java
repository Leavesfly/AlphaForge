package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.stock.application.service.*;
import io.leavesfly.stock.domain.model.entity.AnalysisReport;
import io.leavesfly.stock.domain.model.entity.DecisionSignal;
import io.leavesfly.stock.domain.model.entity.WatchlistItem;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.infrastructure.persistence.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 分析相关 API
 * 分析触发、历史查询、实时行情、决策信号、智能选股、回测、市场概况、自选股
 */
@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final StockAnalysisPipeline pipeline;
    private final AnalysisHistoryService historyService;
    private final DecisionSignalService signalService;
    private final DataFetcherManager dataFetcher;
    private final AlphaSiftScreeningEngine screeningEngine;
    private final BacktestService backtestService;
    private final MarketAnalysisService marketService;
    private final MarketLightService marketLightService;
    private final WatchlistRepository watchlistRepo;

    public AnalysisController(StockAnalysisPipeline pipeline, AnalysisHistoryService historyService,
                             DecisionSignalService signalService, DataFetcherManager dataFetcher,
                             AlphaSiftScreeningEngine screeningEngine, BacktestService backtestService,
                             MarketAnalysisService marketService, MarketLightService marketLightService,
                             WatchlistRepository watchlistRepo) {
        this.pipeline = pipeline;
        this.historyService = historyService;
        this.signalService = signalService;
        this.dataFetcher = dataFetcher;
        this.screeningEngine = screeningEngine;
        this.backtestService = backtestService;
        this.marketService = marketService;
        this.marketLightService = marketLightService;
        this.watchlistRepo = watchlistRepo;
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

        // 异步执行分析
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

    // ==================== 决策信号 ====================

    /** 决策信号列表 */
    @GetMapping("/decision-signals")
    public ResponseEntity<Map<String, Object>> signals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(signalService.listSignals(
                null, stockCode, action, null, null, status, null, null, page, pageSize));
    }

    /** 信号详情 */
    @GetMapping("/decision-signals/{id}")
    public ResponseEntity<?> signalDetail(@PathVariable Long id) {
        return signalService.getSignalById(id)
                .map(s -> ResponseEntity.ok((Object) s))
                .orElse(ResponseEntity.notFound().build());
    }

    /** 信号反馈 */
    @PostMapping("/decision-signals/{id}/feedback")
    public ResponseEntity<Map<String, Object>> signalFeedback(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String feedback = (String) body.get("feedback");
        // 标记为已验证状态
        signalService.updateSignalStatus(id, "verified");
        return ResponseEntity.ok(Map.of("status", "ok", "feedback", feedback != null ? feedback : "none"));
    }

    // ==================== 智能选股 ====================

    /** 执行智能选股 */
    @PostMapping("/screening/run")
    public ResponseEntity<Map<String, Object>> runScreening(@RequestBody Map<String, Object> body) {
        String strategy = (String) body.getOrDefault("strategy", "value_growth");
        String market = (String) body.getOrDefault("market", "A");
        int maxResults = body.containsKey("max_results") ? ((Number) body.get("max_results")).intValue() : 10;

        List<Map<String, Object>> results = screeningEngine.screen(strategy, market, maxResults);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "strategy", strategy,
                "market", market,
                "total_scanned", 20,
                "results", results));
    }

    // ==================== 回测 ====================

    /** 运行回测 */
    @PostMapping("/backtest/run")
    public ResponseEntity<?> runBacktest(@RequestBody Map<String, Object> body) {
        String stockCode = (String) body.get("stock_code");
        if (stockCode == null || stockCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock_code is required"));
        }
        String strategy = (String) body.getOrDefault("strategy", "ma_golden_cross");
        String startStr = (String) body.getOrDefault("start_date", LocalDate.now().minusMonths(6).toString());
        String endStr = (String) body.getOrDefault("end_date", LocalDate.now().toString());
        double capital = body.containsKey("initial_capital") ? ((Number) body.get("initial_capital")).doubleValue() : 100000;

        var record = backtestService.runBacktest(stockCode, strategy,
                LocalDate.parse(startStr), LocalDate.parse(endStr), capital);
        if (record == null) {
            return ResponseEntity.ok(Map.of("status", "failed", "message", "历史数据不足"));
        }
        return ResponseEntity.ok(record);
    }

    /** 回测历史 */
    @GetMapping("/backtest/history")
    public ResponseEntity<?> backtestHistory(@RequestParam(required = false) String stockCode) {
        return ResponseEntity.ok(backtestService.getHistory(stockCode));
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

    // ==================== 自选股 ====================

    /** 获取自选股列表 */
    @GetMapping("/watchlist")
    public ResponseEntity<List<WatchlistItem>> watchlist() {
        return ResponseEntity.ok(watchlistRepo.findAll());
    }

    /** 添加自选股 */
    @PostMapping("/watchlist")
    public ResponseEntity<Map<String, Object>> addWatchlist(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("stock_code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock_code is required"));
        }
        WatchlistItem item = new WatchlistItem();
        item.setStockCode(code);
        item.setStockName((String) body.getOrDefault("stock_name", code));
        item.setMarket((String) body.getOrDefault("market", "A"));
        item.setAddedAt(LocalDateTime.now());
        watchlistRepo.insert(item);
        return ResponseEntity.ok(Map.of("status", "ok", "stock_code", code));
    }

    /** 删除自选股 */
    @DeleteMapping("/watchlist/{code}")
    public ResponseEntity<Map<String, Object>> removeWatchlist(@PathVariable String code) {
        watchlistRepo.deleteByStockCode(code);
        return ResponseEntity.ok(Map.of("status", "ok", "removed", code));
    }
}
