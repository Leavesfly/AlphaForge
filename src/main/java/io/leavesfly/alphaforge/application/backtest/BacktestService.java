package io.leavesfly.alphaforge.application.backtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulationConfig;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulator;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.engine.BacktestSignalEngine;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.backtest.BacktestRecord;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.repository.backtest.BacktestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 回测引擎服务 — 策略逻辑由 YAML 定义驱动，成交由 {@link BacktestSimulator} 仿真。
 */
@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);
    private final MarketDataPort dataFetcher;
    private final BacktestRepository backtestRepo;
    private final StrategyCatalog catalog;
    private final BacktestSimulator simulator;
    private final BacktestSignalEngine signalEngine;
    private final ObjectMapper objectMapper;

    public BacktestService(MarketDataPort dataFetcher,
                           BacktestRepository backtestRepo,
                           StrategyCatalog catalog,
                           BacktestSimulator simulator,
                           BacktestSignalEngine signalEngine,
                           ObjectMapper objectMapper) {
        this.dataFetcher = dataFetcher;
        this.backtestRepo = backtestRepo;
        this.catalog = catalog;
        this.simulator = simulator;
        this.signalEngine = signalEngine;
        this.objectMapper = objectMapper;
    }

    public BacktestRecord runBacktest(String stockCode, String strategyName,
                                      LocalDate startDate, LocalDate endDate, double initialCapital) {
        log.info("开始回测: {} - 策略: {} - 周期: {} ~ {}", stockCode, strategyName, startDate, endDate);

        Optional<StrategyDefinition> strategyOpt = catalog.find(strategyName);
        if (strategyOpt.isEmpty() || !strategyOpt.get().hasBacktest()) {
            log.error("策略不存在或不支持回测: {}", strategyName);
            return null;
        }
        StrategyDefinition strategy = strategyOpt.get();

        List<StockDailyData> historyData = dataFetcher.getHistoryData(stockCode, startDate, endDate);
        if (historyData.isEmpty()) {
            log.error("无历史数据: {}", stockCode);
            return null;
        }

        BacktestSimulationConfig config = resolveSimulationConfig(stockCode, strategy);
        int warmup = simulatorWarmup(strategy);
        if (historyData.size() <= warmup) {
            log.error("历史数据不足，无法回测: {} (需要 > {} 条)", stockCode, warmup);
            return null;
        }

        BacktestSimulationResult result = simulator.simulate(historyData, strategy, initialCapital, config);
        BacktestRecord record = toRecord(stockCode, strategyName, startDate, endDate, initialCapital, historyData, strategy, config, result);
        backtestRepo.save(record);
        log.info("回测完成: {} 总收益: {}% 最大回撤: {}% 交易成本: {}",
                stockCode,
                String.format("%.2f", result.getTotalReturnPct()),
                String.format("%.2f", result.getMaxDrawdownPct()),
                result.getDiagnostics().getOrDefault("total_commission", 0));
        return record;
    }

    private int simulatorWarmup(StrategyDefinition strategy) {
        return signalEngine.computeWarmupDays(strategy);
    }

    private BacktestSimulationConfig resolveSimulationConfig(String stockCode, StrategyDefinition strategy) {
        BacktestSimulationConfig base = BacktestSimulationConfig.forStockCode(stockCode);
        if (strategy.getBacktest() != null) {
            return BacktestSimulationConfig.merge(base, strategy.getBacktest().getSimulation());
        }
        return base;
    }

    private BacktestRecord toRecord(String stockCode,
                                    String strategyName,
                                    LocalDate startDate,
                                    LocalDate endDate,
                                    double initialCapital,
                                    List<StockDailyData> historyData,
                                    StrategyDefinition strategy,
                                    BacktestSimulationConfig config,
                                    BacktestSimulationResult result) {
        BacktestRecord record = new BacktestRecord();
        record.setStockCode(stockCode);
        record.setStockName(historyData.get(0).getStockName());
        record.setStrategyName(strategyName);
        record.setStartDate(startDate.atStartOfDay());
        record.setEndDate(endDate.atStartOfDay());
        record.setInitialCapital(initialCapital);
        record.setFinalCapital(result.getFinalCapital());
        record.setTotalReturnPct(result.getTotalReturnPct());
        record.setAnnualReturnPct(result.getAnnualReturnPct());
        record.setMaxDrawdownPct(result.getMaxDrawdownPct());
        record.setSharpeRatio(result.getSharpeRatio());
        record.setWinRatePct(result.getWinRatePct());
        record.setTotalTrades(result.getTotalTrades());
        record.setWinningTrades(result.getWinningTrades());
        record.setLosingTrades(result.getLosingTrades());
        record.setAvgHoldingDays(result.getAvgHoldingDays());
        record.setProfitLossRatio(result.getProfitLossRatio());
        record.setBenchmarkReturnPct(result.getBenchmarkReturnPct());
        record.setAlphaPct(result.getTotalReturnPct() - result.getBenchmarkReturnPct());
        record.setTradeDetails(toJson(result.getTrades()));
        record.setParameters(toJson(buildParameterSnapshot(strategy, config)));
        record.setDiagnostics(toJson(result.getDiagnostics()));
        return record;
    }

    private Map<String, Object> buildParameterSnapshot(StrategyDefinition strategy, BacktestSimulationConfig config) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("strategy_id", strategy.getId());
        if (strategy.getBacktest() != null) {
            snapshot.put("position_size", strategy.getBacktest().getPositionSize());
            snapshot.put("parameters", strategy.getBacktest().getParameters());
        }
        snapshot.put("simulation", Map.of(
                "execution_mode", config.getExecutionMode().name(),
                "commission_rate", config.getCommissionRate(),
                "stamp_tax_rate", config.getStampTaxRate(),
                "slippage_rate", config.getSlippageRate(),
                "t1_enabled", config.isT1Enabled(),
                "lot_size", config.getLotSize(),
                "limit_pct", config.getLimitPct()
        ));
        return snapshot;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("序列化回测 JSON 失败", e);
            return null;
        }
    }

    public List<BacktestRecord> getHistory(String stockCode) {
        if (stockCode != null && !stockCode.isEmpty()) {
            return backtestRepo.findByStockCodeOrderByCreatedAtDesc(stockCode);
        }
        return backtestRepo.findTop20ByOrderByCreatedAtDesc();
    }

    public Optional<BacktestRecord> getRecord(long id) {
        return backtestRepo.findByIdOpt(id);
    }

    public Map<String, Object> runSignalBacktest(String code, int evalWindowDays, int limit) {
        List<BacktestRecord> records = getHistory(code);
        int evaluated = Math.min(limit, records.size());
        return Map.of("status", "completed", "evaluated", evaluated,
                "stock_code", code != null ? code : "", "eval_window_days", evalWindowDays);
    }

    public List<Map<String, Object>> getBacktestResults(String code, int page, int limit) {
        List<BacktestRecord> records = getHistory(code);
        int offset = (page - 1) * limit;
        return records.stream().skip(offset).limit(limit).map(this::recordToMap).toList();
    }

    public Map<String, Object> getOverallPerformance() {
        List<BacktestRecord> records = backtestRepo.findTop20ByOrderByCreatedAtDesc();
        return aggregatePerformance(records);
    }

    public Map<String, Object> getStockPerformance(String code) {
        List<BacktestRecord> records = backtestRepo.findByStockCodeOrderByCreatedAtDesc(code);
        Map<String, Object> perf = aggregatePerformance(records);
        perf.put("stock_code", code);
        return perf;
    }

    private Map<String, Object> aggregatePerformance(List<BacktestRecord> records) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", records.size());
        if (records.isEmpty()) {
            stats.put("win_rate", null);
            stats.put("profit_loss_ratio", null);
            stats.put("avg_return_pct", null);
            return stats;
        }
        double avgReturn = records.stream()
                .mapToDouble(r -> r.getTotalReturnPct() != null ? r.getTotalReturnPct() : 0).average().orElse(0);
        double avgWinRate = records.stream()
                .mapToDouble(r -> r.getWinRatePct() != null ? r.getWinRatePct() : 0).average().orElse(0);
        double avgPl = records.stream()
                .mapToDouble(r -> r.getProfitLossRatio() != null ? r.getProfitLossRatio() : 0).average().orElse(0);
        stats.put("win_rate", avgWinRate);
        stats.put("profit_loss_ratio", avgPl);
        stats.put("avg_return_pct", avgReturn);
        return stats;
    }

    private Map<String, Object> recordToMap(BacktestRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("stock_code", r.getStockCode());
        m.put("strategy_name", r.getStrategyName());
        m.put("total_return_pct", r.getTotalReturnPct());
        m.put("win_rate_pct", r.getWinRatePct());
        m.put("profit_loss_ratio", r.getProfitLossRatio());
        m.put("created_at", r.getCreatedAt());
        return m;
    }
}
