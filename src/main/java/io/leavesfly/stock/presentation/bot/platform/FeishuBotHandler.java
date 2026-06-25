package io.leavesfly.stock.presentation.bot.platform;

import io.leavesfly.stock.presentation.bot.command.CommandDispatcher;
import io.leavesfly.stock.presentation.bot.model.BotMessage;
import io.leavesfly.stock.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 飞书Bot Webhook处理器
 */
@RestController
@RequestMapping("/bot/feishu")
public class FeishuBotHandler {

    private static final Logger log = LoggerFactory.getLogger(FeishuBotHandler.class);
    private final CommandDispatcher dispatcher;
    private final AppConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FeishuBotHandler(CommandDispatcher dispatcher, AppConfig config) {
        this.dispatcher = dispatcher;
        this.config = config;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            
            // 飞书URL验证
            if (root.has("challenge")) {
                return ResponseEntity.ok(Map.of("challenge", root.get("challenge").asText()));
            }

            // 解析消息
            JsonNode event = root.path("event");
            JsonNode message = event.path("message");
            String content = extractContent(message);
            String senderId = event.path("sender").path("sender_id").path("user_id").asText("");
            String senderName = event.path("sender").path("sender_id").path("name").asText("用户");

            if (content == null || content.isEmpty()) {
                return ResponseEntity.ok(Map.of("code", 0));
            }

            BotMessage botMsg = new BotMessage(content, "feishu");
            botMsg.setSenderId(senderId);
            botMsg.setSenderName(senderName);

            String reply = dispatcher.dispatch(botMsg);
            log.info("飞书消息处理完成: {} -> {}", content.substring(0, Math.min(20, content.length())), reply.substring(0, Math.min(50, reply.length())));

            return ResponseEntity.ok(Map.of("code", 0, "msg", "ok"));
        } catch (Exception e) {
            log.error("飞书Webhook处理失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("code", -1, "msg", e.getMessage()));
        }
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
}
