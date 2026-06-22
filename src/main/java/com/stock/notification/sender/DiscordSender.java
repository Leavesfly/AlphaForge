package com.stock.notification.sender;

import com.stock.config.AppConfig;
import com.stock.model.enums.NotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/** Discord通知发送器 */
@Component
public class DiscordSender implements BaseNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(DiscordSender.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DiscordSender(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
    }

    @Override public NotificationChannel getChannel() { return NotificationChannel.DISCORD; }
    @Override public boolean supportsMarkdown() { return true; }
    @Override public int getMaxContentLength() { return 2000; }

    @Override
    public boolean send(String title, String content) {
        String webhook = config.getDiscordWebhook();
        if (webhook == null || webhook.isEmpty()) return false;
        try {
            String truncated = content.length() > 1900 ? content.substring(0, 1900) + "..." : content;
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("content", "**" + title + "**\n" + truncated);
            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(payload), MediaType.get("application/json"));
            Request request = new Request.Builder().url(webhook).post(body).build();
            try (Response response = httpClient.newCall(request).execute()) { return response.isSuccessful(); }
        } catch (Exception e) { log.error("Discord通知发送失败: {}", e.getMessage()); return false; }
    }
}
