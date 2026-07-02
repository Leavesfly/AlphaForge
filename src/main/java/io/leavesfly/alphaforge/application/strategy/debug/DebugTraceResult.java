package io.leavesfly.alphaforge.application.strategy.debug;

import java.util.List;

/**
 * 策略调试结果：包含逐 K 线的条件命中详情
 */
public class DebugTraceResult {

    private String strategyId;
    private String stockCode;
    private int totalDays;
    private int buySignals;
    private int sellSignals;
    private int warmupDays;
    private List<DebugStep> steps;

    public String getStrategyId() { return strategyId; }
    public void setStrategyId(String strategyId) { this.strategyId = strategyId; }

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }

    public int getTotalDays() { return totalDays; }
    public void setTotalDays(int totalDays) { this.totalDays = totalDays; }

    public int getBuySignals() { return buySignals; }
    public void setBuySignals(int buySignals) { this.buySignals = buySignals; }

    public int getSellSignals() { return sellSignals; }
    public void setSellSignals(int sellSignals) { this.sellSignals = sellSignals; }

    public int getWarmupDays() { return warmupDays; }
    public void setWarmupDays(int warmupDays) { this.warmupDays = warmupDays; }

    public List<DebugStep> getSteps() { return steps; }
    public void setSteps(List<DebugStep> steps) { this.steps = steps; }
}
