package io.leavesfly.stock.domain.model.entity.signal;

import java.time.LocalDateTime;

/**
 * 决策信号反馈实体
 * 对应 decision_signal_feedback 表
 */
public class DecisionSignalFeedback {

    /** 主键ID */
    private Long id;
    /** 关联信号ID */
    private Long signalId;
    /** 反馈值(agree/disagree/partial) */
    private String feedbackValue;
    /** 反对原因码 */
    private String reasonCode;
    /** 备注 */
    private String note;
    /** 来源(user/system) */
    private String source;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSignalId() { return signalId; }
    public void setSignalId(Long signalId) { this.signalId = signalId; }
    public String getFeedbackValue() { return feedbackValue; }
    public void setFeedbackValue(String feedbackValue) { this.feedbackValue = feedbackValue; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
