package io.leavesfly.stock.model.enums;

/**
 * 通知渠道枚举
 * 支持13+通知渠道
 */
public enum NotificationChannel {
    WECOM("wecom", "企业微信"),
    FEISHU("feishu", "飞书"),
    TELEGRAM("telegram", "Telegram"),
    EMAIL("email", "邮件"),
    DISCORD("discord", "Discord"),
    SLACK("slack", "Slack"),
    PUSHOVER("pushover", "Pushover"),
    NTFY("ntfy", "ntfy"),
    GOTIFY("gotify", "Gotify"),
    PUSHPLUS("pushplus", "PushPlus"),
    SERVERCHAN3("serverchan3", "Server酱3"),
    CUSTOM_WEBHOOK("custom_webhook", "自定义Webhook"),
    ASTRBOT("astrbot", "AstrBot");

    private final String code;
    private final String name;

    NotificationChannel(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }

    public static NotificationChannel fromCode(String code) {
        for (NotificationChannel ch : values()) {
            if (ch.code.equalsIgnoreCase(code)) return ch;
        }
        return null;
    }
}
