package io.leavesfly.stock.domain.model.entity;

import java.time.LocalDateTime;

/**
 * 决策信号反馈实体
 * 对应 decision_signal_feedback 表
 */
public class DecisionSignalFeedback {

    private Long id;
    private Long signalId;
    private String feedbackValue;
    private String reasonCode;
    private String note;
    private String source;
    private LocalDateTime createdAt;
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
