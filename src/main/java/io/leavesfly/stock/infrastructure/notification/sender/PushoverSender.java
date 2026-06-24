package io.leavesfly.stock.infrastructure.notification.sender;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.enums.NotificationChannel;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/** Pushover通知发送器 */
@Component
public class PushoverSender implements BaseNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(PushoverSender.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;

    public PushoverSender(AppConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
    }

    @Override public NotificationChannel getChannel() { return NotificationChannel.PUSHOVER; }

    @Override
    public boolean send(String title, String content) {
        String userKey = config.getPushoverUserKey();
        String appToken = config.getPushoverAppToken();
        if (userKey == null || userKey.isEmpty() || appToken == null || appToken.isEmpty()) return false;
        try {
            FormBody body = new FormBody.Builder()
                    .add("token", appToken)
                    .add("user", userKey)
                    .add("title", title)
                    .add("message", content)
                    .add("html", "1")
                    .build();
            Request request = new Request.Builder().url("https://api.pushover.net/1/messages.json").post(body).build();
            try (Response response = httpClient.newCall(request).execute()) { return response.isSuccessful(); }
        } catch (Exception e) { log.error("Pushover发送失败: {}", e.getMessage()); return false; }
    }
}
