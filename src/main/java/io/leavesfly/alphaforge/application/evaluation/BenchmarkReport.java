package io.leavesfly.alphaforge.application.evaluation;

import io.leavesfly.alphaforge.application.backtest.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.strategy.model.WalkForwardResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基准评估报告 — 单个策略的完整评估结果
 */
public class BenchmarkReport {

    private final String strategyId;
    private final String stockCode;
    private final int backtestDays;
    private final BacktestSimulationResult backtestResult;
    private final WalkForwardResult walkForwardResult;
    private final StrategyQualityScore qualityScore;
    private final long durationMs;
    private final String error;

    public BenchmarkReport(String strategyId, String stockCode, int backtestDays,
                           BacktestSimulationResult backtestResult,
                           WalkForwardResult walkForwardResult,
                           StrategyQualityScore qualityScore,
                           long durationMs) {
        this.strategyId = strategyId;
        this.stockCode = stockCode;
        this.backtestDays = backtestDays;
        this.backtestResult = backtestResult;
        this.walkForwardResult = walkForwardResult;
        this.qualityScore = qualityScore;
        this.durationMs = durationMs;
        this.error = null;
    }

    private BenchmarkReport(String strategyId, String stockCode, String error) {
        this.strategyId = strategyId;
        this.stockCode = stockCode;
        this.backtestDays = 0;
        this.backtestResult = null;
        this.walkForwardResult = null;
        this.qualityScore = null;
        this.durationMs = 0;
        this.error = error;
    }

    public static BenchmarkReport error(String strategyId, String stockCode, String error) {
        return new BenchmarkReport(strategyId, stockCode, error);
    }

    public boolean isSuccess() { return error == null; }
    public String getStrategyId() { return strategyId; }
    public String getStockCode() { return stockCode; }
    public int getBacktestDays() { return backtestDays; }
    public BacktestSimulationResult getBacktestResult() { return backtestResult; }
    public WalkForwardResult getWalkForwardResult() { return walkForwardResult; }
    public StrategyQualityScore getQualityScore() { return qualityScore; }
    public long getDurationMs() { return durationMs; }
    public String getError() { return error; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("strategy_id", strategyId);
        map.put("stock_code", stockCode);
        map.put("backtest_days", backtestDays);
        map.put("success", isSuccess());
        if (error != null) {
            map.put("error", error);
            return map;
        }
        if (backtestResult != null) {
            map.put("annual_return_pct", String.format("%.1f", backtestResult.getAnnualReturnPct()));
            map.put("total_return_pct", String.format("%.1f", backtestResult.getTotalReturnPct()));
            map.put("sharpe_ratio", String.format("%.2f", backtestResult.getSharpeRatio()));
            map.put("max_drawdown_pct", String.format("%.1f", backtestResult.getMaxDrawdownPct()));
            map.put("win_rate_pct", String.format("%.1f", backtestResult.getWinRatePct()));
            map.put("total_trades", backtestResult.getTotalTrades());
        }
        if (qualityScore != null) {
            map.put("quality", qualityScore.toMap());
        }
        map.put("duration_ms", durationMs);
        return map;
    }
}
