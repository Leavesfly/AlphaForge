package io.leavesfly.stock.domain.model.entity;

import java.time.LocalDateTime;

/**
 * 告警规则实体
 * 对应Python版本的 alert_repo.py
 */
public class AlertRule {

    private Long id;

    /** 股票代码 */
    private String stockCode;

    /** 股票名称 */
    private String stockName;

    /** 告警类型: price_above/price_below/change_pct_above/change_pct_below/volume_above/custom */
    private String alertType;

    /** 触发条件值 */
    private Double thresholdValue;

    /** 自定义条件表达式(JSON) */
    private String conditionExpr;

    /** 是否启用 */
    private Boolean enabled = true;

    /** 是否已触发 */
    private Boolean triggered = false;

    /** 上次触发时间 */
    private LocalDateTime lastTriggeredAt;

    /** 触发后是否自动禁用(一次性告警) */
    private Boolean oneShot = false;

    /** 通知渠道(逗号分隔) */
    private String notifyChannels;

    /** 备注 */
    private String note;

    /** 创建时间 */
    private LocalDateTime createdAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getConditionExpr() { return conditionExpr; }
    public void setConditionExpr(String conditionExpr) { this.conditionExpr = conditionExpr; }
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
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
