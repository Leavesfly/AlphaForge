package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.domain.model.entity.BacktestRecord;
import io.leavesfly.stock.application.service.BacktestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

/**
 * 回测API控制器 (对齐 dsa-web)
 */
@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /** 运行回测(增强版: 支持信号回溯) */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runBacktest(@RequestBody(required = false) Map<String, Object> request) {
        if (request == null) request = Map.of();
        String stockCode = (String) request.getOrDefault("code", "");
        int evalWindowDays = ((Number) request.getOrDefault("eval_window_days", 7)).intValue();
        int limit = ((Number) request.getOrDefault("limit", 50)).intValue();

        Map<String, Object> result = backtestService.runSignalBacktest(stockCode, evalWindowDays, limit);
        return ResponseEntity.ok(result);
    }

    /** 获取分页回测结果 */
    @GetMapping("/results")
    public ResponseEntity<Map<String, Object>> getResults(
            @RequestParam(required = false) String code,
            @RequestParam(required = false, name = "eval_window_days") Integer evalWindowDays,
            @RequestParam(required = false, name = "analysis_date_from") String analysisDateFrom,
            @RequestParam(required = false, name = "analysis_date_to") String analysisDateTo,
            @RequestParam(required = false, name = "analysis_phase") String analysisPhase,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        List<Map<String, Object>> items = backtestService.getBacktestResults(code, page, limit);
        return ResponseEntity.ok(Map.of("items", items, "total", items.size(), "page", page, "limit", limit));
    }

    /** 获取整体绩效 */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getOverallPerformance(
            @RequestParam(required = false, name = "eval_window_days") Integer evalWindowDays,
            @RequestParam(required = false, name = "analysis_phase") String analysisPhase) {
        Map<String, Object> perf = backtestService.getOverallPerformance();
        return ResponseEntity.ok(perf);
    }

    /** 获取个股绩效 */
    @GetMapping("/performance/{code}")
    public ResponseEntity<Map<String, Object>> getStockPerformance(@PathVariable String code) {
        Map<String, Object> perf = backtestService.getStockPerformance(code);
        return ResponseEntity.ok(perf);
    }

    // ========== 旧端点兼容 ==========
    @GetMapping("/history")
    public ResponseEntity<List<BacktestRecord>> getHistory(@RequestParam(required = false) String stockCode) {
        return ResponseEntity.ok(backtestService.getHistory(stockCode));
    }

    @GetMapping("/strategies")
    public ResponseEntity<List<Map<String, String>>> getStrategies() {
        return ResponseEntity.ok(List.of(
            Map.of("name", "ma_golden_cross", "label", "均线金叉", "description", "MA5上穿MA20买入"),
            Map.of("name", "volume_breakout", "label", "放量突破", "description", "成交量突破2倍均量"),
            Map.of("name", "bull_trend", "label", "牛趋势", "description", "价格在MA10和MA30之上"),
            Map.of("name", "shrink_pullback", "label", "缩量回调", "description", "趋势回调缩量后反弹"),
            Map.of("name", "box_oscillation", "label", "箱体震荡", "description", "箱体下沿买入上沿卖出")
        ));
    }

}
