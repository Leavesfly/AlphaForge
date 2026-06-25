package io.leavesfly.stock.config;

/**
 * 通知渠道相关配置
 */
public class NotificationConfig {

    private String notificationChannels = "";
    private String wecomWebhook = "";
    private String feishuWebhook = "";
    private String dingtalkWebhook = "";
    private String emailSmtpHost = "";
    private int emailSmtpPort = 465;
    private String emailUser = "";
    private String emailPassword = "";
    private String emailTo = "";

    // Getters & Setters
    public String getNotificationChannels() { return notificationChannels; }
    public void setNotificationChannels(String notificationChannels) { this.notificationChannels = notificationChannels; }
    public String getWecomWebhook() { return wecomWebhook; }
    public void setWecomWebhook(String wecomWebhook) { this.wecomWebhook = wecomWebhook; }
    public String getFeishuWebhook() { return feishuWebhook; }
    public void setFeishuWebhook(String feishuWebhook) { this.feishuWebhook = feishuWebhook; }
    public String getDingtalkWebhook() { return dingtalkWebhook; }
    public void setDingtalkWebhook(String dingtalkWebhook) { this.dingtalkWebhook = dingtalkWebhook; }
    public String getEmailSmtpHost() { return emailSmtpHost; }
    public void setEmailSmtpHost(String emailSmtpHost) { this.emailSmtpHost = emailSmtpHost; }
    public int getEmailSmtpPort() { return emailSmtpPort; }
    public void setEmailSmtpPort(int emailSmtpPort) { this.emailSmtpPort = emailSmtpPort; }
    public String getEmailUser() { return emailUser; }
    public void setEmailUser(String emailUser) { this.emailUser = emailUser; }
    public String getEmailPassword() { return emailPassword; }
    public void setEmailPassword(String emailPassword) { this.emailPassword = emailPassword; }
    public String getEmailTo() { return emailTo; }
    public void setEmailTo(String emailTo) { this.emailTo = emailTo; }

}
