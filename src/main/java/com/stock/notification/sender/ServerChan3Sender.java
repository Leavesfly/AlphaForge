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

/** Server酱3通知发送器 */
@Component
public class ServerChan3Sender implements BaseNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(ServerChan3Sender.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;

    public ServerChan3Sender(AppConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
    }

    @Override public NotificationChannel getChannel() { return NotificationChannel.SERVERCHAN3; }
    @Override public boolean supportsMarkdown() { return true; }

    @Override
    public boolean send(String title, String content) {
        String key = config.getServerchan3Key();
        if (key == null || key.isEmpty()) return false;
        try {
            String url = "https://sctapi.ftqq.com/" + key + ".send";
            FormBody body = new FormBody.Builder()
                    .add("title", title)
                    .add("desp", content)
                    .build();
            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = httpClient.newCall(request).execute()) { return response.isSuccessful(); }
        } catch (Exception e) { log.error("Server酱3发送失败: {}", e.getMessage()); return false; }
    }
}
