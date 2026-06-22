package com.stock.notification.sender;

import com.stock.config.AppConfig;
import com.stock.model.enums.NotificationChannel;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/** Ntfy通知发送器 */
@Component
public class NtfySender implements BaseNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(NtfySender.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;

    public NtfySender(AppConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
    }

    @Override public NotificationChannel getChannel() { return NotificationChannel.NTFY; }
    @Override public boolean supportsMarkdown() { return true; }

    @Override
    public boolean send(String title, String content) {
        String topic = config.getNtfyTopic();
        String server = config.getNtfyServer();
        if (topic == null || topic.isEmpty()) return false;
        try {
            String url = (server != null && !server.isEmpty() ? server : "https://ntfy.sh") + "/" + topic;
            RequestBody body = RequestBody.create(content, MediaType.get("text/markdown"));
            Request request = new Request.Builder().url(url).post(body)
                    .header("Title", title).header("Priority", "default").build();
            try (Response response = httpClient.newCall(request).execute()) { return response.isSuccessful(); }
        } catch (Exception e) { log.error("Ntfy通知发送失败: {}", e.getMessage()); return false; }
    }
}
