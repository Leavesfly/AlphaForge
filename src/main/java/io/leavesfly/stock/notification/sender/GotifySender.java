package io.leavesfly.stock.notification.sender;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.model.enums.NotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/** Gotify通知发送器 */
@Component
public class GotifySender implements BaseNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(GotifySender.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GotifySender(AppConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
    }

    @Override public NotificationChannel getChannel() { return NotificationChannel.GOTIFY; }
    @Override public boolean supportsMarkdown() { return true; }

    @Override
    public boolean send(String title, String content) {
        String url = config.getGotifyUrl();
        String token = config.getGotifyToken();
        if (url == null || url.isEmpty() || token == null || token.isEmpty()) return false;
        try {
            String apiUrl = url.endsWith("/") ? url + "message" : url + "/message";
            apiUrl += "?token=" + token;
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("title", title);
            payload.put("message", content);
            payload.put("priority", 5);
            ObjectNode extras = payload.putObject("extras");
            extras.putObject("client::display").put("contentType", "text/markdown");
            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(payload), MediaType.get("application/json"));
            Request request = new Request.Builder().url(apiUrl).post(body).build();
            try (Response response = httpClient.newCall(request).execute()) { return response.isSuccessful(); }
        } catch (Exception e) { log.error("Gotify通知发送失败: {}", e.getMessage()); return false; }
    }
}
