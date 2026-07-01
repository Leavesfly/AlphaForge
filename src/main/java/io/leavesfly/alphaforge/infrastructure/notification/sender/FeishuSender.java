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
 * 飞书Webhook通知发送器
 */
@Component
public class FeishuSender extends AbstractWebhookSender {

    private static final Logger log = LoggerFactory.getLogger(FeishuSender.class);
    private final NotificationConfig config;

    public FeishuSender(NotificationConfig config, OkHttpClient httpClient, ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
        this.config = config;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.FEISHU;
    }

    @Override
    public boolean supportsMarkdown() {
        return true;
    }

    @Override
    protected String getWebhookUrl() {
        String webhook = config.getFeishuWebhook();
        if (webhook == null || webhook.isEmpty()) {
            log.warn("飞书Webhook未配置");
        }
        return webhook;
    }

    @Override
    protected ObjectNode buildPayload(String title, String content) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("msg_type", "interactive");

        ObjectNode card = payload.putObject("card");
        ObjectNode cardHeader = card.putObject("header");
        ObjectNode titleNode = cardHeader.putObject("title");
        titleNode.put("tag", "plain_text");
        titleNode.put("content", title);

        var elements = card.putArray("elements");
        ObjectNode mdElement = elements.addObject();
        mdElement.put("tag", "markdown");
        mdElement.put("content", content);
        return payload;
    }
}
