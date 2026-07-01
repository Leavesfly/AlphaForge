package io.leavesfly.alphaforge.presentation.bot.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.presentation.bot.command.CommandDispatcher;
import io.leavesfly.alphaforge.presentation.bot.model.BotMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Bot Webhook 处理器抽象基类
 *
 * 封装各平台 Bot 处理器共享的公共逻辑：
 * - ObjectMapper 注入（不再各自 new）
 * - Webhook 处理模板：解析 JSON → 提取消息 → 分发 → 构建响应
 *
 * 子类只需实现 {@link #extractMessage(JsonNode)} 和 {@link #buildSuccessResponse(String)} 即可。
 */
public abstract class AbstractBotHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final CommandDispatcher dispatcher;
    protected final ObjectMapper objectMapper;

    protected AbstractBotHandler(CommandDispatcher dispatcher, ObjectMapper objectMapper) {
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    /** 各平台标识名（如 "dingtalk"、"feishu"、"telegram"） */
    protected abstract String getPlatform();

    /**
     * 从请求 JSON 中提取消息内容和发送者信息
     *
     * @param root 请求 JSON 根节点
     * @return 提取出的 BotMessage，若为空消息或无需处理则返回 null
     */
    protected abstract BotMessage extractMessage(JsonNode root);

    /**
     * 构建分发成功后的 HTTP 响应
     *
     * @param reply 分发器返回的回复文本
     * @return HTTP 响应体
     */
    protected abstract ResponseEntity<Map<String, Object>> buildSuccessResponse(String reply);

    /**
     * 处理 Webhook 请求的模板方法
     */
    public ResponseEntity<Map<String, Object>> handleWebhook(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);

            // 平台特定的前置处理（如飞书 URL 验证）
            ResponseEntity<Map<String, Object>> earlyReturn = handlePlatformSpecific(root);
            if (earlyReturn != null) return earlyReturn;

            BotMessage botMsg = extractMessage(root);
            if (botMsg == null || botMsg.getContent() == null || botMsg.getContent().isEmpty()) {
                return buildEmptyResponse();
            }

            String reply = dispatcher.dispatch(botMsg);
            logPlatformMessage(botMsg.getContent(), reply);
            return buildSuccessResponse(reply);
        } catch (Exception e) {
            log.error("{} Webhook处理失败: {}", getPlatform(), e.getMessage());
            return buildErrorResponse(e);
        }
    }

    /** 平台特定前置处理钩子（如飞书 challenge 验证），无需时返回 null */
    protected ResponseEntity<Map<String, Object>> handlePlatformSpecific(JsonNode root) {
        return null;
    }

    /** 空消息响应（默认返回成功） */
    protected ResponseEntity<Map<String, Object>> buildEmptyResponse() {
        return ResponseEntity.ok(Map.of());
    }

    /** 错误响应（默认返回成功以避免平台重试） */
    protected ResponseEntity<Map<String, Object>> buildErrorResponse(Exception e) {
        return ResponseEntity.ok(Map.of());
    }

    /** 平台日志输出 */
    protected void logPlatformMessage(String content, String reply) {
        String preview = content.length() > 20 ? content.substring(0, 20) : content;
        log.info("{}消息处理: {} -> 回复长度: {}", getPlatform(), preview, reply.length());
    }
}
