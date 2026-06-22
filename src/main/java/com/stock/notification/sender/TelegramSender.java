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
 * Telegram通知发送器
 */
@Component
public class TelegramSender implements BaseNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramSender.class);
    private static final String TELEGRAM_API = "https://api.telegram.org/bot%s/sendMessage";
    
    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramSender(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public boolean supportsMarkdown() {
        return true;
    }

    @Override
    public int getMaxContentLength() {
        return 4096;
    }

    @Override
    public boolean send(String title, String content) {
        String botToken = config.getTelegramBotToken();
        String chatId = config.getTelegramChatId();
        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) {
            log.warn("Telegram配置不完整");
            return false;
        }

        try {
            String url = String.format(TELEGRAM_API, botToken);
            String fullContent = "**" + title + "**\n\n" + content;
            if (fullContent.length() > getMaxContentLength()) {
                fullContent = fullContent.substring(0, getMaxContentLength() - 10) + "\n...(已截断)";
            }

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("chat_id", chatId);
            payload.put("text", fullContent);
            payload.put("parse_mode", "Markdown");

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(payload),
                    MediaType.get("application/json"));

            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.error("Telegram通知发送失败: {}", e.getMessage());
            return false;
        }
    }
}
