package io.leavesfly.stock.infrastructure.notification.sender;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.enums.NotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 钉钉Webhook通知发送器
 */
@Component
public class DingTalkSender implements BaseNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(DingTalkSender.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DingTalkSender(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.DINGTALK;
    }

    @Override
    public boolean supportsMarkdown() {
        return true;
    }

    @Override
    public int getMaxContentLength() {
        return 20000;
    }

    @Override
    public boolean send(String title, String content) {
        String webhook = config.getDingtalkWebhook();
        if (webhook == null || webhook.isEmpty()) {
            log.warn("钉钉Webhook未配置");
            return false;
        }

        try {
            String truncated = truncateContent(content, getMaxContentLength());

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("msgtype", "markdown");
            ObjectNode markdown = payload.putObject("markdown");
            markdown.put("title", title);
            markdown.put("text", truncated);

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(payload),
                    MediaType.get("application/json"));

            Request request = new Request.Builder()
                    .url(webhook)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.error("钉钉通知发送失败: {}", e.getMessage());
            return false;
        }
    }

    private String truncateContent(String content, int maxLen) {
        if (content == null) return "";
        if (content.length() <= maxLen) return content;
        return content.substring(0, maxLen - 10) + "\n...(已截断)";
    }
}
