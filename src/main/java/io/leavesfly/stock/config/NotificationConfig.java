package io.leavesfly.stock.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知渠道相关配置
 */
public class NotificationConfig {

    private String notificationChannels = "";
    private String wecomWebhook = "";
    private String feishuWebhook = "";
    private String telegramBotToken = "";
    private String telegramChatId = "";
    private String emailSmtpHost = "";
    private int emailSmtpPort = 465;
    private String emailUser = "";
    private String emailPassword = "";
    private String emailTo = "";
    private String discordWebhook = "";
    private String slackWebhook = "";
    private String pushoverUserKey = "";
    private String pushoverAppToken = "";
    private String ntfyTopic = "";
    private String ntfyServer = "https://ntfy.sh";
    private String gotifyUrl = "";
    private String gotifyToken = "";
    private String pushplusToken = "";
    private String serverchan3Key = "";
    private String customWebhookUrl = "";
    private String customWebhookMethod = "POST";
    private Map<String, String> customWebhookHeaders = new HashMap<>();
    private String astrbotWebhook = "";

    // Getters & Setters
    public String getNotificationChannels() { return notificationChannels; }
    public void setNotificationChannels(String notificationChannels) { this.notificationChannels = notificationChannels; }
    public String getWecomWebhook() { return wecomWebhook; }
    public void setWecomWebhook(String wecomWebhook) { this.wecomWebhook = wecomWebhook; }
    public String getFeishuWebhook() { return feishuWebhook; }
    public void setFeishuWebhook(String feishuWebhook) { this.feishuWebhook = feishuWebhook; }
    public String getTelegramBotToken() { return telegramBotToken; }
    public void setTelegramBotToken(String telegramBotToken) { this.telegramBotToken = telegramBotToken; }
    public String getTelegramChatId() { return telegramChatId; }
    public void setTelegramChatId(String telegramChatId) { this.telegramChatId = telegramChatId; }
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
    public String getDiscordWebhook() { return discordWebhook; }
    public void setDiscordWebhook(String discordWebhook) { this.discordWebhook = discordWebhook; }
    public String getSlackWebhook() { return slackWebhook; }
    public void setSlackWebhook(String slackWebhook) { this.slackWebhook = slackWebhook; }
    public String getPushoverUserKey() { return pushoverUserKey; }
    public void setPushoverUserKey(String pushoverUserKey) { this.pushoverUserKey = pushoverUserKey; }
    public String getPushoverAppToken() { return pushoverAppToken; }
    public void setPushoverAppToken(String pushoverAppToken) { this.pushoverAppToken = pushoverAppToken; }
    public String getNtfyTopic() { return ntfyTopic; }
    public void setNtfyTopic(String ntfyTopic) { this.ntfyTopic = ntfyTopic; }
    public String getNtfyServer() { return ntfyServer; }
    public void setNtfyServer(String ntfyServer) { this.ntfyServer = ntfyServer; }
    public String getGotifyUrl() { return gotifyUrl; }
    public void setGotifyUrl(String gotifyUrl) { this.gotifyUrl = gotifyUrl; }
    public String getGotifyToken() { return gotifyToken; }
    public void setGotifyToken(String gotifyToken) { this.gotifyToken = gotifyToken; }
    public String getPushplusToken() { return pushplusToken; }
    public void setPushplusToken(String pushplusToken) { this.pushplusToken = pushplusToken; }
    public String getServerchan3Key() { return serverchan3Key; }
    public void setServerchan3Key(String serverchan3Key) { this.serverchan3Key = serverchan3Key; }
    public String getCustomWebhookUrl() { return customWebhookUrl; }
    public void setCustomWebhookUrl(String customWebhookUrl) { this.customWebhookUrl = customWebhookUrl; }
    public String getCustomWebhookMethod() { return customWebhookMethod; }
    public void setCustomWebhookMethod(String customWebhookMethod) { this.customWebhookMethod = customWebhookMethod; }
    public String getAstrbotWebhook() { return astrbotWebhook; }
    public void setAstrbotWebhook(String astrbotWebhook) { this.astrbotWebhook = astrbotWebhook; }
}
