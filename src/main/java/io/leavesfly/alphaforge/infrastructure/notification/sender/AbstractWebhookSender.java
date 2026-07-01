package io.leavesfly.alphaforge.infrastructure.notification.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.leavesfly.alphaforge.config.InfrastructureConfig;
import okhttp3.*;

import java.util.concurrent.TimeUnit;

/**
 * Webhook 通知发送器抽象基类
 *
 * 封装所有 Webhook 发送器共享的公共逻辑：
 * - OkHttpClient / ObjectMapper 注入（不再各自 new）
 * - HTTP POST 发送模板
 * - 内容截断
 *
 * 子类只需实现 {@link #buildPayload(String, String)} 和 {@link #getWebhookUrl()} 即可。
 */
public abstract class AbstractWebhookSender implements BaseNotificationSender {

    protected final OkHttpClient httpClient;
    protected final ObjectMapper objectMapper;

    protected AbstractWebhookSender(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建各平台特定的请求体 JSON
     *
     * @param title   通知标题
     * @param content 通知内容（已截断）
     * @return 请求体 JSON ObjectNode
     */
    protected abstract ObjectNode buildPayload(String title, String content);

    /**
     * 获取各平台配置的 Webhook URL
     *
     * @return webhook URL，未配置时返回 null
     */
    protected abstract String getWebhookUrl();

    @Override
    public boolean send(String title, String content) {
        String webhook = getWebhookUrl();
        if (webhook == null || webhook.isEmpty()) {
            return false;
        }

        try {
            String truncated = truncateContent(content, getMaxContentLength());
            ObjectNode payload = buildPayload(title, truncated);

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
            return false;
        }
    }

    /**
     * 截断内容到指定长度
     */
    protected String truncateContent(String content, int maxLen) {
        if (content == null) return "";
        if (content.length() <= maxLen) return content;
        return content.substring(0, maxLen - 10) + "\n...(已截断)";
    }
}
