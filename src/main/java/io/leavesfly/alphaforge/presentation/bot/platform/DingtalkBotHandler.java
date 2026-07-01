package io.leavesfly.alphaforge.presentation.bot.platform;

import io.leavesfly.alphaforge.presentation.bot.command.CommandDispatcher;
import io.leavesfly.alphaforge.presentation.bot.model.BotMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 钉钉Bot Webhook处理器
 */
@RestController
@RequestMapping("/bot/dingtalk")
public class DingtalkBotHandler extends AbstractBotHandler {

    public DingtalkBotHandler(CommandDispatcher dispatcher, ObjectMapper objectMapper) {
        super(dispatcher, objectMapper);
    }

    @Override
    protected String getPlatform() {
        return "dingtalk";
    }

    @Override
    protected BotMessage extractMessage(JsonNode root) {
        String msgType = root.path("msgtype").asText("text");
        String content = "";

        if ("text".equals(msgType)) {
            content = root.path("text").path("content").asText("").trim();
        }

        if (content.isEmpty()) return null;

        BotMessage botMsg = new BotMessage(content, "dingtalk");
        botMsg.setSenderId(root.path("senderStaffId").asText(""));
        botMsg.setSenderName(root.path("senderNick").asText("用户"));
        return botMsg;
    }

    @Override
    protected ResponseEntity<Map<String, Object>> buildSuccessResponse(String reply) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("msgtype", "text");
        response.put("text", Map.of("content", reply));
        return ResponseEntity.ok(response);
    }

    @Override
    protected ResponseEntity<Map<String, Object>> buildEmptyResponse() {
        return ResponseEntity.ok(Map.of("msgtype", "empty"));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody String body) {
        return super.handleWebhook(body);
    }
}
