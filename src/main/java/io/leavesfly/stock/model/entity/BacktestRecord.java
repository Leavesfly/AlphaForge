package io.leavesfly.stock.model.entity;

import java.time.LocalDateTime;

/**
 * 回测记录实体
 * 对应Python版本的 backtest_repo.py
 */
public class BacktestRecord {

    private Long id;

    /** 股票代码 */
    private String stockCode;

    /** 股票名称 */
    private String stockName;

    /** 策略名称 */
    private String strategyName;

    /** 回测开始日期 */
    private LocalDateTime startDate;

    /** 回测结束日期 */
    private LocalDateTime endDate;

    /** 初始资金 */
    private Double initialCapital;

    /** 最终资金 */
    private Double finalCapital;

    /** 总收益率(%) */
    private Double totalReturnPct;

    /** 年化收益率(%) */
    private Double annualReturnPct;

    /** 最大回撤(%) */
    private Double maxDrawdownPct;

    /** 夏普比率 */
    private Double sharpeRatio;

    /** 胜率(%) */
    private Double winRatePct;

    /** 总交易次数 */
    private Integer totalTrades;

    /** 盈利次数 */
    private Integer winningTrades;

    /** 亏损次数 */
    private Integer losingTrades;

    /** 平均持仓天数 */
    private Double avgHoldingDays;

    /** 盈亏比 */
    private Double profitLossRatio;

    /** 基准收益率(buy&hold) */
    private Double benchmarkReturnPct;

    /** 超额收益(%) */
    private Double alphaPct;

    /** 交易明细JSON */
    private String tradeDetails;

    /** 回测参数JSON */
    private String parameters;

    private LocalDateTime createdAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public Double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(Double initialCapital) { this.initialCapital = initialCapital; }
    public Double getFinalCapital() { return finalCapital; }
    public void setFinalCapital(Double finalCapital) { this.finalCapital = finalCapital; }
    public Double getTotalReturnPct() { return totalReturnPct; }
    public void setTotalReturnPct(Double totalReturnPct) { this.totalReturnPct = totalReturnPct; }
    public Double getAnnualReturnPct() { return annualReturnPct; }
    public void setAnnualReturnPct(Double annualReturnPct) { this.annualReturnPct = annualReturnPct; }
    public Double getMaxDrawdownPct() { return maxDrawdownPct; }
    public void setMaxDrawdownPct(Double maxDrawdownPct) { this.maxDrawdownPct = maxDrawdownPct; }
    public Double getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(Double sharpeRatio) { this.sharpeRatio = sharpeRatio; }
    public Double getWinRatePct() { return winRatePct; }
    public void setWinRatePct(Double winRatePct) { this.winRatePct = winRatePct; }
    public Integer getTotalTrades() { return totalTrades; }
    public void setTotalTrades(Integer totalTrades) { this.totalTrades = totalTrades; }
    public Integer getWinningTrades() { return winningTrades; }
    public void setWinningTrades(Integer winningTrades) { this.winningTrades = winningTrades; }
    public Integer getLosingTrades() { return losingTrades; }
    public void setLosingTrades(Integer losingTrades) { this.losingTrades = losingTrades; }
    public Double getAvgHoldingDays() { return avgHoldingDays; }
    public void setAvgHoldingDays(Double avgHoldingDays) { this.avgHoldingDays = avgHoldingDays; }
    public Double getProfitLossRatio() { return profitLossRatio; }
    public void setProfitLossRatio(Double profitLossRatio) { this.profitLossRatio = profitLossRatio; }
    public Double getBenchmarkReturnPct() { return benchmarkReturnPct; }
    public void setBenchmarkReturnPct(Double benchmarkReturnPct) { this.benchmarkReturnPct = benchmarkReturnPct; }
    public Double getAlphaPct() { return alphaPct; }
    public void setAlphaPct(Double alphaPct) { this.alphaPct = alphaPct; }
    public String getTradeDetails() { return tradeDetails; }
    public void setTradeDetails(String tradeDetails) { this.tradeDetails = tradeDetails; }
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
