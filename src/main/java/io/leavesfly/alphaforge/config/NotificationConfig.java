package io.leavesfly.alphaforge.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 通知渠道相关配置 — 独立 Spring Bean，通过 EnvVarProvider 加载环境变量
 */
@Component
public class NotificationConfig {

    private final EnvVarProvider env;

    public NotificationConfig(EnvVarProvider env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        notificationChannels = env.get("NOTIFICATION_CHANNELS", "");
        wecomWebhook = env.get("WECOM_WEBHOOK", "");
        feishuWebhook = env.get("FEISHU_WEBHOOK", "");
        dingtalkWebhook = env.get("DINGTALK_WEBHOOK", "");
        emailSmtpHost = env.get("EMAIL_SMTP_HOST", "");
        emailSmtpPort = env.getInt("EMAIL_SMTP_PORT", 465);
        emailUser = env.get("EMAIL_USER", "");
        emailPassword = env.get("EMAIL_PASSWORD", "");
        emailTo = env.get("EMAIL_TO", "");
    }

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
