package io.leavesfly.stock.infrastructure.notification.sender;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.enums.NotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/** 自定义Webhook通知发送器 */
@Component
public class CustomWebhookSender implements BaseNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(CustomWebhookSender.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomWebhookSender(AppConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
    }

    @Override public NotificationChannel getChannel() { return NotificationChannel.CUSTOM_WEBHOOK; }

    @Override
    public boolean send(String title, String content) {
        String url = config.getCustomWebhookUrl();
        if (url == null || url.isEmpty()) return false;
        try {
            String payload = objectMapper.writeValueAsString(java.util.Map.of("title", title, "content", content, "timestamp", System.currentTimeMillis()));
            Request.Builder builder = new Request.Builder().url(url);
            String method = config.getCustomWebhookMethod();
            RequestBody body = RequestBody.create(payload, MediaType.get("application/json"));
            if ("GET".equalsIgnoreCase(method)) {
                builder.get();
            } else {
                builder.post(body);
            }
            try (Response response = httpClient.newCall(builder.build()).execute()) { return response.isSuccessful(); }
        } catch (Exception e) { log.error("自定义Webhook发送失败: {}", e.getMessage()); return false; }
    }
}
