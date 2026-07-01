package io.leavesfly.alphaforge.infrastructure.notification.sender;

import io.leavesfly.alphaforge.config.NotificationConfig;
import io.leavesfly.alphaforge.domain.model.enums.NotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 企业微信Webhook通知发送器
 */
@Component
public class WecomSender extends AbstractWebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WecomSender.class);
    private final NotificationConfig config;

    public WecomSender(NotificationConfig config, OkHttpClient httpClient, ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
        this.config = config;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.WECOM;
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
    protected String getWebhookUrl() {
        String webhook = config.getWecomWebhook();
        if (webhook == null || webhook.isEmpty()) {
            log.warn("企业微信Webhook未配置");
        }
        return webhook;
    }

    @Override
    protected ObjectNode buildPayload(String title, String content) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("msgtype", "markdown");
        ObjectNode markdown = payload.putObject("markdown");
        markdown.put("content", content);
        return payload;
    }
}
