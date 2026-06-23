package io.leavesfly.stock.api.controller;

import io.leavesfly.stock.model.entity.BacktestRecord;
import io.leavesfly.stock.service.BacktestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

/**
 * 回测API控制器
 * 对应Python版本的 api/v1/endpoints/backtest.py
 */
@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runBacktest(@RequestBody Map<String, Object> request) {
        String stockCode = (String) request.getOrDefault("stock_code", "");
        String strategy = (String) request.getOrDefault("strategy", "ma_golden_cross");
        int days = (int) request.getOrDefault("days", 180);
        double capital = ((Number) request.getOrDefault("initial_capital", 100000)).doubleValue();

        if (stockCode.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "请提供stock_code"));

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        BacktestRecord result = backtestService.runBacktest(stockCode, strategy, startDate, endDate, capital);
        
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.internalServerError().body(Map.of("error", "回测失败，数据不足"));
    }

    @GetMapping("/history")
    public ResponseEntity<List<BacktestRecord>> getHistory(@RequestParam(required = false) String stockCode) {
        return ResponseEntity.ok(backtestService.getHistory(stockCode));
    }

    @GetMapping("/strategies")
    public ResponseEntity<List<Map<String, String>>> getStrategies() {
        List<Map<String, String>> strategies = List.of(
            Map.of("name", "ma_golden_cross", "label", "均线金叉", "description", "MA5上穿MA20买入，下穿卖出"),
            Map.of("name", "volume_breakout", "label", "放量突破", "description", "成交量突破2倍均量且涨幅>3%买入"),
            Map.of("name", "bull_trend", "label", "牛趋势", "description", "价格在MA10和MA30之上时持有"),
            Map.of("name", "shrink_pullback", "label", "缩量回调", "description", "趋势回调缩量后反弹买入"),
            Map.of("name", "box_oscillation", "label", "箱体震荡", "description", "箱体下沿买入上沿卖出")
        );
        return ResponseEntity.ok(strategies);
    }
}
