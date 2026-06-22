package com.stock.bot.model;

/**
 * Bot统一消息模型
 * 对应Python版本的 bot/models.py
 */
public class BotMessage {
    
    /** 消息内容 */
    private String content;
    
    /** 发送者ID */
    private String senderId;
    
    /** 发送者名称 */
    private String senderName;
    
    /** 来源平台 */
    private String platform;
    
    /** 群组/频道ID */
    private String groupId;
    
    /** 消息类型(text/image/command) */
    private String messageType = "text";
    
    /** 原始消息对象 */
    private Object rawMessage;

    public BotMessage() {}

    public BotMessage(String content, String platform) {
        this.content = content;
        this.platform = platform;
    }

    // Getters and Setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public Object getRawMessage() { return rawMessage; }
    public void setRawMessage(Object rawMessage) { this.rawMessage = rawMessage; }

    /**
     * 判断是否为命令消息
     */
    public boolean isCommand() {
        return content != null && content.startsWith("/");
    }

    /**
     * 获取命令名称
     */
    public String getCommandName() {
        if (!isCommand()) return null;
        String[] parts = content.split("\\s+", 2);
        return parts[0].substring(1).toLowerCase();
    }

    /**
     * 获取命令参数
     */
    public String getCommandArgs() {
        if (!isCommand()) return "";
        String[] parts = content.split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }
}
