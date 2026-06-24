package io.leavesfly.stock.domain.model.entity;

import java.time.LocalDateTime;

/**
 * 决策信号实体
 * 对齐 schema.sql decision_signals 表全部字段
 */
public class DecisionSignal {

    private Long id;
    private String stockCode;
    private String stockName;
    private String market;
    private String sourceType;
    private String sourceAgent;
    private Long sourceReportId;
    private String traceId;
    private String marketPhase;
    private String triggerSource;
    private String action;
    private String actionLabel;
    private Double confidence;
    private Integer score;
    private String horizon;
    private Double entryLow;
    private Double entryHigh;
    private Double stopLoss;
    private Double targetPrice;
    private String invalidation;
    private String watchConditions;
    private String reason;
    private String riskSummary;
    private String catalystSummary;
    /** JSON格式证据 */
    private String evidence;
    private String dataQualitySummary;
    private String planQuality;
    private String status;
    private LocalDateTime expiresAt;
    /** JSON格式元数据 */
    private String metadata;
    private String reportLanguage;
    private LocalDateTime createdAt;
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
