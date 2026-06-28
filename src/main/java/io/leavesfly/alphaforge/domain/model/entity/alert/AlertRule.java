package io.leavesfly.alphaforge.domain.model.entity.alert;

import java.time.LocalDateTime;

/**
 * 告警规则实体
 */
public class AlertRule {

    /** 主键ID */
    private Long id;
    /** 规则名称 */
    private String name;
    /** 股票代码 */
    private String stockCode;
    /** 股票名称 */
    private String stockName;
    /** 告警类型(如 price_cross/volume_spike/rsi_over) */
    private String alertType;
    /** 目标范围(single/portfolio/market) */
    private String targetScope;
    /** 监控目标(股票代码或指数代码) */
    private String target;
    /** 严重级别(info/warning/critical) */
    private String severity;
    /** 触发阈值 */
    private Double thresholdValue;
    /** 条件表达式 */
    private String conditionExpr;
    /** JSON格式参数 */
    private String parameters;
    /** 是否启用 */
    private Boolean enabled = true;
    /** 是否已触发 */
    private Boolean triggered = false;
    /** 最后触发时间 */
    private LocalDateTime lastTriggeredAt;
    /** 是否一次性告警(触发后自动禁用) */
    private Boolean oneShot = false;
    /** 通知渠道(逗号分隔) */
    private String notifyChannels;
    /** 来源 */
    private String source;
    /** 备注 */
    private String note;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getTargetScope() { return targetScope; }
    public void setTargetScope(String targetScope) { this.targetScope = targetScope; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getConditionExpr() { return conditionExpr; }
    public void setConditionExpr(String conditionExpr) { this.conditionExpr = conditionExpr; }
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getTriggered() { return triggered; }
    public void setTriggered(Boolean triggered) { this.triggered = triggered; }
    public LocalDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(LocalDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
    public Boolean getOneShot() { return oneShot; }
    public void setOneShot(Boolean oneShot) { this.oneShot = oneShot; }
    public String getNotifyChannels() { return notifyChannels; }
    public void setNotifyChannels(String notifyChannels) { this.notifyChannels = notifyChannels; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
