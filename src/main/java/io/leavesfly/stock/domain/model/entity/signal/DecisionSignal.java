package io.leavesfly.stock.domain.model.entity.signal;

import java.time.LocalDateTime;

/**
 * 决策信号实体
 * 对齐 schema.sql decision_signals 表全部字段
 */
public class DecisionSignal {

    /** 主键ID */
    private Long id;
    /** 股票代码 */
    private String stockCode;
    /** 股票名称 */
    private String stockName;
    /** 市场(A/US/HK) */
    private String market;
    /** 来源类型(llm/technical/manual) */
    private String sourceType;
    /** 来源Agent名称 */
    private String sourceAgent;
    /** 来源报告ID */
    private Long sourceReportId;
    /** 追踪ID(用于全链路追踪) */
    private String traceId;
    /** 市场阶段(accumulation/distribution/breakdown) */
    private String marketPhase;
    /** 触发源 */
    private String triggerSource;
    /** 动作(buy/sell/hold) */
    private String action;
    /** 动作标签(中文展示) */
    private String actionLabel;
    /** 置信度(0-1) */
    private Double confidence;
    /** 综合评分(0-100) */
    private Integer score;
    /** 投资周期(short/mid/long) */
    private String horizon;
    /** 入场价下限 */
    private Double entryLow;
    /** 入场价上限 */
    private Double entryHigh;
    /** 止损价 */
    private Double stopLoss;
    /** 目标价 */
    private Double targetPrice;
    /** 失效条件 */
    private String invalidation;
    /** 观察条件 */
    private String watchConditions;
    /** 决策理由 */
    private String reason;
    /** 风险摘要 */
    private String riskSummary;
    /** 催化剂摘要 */
    private String catalystSummary;
    /** JSON格式证据 */
    private String evidence;
    /** 数据质量摘要 */
    private String dataQualitySummary;
    /** 计划质量评估 */
    private String planQuality;
    /** 状态(active/expired/closed) */
    private String status;
    /** 过期时间 */
    private LocalDateTime expiresAt;
    /** JSON格式元数据 */
    private String metadata;
    /** 报告语言 */
    private String reportLanguage;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceAgent() { return sourceAgent; }
    public void setSourceAgent(String sourceAgent) { this.sourceAgent = sourceAgent; }
    public Long getSourceReportId() { return sourceReportId; }
    public void setSourceReportId(Long sourceReportId) { this.sourceReportId = sourceReportId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getMarketPhase() { return marketPhase; }
    public void setMarketPhase(String marketPhase) { this.marketPhase = marketPhase; }
    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActionLabel() { return actionLabel; }
    public void setActionLabel(String actionLabel) { this.actionLabel = actionLabel; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getHorizon() { return horizon; }
    public void setHorizon(String horizon) { this.horizon = horizon; }
    public Double getEntryLow() { return entryLow; }
    public void setEntryLow(Double entryLow) { this.entryLow = entryLow; }
    public Double getEntryHigh() { return entryHigh; }
    public void setEntryHigh(Double entryHigh) { this.entryHigh = entryHigh; }
    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }
    public Double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(Double targetPrice) { this.targetPrice = targetPrice; }
    public String getInvalidation() { return invalidation; }
    public void setInvalidation(String invalidation) { this.invalidation = invalidation; }
    public String getWatchConditions() { return watchConditions; }
    public void setWatchConditions(String watchConditions) { this.watchConditions = watchConditions; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRiskSummary() { return riskSummary; }
    public void setRiskSummary(String riskSummary) { this.riskSummary = riskSummary; }
    public String getCatalystSummary() { return catalystSummary; }
    public void setCatalystSummary(String catalystSummary) { this.catalystSummary = catalystSummary; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getDataQualitySummary() { return dataQualitySummary; }
    public void setDataQualitySummary(String dataQualitySummary) { this.dataQualitySummary = dataQualitySummary; }
    public String getPlanQuality() { return planQuality; }
    public void setPlanQuality(String planQuality) { this.planQuality = planQuality; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getReportLanguage() { return reportLanguage; }
    public void setReportLanguage(String reportLanguage) { this.reportLanguage = reportLanguage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
