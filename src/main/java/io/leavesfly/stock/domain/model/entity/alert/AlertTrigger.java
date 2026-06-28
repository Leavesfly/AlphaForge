package io.leavesfly.stock.domain.model.entity.alert;

import java.time.LocalDateTime;

/**
 * 告警触发记录实体
 * 对应 alert_triggers 表
 */
public class AlertTrigger {

    /** 主键ID */
    private Long id;
    /** 关联规则ID */
    private Long ruleId;
    /** 触发目标(股票代码或指数) */
    private String target;
    /** 显示用目标名称 */
    private String displayTarget;
    /** 触发状态(active/acknowledged/resolved) */
    private String status;
    /** 观测值 */
    private Double observedValue;
    /** 阈值 */
    private Double thresholdValue;
    /** 触发消息 */
    private String message;
    /** 触发时间 */
    private LocalDateTime triggeredAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getDisplayTarget() { return displayTarget; }
    public void setDisplayTarget(String displayTarget) { this.displayTarget = displayTarget; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getObservedValue() { return observedValue; }
    public void setObservedValue(Double observedValue) { this.observedValue = observedValue; }
    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
}
