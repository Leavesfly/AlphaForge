package com.stock.notification.sender;

import com.stock.model.enums.NotificationChannel;

/**
 * 通知发送器基础接口
 * 所有通知渠道发送器都需要实现此接口
 */
public interface BaseNotificationSender {

    /**
     * 获取通知渠道类型
     */
    NotificationChannel getChannel();

    /**
     * 发送通知
     *
     * @param title   通知标题
     * @param content 通知内容
     * @return 是否发送成功
     */
    boolean send(String title, String content);

    /**
     * 是否支持Markdown格式
     */
    default boolean supportsMarkdown() {
        return false;
    }

    /**
     * 是否支持图片发送
     */
    default boolean supportsImage() {
        return false;
    }

    /**
     * 获取内容长度限制
     */
    default int getMaxContentLength() {
        return 4096;
    }
}
