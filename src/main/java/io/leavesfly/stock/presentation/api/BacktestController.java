package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.service.BacktestService;
import io.leavesfly.stock.application.service.BacktestVisualizationService;
import io.leavesfly.stock.presentation.api.dto.BacktestRequest;
import jakarta.validation.Valid;
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
    private final BacktestVisualizationService visualizationService;

    public BacktestController(BacktestService backtestService,
                              BacktestVisualizationService visualizationService) {
        this.backtestService = backtestService;
        this.visualizationService = visualizationService;
    }

    /** 运行回测 */
    @PostMapping("/run")
    public ResponseEntity<?> run(@Valid @RequestBody BacktestRequest request) {
        String startStr = request.getStartDate() != null ? request.getStartDate()
                : LocalDate.now().minusMonths(6).toString();
        String endStr = request.getEndDate() != null ? request.getEndDate() : LocalDate.now().toString();

        var record = backtestService.runBacktest(request.getStockCode(), request.getStrategy(),
                LocalDate.parse(startStr), LocalDate.parse(endStr), request.getInitialCapital());
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

    /** 回测可视化数据（净值曲线、回撤、买卖点、月度收益） */
    @GetMapping("/{id}/visualization")
    public ResponseEntity<?> visualization(@PathVariable long id) {
        return visualizationService.buildVisualization(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
