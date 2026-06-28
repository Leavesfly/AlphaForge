package io.leavesfly.stock.domain.model.entity.alert;

import java.time.LocalDateTime;

/**
 * 告警通知记录实体
 * 对应 alert_notifications 表
 */
public class AlertNotification {

    /** 主键ID */
    private Long id;
    /** 关联触发记录ID */
    private Long triggerId;
    /** 通知渠道(dingtalk/feishu/telegram/webhook) */
    private String channel;
    /** 是否发送成功 */
    private Boolean success;
    /** 错误码 */
    private String errorCode;
    /** 错误信息 */
    private String errorMessage;
    /** 发送时间 */
    private LocalDateTime sentAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTriggerId() { return triggerId; }
    public void setTriggerId(Long triggerId) { this.triggerId = triggerId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
