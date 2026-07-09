package io.leavesfly.alphaforge.application.backtest.pairs;

import io.leavesfly.alphaforge.application.backtest.BacktestDailySnapshot;
import io.leavesfly.alphaforge.application.backtest.BacktestTrade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配对交易回测结果，字段对齐单标的 {@code BacktestSimulationResult}，
 * 额外在 {@link #getDiagnostics()} 中记录配对特有指标（beta、相关系数、半衰期、是否适合配对）。
 */
public class PairBacktestResult {

    private double finalCapital;
    private double totalReturnPct;
    private double annualReturnPct;
    private double maxDrawdownPct;
    private double sharpeRatio;
    private double winRatePct;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double avgHoldingDays;
    private double profitLossRatio;
    private final List<BacktestTrade> trades = new ArrayList<>();
    private final List<BacktestDailySnapshot> equityCurve = new ArrayList<>();
    private final Map<String, Object> diagnostics = new LinkedHashMap<>();

    public double getFinalCapital() { return finalCapital; }
    public void setFinalCapital(double finalCapital) { this.finalCapital = finalCapital; }
    public double getTotalReturnPct() { return totalReturnPct; }
    public void setTotalReturnPct(double totalReturnPct) { this.totalReturnPct = totalReturnPct; }
    public double getAnnualReturnPct() { return annualReturnPct; }
    public void setAnnualReturnPct(double annualReturnPct) { this.annualReturnPct = annualReturnPct; }
    public double getMaxDrawdownPct() { return maxDrawdownPct; }
    public void setMaxDrawdownPct(double maxDrawdownPct) { this.maxDrawdownPct = maxDrawdownPct; }
    public double getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(double sharpeRatio) { this.sharpeRatio = sharpeRatio; }
    public double getWinRatePct() { return winRatePct; }
    public void setWinRatePct(double winRatePct) { this.winRatePct = winRatePct; }
    public int getTotalTrades() { return totalTrades; }
    public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }
    public int getWinningTrades() { return winningTrades; }
    public void setWinningTrades(int winningTrades) { this.winningTrades = winningTrades; }
    public int getLosingTrades() { return losingTrades; }
    public void setLosingTrades(int losingTrades) { this.losingTrades = losingTrades; }
    public double getAvgHoldingDays() { return avgHoldingDays; }
    public void setAvgHoldingDays(double avgHoldingDays) { this.avgHoldingDays = avgHoldingDays; }
    public double getProfitLossRatio() { return profitLossRatio; }
    public void setProfitLossRatio(double profitLossRatio) { this.profitLossRatio = profitLossRatio; }
    public List<BacktestTrade> getTrades() { return trades; }
    public List<BacktestDailySnapshot> getEquityCurve() { return equityCurve; }
    public Map<String, Object> getDiagnostics() { return diagnostics; }
}
