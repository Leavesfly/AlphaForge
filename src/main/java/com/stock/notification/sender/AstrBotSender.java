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

/** AstrBot通知发送器 */
@Component
public class AstrBotSender implements BaseNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(AstrBotSender.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AstrBotSender(AppConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
    }

    @Override public NotificationChannel getChannel() { return NotificationChannel.ASTRBOT; }
    @Override public boolean supportsMarkdown() { return true; }

    @Override
    public boolean send(String title, String content) {
        String webhook = config.getAstrbotWebhook();
        if (webhook == null || webhook.isEmpty()) return false;
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("title", title);
            payload.put("content", content);
            payload.put("format", "markdown");
            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(payload), MediaType.get("application/json"));
            Request request = new Request.Builder().url(webhook).post(body).build();
            try (Response response = httpClient.newCall(request).execute()) { return response.isSuccessful(); }
        } catch (Exception e) { log.error("AstrBot发送失败: {}", e.getMessage()); return false; }
    }
}
