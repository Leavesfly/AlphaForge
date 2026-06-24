package io.leavesfly.stock.infrastructure.notification.sender;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.enums.NotificationChannel;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/** PushPlus通知发送器 */
@Component
public class PushPlusSender implements BaseNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(PushPlusSender.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;

    public PushPlusSender(AppConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
    }

    @Override public NotificationChannel getChannel() { return NotificationChannel.PUSHPLUS; }
    @Override public boolean supportsMarkdown() { return true; }

    @Override
    public boolean send(String title, String content) {
        String token = config.getPushplusToken();
        if (token == null || token.isEmpty()) return false;
        try {
            String url = "https://www.pushplus.plus/send?token=" + token +
                    "&title=" + java.net.URLEncoder.encode(title, "UTF-8") +
                    "&content=" + java.net.URLEncoder.encode(content, "UTF-8") +
                    "&template=markdown";
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) { return response.isSuccessful(); }
        } catch (Exception e) { log.error("PushPlus通知发送失败: {}", e.getMessage()); return false; }
    }
}
