package com.stock.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 回测记录实体
 * 对应Python版本的 backtest_repo.py
 */
@Entity
@Table(name = "backtest_records")
public class BacktestRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 股票代码 */
    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    /** 股票名称 */
    @Column(name = "stock_name", length = 50)
    private String stockName;

    /** 策略名称 */
    @Column(name = "strategy_name", length = 50)
    private String strategyName;

    /** 回测开始日期 */
    @Column(name = "start_date")
    private LocalDateTime startDate;

    /** 回测结束日期 */
    @Column(name = "end_date")
    private LocalDateTime endDate;

    /** 初始资金 */
    @Column(name = "initial_capital")
    private Double initialCapital;

    /** 最终资金 */
    @Column(name = "final_capital")
    private Double finalCapital;

    /** 总收益率(%) */
    @Column(name = "total_return_pct")
    private Double totalReturnPct;

    /** 年化收益率(%) */
    @Column(name = "annual_return_pct")
    private Double annualReturnPct;

    /** 最大回撤(%) */
    @Column(name = "max_drawdown_pct")
    private Double maxDrawdownPct;

    /** 夏普比率 */
    @Column(name = "sharpe_ratio")
    private Double sharpeRatio;

    /** 胜率(%) */
    @Column(name = "win_rate_pct")
    private Double winRatePct;

    /** 总交易次数 */
    @Column(name = "total_trades")
    private Integer totalTrades;

    /** 盈利次数 */
    @Column(name = "winning_trades")
    private Integer winningTrades;

    /** 亏损次数 */
    @Column(name = "losing_trades")
    private Integer losingTrades;

    /** 平均持仓天数 */
    @Column(name = "avg_holding_days")
    private Double avgHoldingDays;

    /** 盈亏比 */
    @Column(name = "profit_loss_ratio")
    private Double profitLossRatio;

    /** 基准收益率(buy&hold) */
    @Column(name = "benchmark_return_pct")
    private Double benchmarkReturnPct;

    /** 超额收益(%) */
    @Column(name = "alpha_pct")
    private Double alphaPct;

    /** 交易明细JSON */
    @Column(name = "trade_details", columnDefinition = "TEXT")
    private String tradeDetails;

    /** 回测参数JSON */
    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }

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
