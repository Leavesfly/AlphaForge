package io.leavesfly.stock.model.entity;

import java.time.LocalDateTime;

/**
 * 决策信号实体
 * 对应Python版本的 decision_signal_repo.py
 */
public class DecisionSignal {

    private Long id;

    /** 股票代码 */
    private String stockCode;

    /** 股票名称 */
    private String stockName;

    /** 信号类型: buy/sell/hold */
    private String signalType;

    /** 信号强度(0-100) */
    private Integer strength;

    /** 信号来源: technical/fundamental/news/agent */
    private String source;

    /** 目标价格 */
    private Double targetPrice;

    /** 止损价格 */
    private Double stopLossPrice;

    /** 信号触发时价格 */
    private Double triggerPrice;

    /** 推荐仓位(%) */
    private Double positionPct;

    /** 理由说明 */
    private String reasoning;

    /** 置信度(0-1) */
    private Double confidence;

    /** 信号状态: active/expired/executed/cancelled */
    private String status = "active";

    /** 有效期截止 */
    private LocalDateTime validUntil;

    /** 关联的分析报告ID */
    private Long reportId;

    private LocalDateTime createdAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getSignalType() { return signalType; }
    public void setSignalType(String signalType) { this.signalType = signalType; }
    public Integer getStrength() { return strength; }
    public void setStrength(Integer strength) { this.strength = strength; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(Double targetPrice) { this.targetPrice = targetPrice; }
    public Double getStopLossPrice() { return stopLossPrice; }
    public void setStopLossPrice(Double stopLossPrice) { this.stopLossPrice = stopLossPrice; }
    public Double getTriggerPrice() { return triggerPrice; }
    public void setTriggerPrice(Double triggerPrice) { this.triggerPrice = triggerPrice; }
    public Double getPositionPct() { return positionPct; }
    public void setPositionPct(Double positionPct) { this.positionPct = positionPct; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
