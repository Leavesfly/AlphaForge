package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.service.BacktestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 策略回测 API
 */
@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /** 运行回测 */
    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody Map<String, Object> body) {
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
    @GetMapping("/history")
    public ResponseEntity<?> history(@RequestParam(required = false) String stockCode) {
        return ResponseEntity.ok(backtestService.getHistory(stockCode));
    }
}
