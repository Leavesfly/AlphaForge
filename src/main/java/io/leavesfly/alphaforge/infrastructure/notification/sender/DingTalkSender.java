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
 * 钉钉Webhook通知发送器
 */
@Component
public class DingTalkSender extends AbstractWebhookSender {

    private static final Logger log = LoggerFactory.getLogger(DingTalkSender.class);
    private final NotificationConfig config;

    public DingTalkSender(NotificationConfig config, OkHttpClient httpClient, ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
        this.config = config;
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
    protected String getWebhookUrl() {
        String webhook = config.getDingtalkWebhook();
        if (webhook == null || webhook.isEmpty()) {
            log.warn("钉钉Webhook未配置");
        }
        return webhook;
    }

    @Override
    protected ObjectNode buildPayload(String title, String content) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("msgtype", "markdown");
        ObjectNode markdown = payload.putObject("markdown");
        markdown.put("title", title);
        markdown.put("text", content);
        return payload;
    }
}
