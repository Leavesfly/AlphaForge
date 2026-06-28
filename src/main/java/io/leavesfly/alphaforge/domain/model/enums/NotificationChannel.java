package io.leavesfly.alphaforge.domain.model.enums;

/**
 * 通知渠道枚举
 * 支持企业微信、飞书、钉钉、邮件通知渠道
 */
public enum NotificationChannel {
    WECOM("wecom", "企业微信"),
    FEISHU("feishu", "飞书"),
    DINGTALK("dingtalk", "钉钉"),
    EMAIL("email", "邮件");

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
