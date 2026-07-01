package io.leavesfly.alphaforge.presentation.bot.platform;

import io.leavesfly.alphaforge.presentation.bot.command.CommandDispatcher;
import io.leavesfly.alphaforge.presentation.bot.model.BotMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 飞书Bot Webhook处理器
 */
@RestController
@RequestMapping("/bot/feishu")
public class FeishuBotHandler extends AbstractBotHandler {

    public FeishuBotHandler(CommandDispatcher dispatcher, ObjectMapper objectMapper) {
        super(dispatcher, objectMapper);
    }

    @Override
    protected String getPlatform() {
        return "feishu";
    }

    @Override
    protected ResponseEntity<Map<String, Object>> handlePlatformSpecific(JsonNode root) {
        // 飞书URL验证
        if (root.has("challenge")) {
            return ResponseEntity.ok(Map.of("challenge", root.get("challenge").asText()));
        }
        return null;
    }

    @Override
    protected BotMessage extractMessage(JsonNode root) {
        JsonNode event = root.path("event");
        JsonNode message = event.path("message");
        String content = extractContent(message);

        if (content == null || content.isEmpty()) return null;

        BotMessage botMsg = new BotMessage(content, "feishu");
        botMsg.setSenderId(event.path("sender").path("sender_id").path("user_id").asText(""));
        botMsg.setSenderName(event.path("sender").path("sender_id").path("name").asText("用户"));
        return botMsg;
    }

    @Override
    protected ResponseEntity<Map<String, Object>> buildSuccessResponse(String reply) {
        return ResponseEntity.ok(Map.of("code", 0, "msg", "ok"));
    }

    @Override
    protected ResponseEntity<Map<String, Object>> buildEmptyResponse() {
        return ResponseEntity.ok(Map.of("code", 0));
    }

    @Override
    protected ResponseEntity<Map<String, Object>> buildErrorResponse(Exception e) {
        return ResponseEntity.ok(Map.of("code", -1, "msg", e.getMessage()));
    }

    private String extractContent(JsonNode message) {
        String msgType = message.path("message_type").asText("");
        if ("text".equals(msgType)) {
            try {
                JsonNode contentNode = objectMapper.readTree(message.path("content").asText("{}"));
                return contentNode.path("text").asText("").trim();
            } catch (Exception e) { return ""; }
        }
        return "";
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody String body) {
        return super.handleWebhook(body);
    }
}
