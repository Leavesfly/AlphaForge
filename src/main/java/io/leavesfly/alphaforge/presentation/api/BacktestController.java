package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.backtest.BacktestSimulationConfig;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulator;
import io.leavesfly.alphaforge.application.service.screening.BacktestService;
import io.leavesfly.alphaforge.application.service.screening.BacktestVisualizationService;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.engine.MonteCarloSimulator;
import io.leavesfly.alphaforge.application.strategy.engine.ParameterOptimizer;
import io.leavesfly.alphaforge.application.strategy.engine.PortfolioBacktestService;
import io.leavesfly.alphaforge.application.strategy.engine.WalkForwardValidator;
import io.leavesfly.alphaforge.application.strategy.model.OptimizationResult;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.application.strategy.model.WalkForwardResult;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.presentation.api.dto.BacktestRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 策略回测 API — 含参数优化、Walk-Forward、蒙特卡洛、组合回测。
 */
@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {

    private final BacktestService backtestService;
    private final BacktestVisualizationService visualizationService;
    private final ParameterOptimizer optimizer;
    private final WalkForwardValidator walkForwardValidator;
    private final MonteCarloSimulator monteCarloSimulator;
    private final PortfolioBacktestService portfolioBacktestService;
    private final StrategyCatalog catalog;
    private final MarketDataPort dataFetcher;
    private final BacktestSimulator simulator;

    public BacktestController(BacktestService backtestService,
                              BacktestVisualizationService visualizationService,
                              ParameterOptimizer optimizer,
                              WalkForwardValidator walkForwardValidator,
                              MonteCarloSimulator monteCarloSimulator,
                              PortfolioBacktestService portfolioBacktestService,
                              StrategyCatalog catalog,
                              MarketDataPort dataFetcher,
                              BacktestSimulator simulator) {
        this.backtestService = backtestService;
        this.visualizationService = visualizationService;
        this.optimizer = optimizer;
        this.walkForwardValidator = walkForwardValidator;
        this.monteCarloSimulator = monteCarloSimulator;
        this.portfolioBacktestService = portfolioBacktestService;
        this.catalog = catalog;
        this.dataFetcher = dataFetcher;
        this.simulator = simulator;
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

    /** 参数网格搜索优化 */
    @PostMapping("/optimize")
    public ResponseEntity<?> optimize(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("stock_code");
        String strategyId = (String) body.getOrDefault("strategy", "ma_golden_cross");
        int days = body.containsKey("days") ? ((Number) body.get("days")).intValue() : 365;

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock_code 不能为空"));
        }

        StrategyDefinition strategy = catalog.find(strategyId).orElse(null);
        if (strategy == null || strategy.getBacktest() == null || !strategy.getBacktest().hasParamSpace()) {
            return ResponseEntity.ok(Map.of("error", "策略不存在或未声明 param_space"));
        }

        LocalDate end = LocalDate.now();
        List<StockDailyData> data = dataFetcher.getHistoryData(code, end.minusDays(days), end);
        if (data == null || data.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "无法获取历史数据: " + code));
        }

        OptimizationResult result = optimizer.optimize(strategy, data, 100000);
        return ResponseEntity.ok(result.toMap());
    }

    /** Walk-Forward 滚动窗口验证 */
    @PostMapping("/walk-forward")
    public ResponseEntity<?> walkForward(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("stock_code");
        String strategyId = (String) body.getOrDefault("strategy", "ma_golden_cross");
        int days = body.containsKey("days") ? ((Number) body.get("days")).intValue() : 365;
        int windowSize = body.containsKey("window_size") ? ((Number) body.get("window_size")).intValue() : 0;

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock_code 不能为空"));
        }

        StrategyDefinition strategy = catalog.find(strategyId).orElse(null);
        if (strategy == null || strategy.getBacktest() == null || !strategy.getBacktest().hasParamSpace()) {
            return ResponseEntity.ok(Map.of("error", "策略不存在或未声明 param_space"));
        }

        LocalDate end = LocalDate.now();
        List<StockDailyData> data = dataFetcher.getHistoryData(code, end.minusDays(days), end);
        if (data == null || data.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "无法获取历史数据: " + code));
        }

        WalkForwardResult result = walkForwardValidator.validate(strategy, data, windowSize, 100000);
        return ResponseEntity.ok(result.toMap());
    }

    /** 蒙特卡洛模拟 — 对最近一次回测结果进行稳健性分析 */
    @PostMapping("/monte-carlo")
    public ResponseEntity<?> monteCarlo(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("stock_code");
        String strategyId = (String) body.getOrDefault("strategy", "ma_golden_cross");
        int days = body.containsKey("days") ? ((Number) body.get("days")).intValue() : 180;
        int iterations = body.containsKey("iterations") ? ((Number) body.get("iterations")).intValue() : 1000;

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock_code 不能为空"));
        }

        StrategyDefinition strategy = catalog.find(strategyId).orElse(null);
        if (strategy == null || strategy.getBacktest() == null) {
            return ResponseEntity.ok(Map.of("error", "策略不存在或不支持回测"));
        }

        LocalDate end = LocalDate.now();
        List<StockDailyData> data = dataFetcher.getHistoryData(code, end.minusDays(days), end);
        if (data == null || data.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "无法获取历史数据: " + code));
        }

        BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode(code);
        BacktestSimulationResult simResult = simulator.simulate(data, strategy, 100000, config);
        Map<String, Object> mcResult = monteCarloSimulator.simulate(simResult, iterations);
        return ResponseEntity.ok(mcResult);
    }

    /** 多策略组合回测 */
    @PostMapping("/portfolio")
    public ResponseEntity<?> portfolio(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("stock_code");
        Object strategiesObj = body.get("strategies");
        int days = body.containsKey("days") ? ((Number) body.get("days")).intValue() : 180;
        double capital = body.containsKey("capital") ? ((Number) body.get("capital")).doubleValue() : 100000;

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock_code 不能为空"));
        }
        if (strategiesObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "strategies 不能为空"));
        }

        List<String> strategyIds;
        if (strategiesObj instanceof String s) {
            strategyIds = Arrays.stream(s.split(",")).map(String::trim).filter(str -> !str.isEmpty()).toList();
        } else if (strategiesObj instanceof List<?> list) {
            strategyIds = list.stream().map(String::valueOf).toList();
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "strategies 格式错误"));
        }

        if (strategyIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "strategies 不能为空"));
        }

        LocalDate end = LocalDate.now();
        Map<String, Object> result = portfolioBacktestService.runPortfolioBacktest(
                code, strategyIds, end.minusDays(days), end, capital);
        return ResponseEntity.ok(result);
    }
}
