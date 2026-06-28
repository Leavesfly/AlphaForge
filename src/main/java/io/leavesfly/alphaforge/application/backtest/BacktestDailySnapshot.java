package io.leavesfly.alphaforge.application.backtest;

import java.time.LocalDate;

/** 回测每日净值快照，用于可视化。 */
public class BacktestDailySnapshot {

    private LocalDate date;
    private double portfolioValue;
    private double benchmarkValue;
    private double drawdownPct;
    private double closePrice;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public double getPortfolioValue() { return portfolioValue; }
    public void setPortfolioValue(double portfolioValue) { this.portfolioValue = portfolioValue; }
    public double getBenchmarkValue() { return benchmarkValue; }
    public void setBenchmarkValue(double benchmarkValue) { this.benchmarkValue = benchmarkValue; }
    public double getDrawdownPct() { return drawdownPct; }
    public void setDrawdownPct(double drawdownPct) { this.drawdownPct = drawdownPct; }
    public double getClosePrice() { return closePrice; }
    public void setClosePrice(double closePrice) { this.closePrice = closePrice; }
}
