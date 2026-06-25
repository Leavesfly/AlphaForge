package io.leavesfly.stock.domain.model.entity;

import java.time.LocalDateTime;

/**
 * 回测记录实体
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

    /** 关联信号ID */
    private Long signalId;

    /** 市场阶段 */
    private String marketPhase;

    /** 市场阶段总结 */
    private String marketPhaseSummary;

    /** 信号动作 */
    private String action;

    /** 预期方向 */
    private String directionExpected;

    /** 实际变动 */
    private String actualMovement;

    /** 回测结果 */
    private String outcome;

    /** 评估状态 */
    private String evalStatus;

    /** 评估窗口天数 */
    private Integer evalWindowDays;

    /** 回报率(%) */
    private Double returnPct;

    /** 分析日期 */
    private LocalDateTime analysisDate;

    /** 诊断信息JSON */
    private String diagnostics;

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
    public Long getSignalId() { return signalId; }
    public void setSignalId(Long signalId) { this.signalId = signalId; }
    public String getMarketPhase() { return marketPhase; }
    public void setMarketPhase(String marketPhase) { this.marketPhase = marketPhase; }
    public String getMarketPhaseSummary() { return marketPhaseSummary; }
    public void setMarketPhaseSummary(String marketPhaseSummary) { this.marketPhaseSummary = marketPhaseSummary; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDirectionExpected() { return directionExpected; }
    public void setDirectionExpected(String directionExpected) { this.directionExpected = directionExpected; }
    public String getActualMovement() { return actualMovement; }
    public void setActualMovement(String actualMovement) { this.actualMovement = actualMovement; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getEvalStatus() { return evalStatus; }
    public void setEvalStatus(String evalStatus) { this.evalStatus = evalStatus; }
    public Integer getEvalWindowDays() { return evalWindowDays; }
    public void setEvalWindowDays(Integer evalWindowDays) { this.evalWindowDays = evalWindowDays; }
    public Double getReturnPct() { return returnPct; }
    public void setReturnPct(Double returnPct) { this.returnPct = returnPct; }
    public LocalDateTime getAnalysisDate() { return analysisDate; }
    public void setAnalysisDate(LocalDateTime analysisDate) { this.analysisDate = analysisDate; }
    public String getDiagnostics() { return diagnostics; }
    public void setDiagnostics(String diagnostics) { this.diagnostics = diagnostics; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
