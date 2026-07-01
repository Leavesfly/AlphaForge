package io.leavesfly.alphaforge.presentation.bot.platform;

import io.leavesfly.alphaforge.presentation.bot.command.CommandDispatcher;
import io.leavesfly.alphaforge.presentation.bot.model.BotMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Telegram Bot Webhook处理器
 */
@RestController
@RequestMapping("/bot/telegram")
public class TelegramBotHandler extends AbstractBotHandler {

    public TelegramBotHandler(CommandDispatcher dispatcher, ObjectMapper objectMapper) {
        super(dispatcher, objectMapper);
    }

    @Override
    protected String getPlatform() {
        return "telegram";
    }

    @Override
    protected BotMessage extractMessage(JsonNode root) {
        JsonNode message = root.path("message");
        if (message.isMissingNode()) return null;

        String text = message.path("text").asText("");
        if (text.isEmpty()) return null;

        BotMessage botMsg = new BotMessage(text, "telegram");
        botMsg.setSenderId(message.path("from").path("id").asText(""));
        botMsg.setSenderName(message.path("from").path("first_name").asText("用户"));
        botMsg.setGroupId(message.path("chat").path("id").asText(""));
        return botMsg;
    }

    @Override
    protected ResponseEntity<Map<String, Object>> buildSuccessResponse(String reply) {
        // Telegram需要主动发送回复(通过sendMessage API)
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Override
    protected ResponseEntity<Map<String, Object>> buildEmptyResponse() {
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Override
    protected ResponseEntity<Map<String, Object>> buildErrorResponse(Exception e) {
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleUpdate(@RequestBody String body) {
        return super.handleWebhook(body);
    }
}
