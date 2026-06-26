package io.leavesfly.stock.application.backtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回测仿真结果。
 */
public class BacktestSimulationResult {

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
    private double benchmarkReturnPct;
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
    public double getBenchmarkReturnPct() { return benchmarkReturnPct; }
    public void setBenchmarkReturnPct(double benchmarkReturnPct) { this.benchmarkReturnPct = benchmarkReturnPct; }
    public List<BacktestTrade> getTrades() { return trades; }
    public List<BacktestDailySnapshot> getEquityCurve() { return equityCurve; }
    public Map<String, Object> getDiagnostics() { return diagnostics; }
}
