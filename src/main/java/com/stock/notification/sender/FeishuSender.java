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

/**
 * 飞书Webhook通知发送器
 */
@Component
public class FeishuSender implements BaseNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(FeishuSender.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FeishuSender(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.FEISHU;
    }

    @Override
    public boolean supportsMarkdown() {
        return true;
    }

    @Override
    public boolean send(String title, String content) {
        String webhook = config.getFeishuWebhook();
        if (webhook == null || webhook.isEmpty()) {
            log.warn("飞书Webhook未配置");
            return false;
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("msg_type", "interactive");
            
            ObjectNode card = payload.putObject("card");
            ObjectNode cardHeader = card.putObject("header");
            ObjectNode titleNode = cardHeader.putObject("title");
            titleNode.put("tag", "plain_text");
            titleNode.put("content", title);
            
            var elements = card.putArray("elements");
            ObjectNode mdElement = elements.addObject();
            mdElement.put("tag", "markdown");
            mdElement.put("content", content);

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(payload),
                    MediaType.get("application/json"));

            Request request = new Request.Builder().url(webhook).post(body).build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.error("飞书通知发送失败: {}", e.getMessage());
            return false;
        }
    }
}
