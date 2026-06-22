package com.stock.bot.platform;

import com.stock.bot.command.CommandDispatcher;
import com.stock.bot.model.BotMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Telegram Bot Webhook处理器
 * 对应Python版本的 bot/platforms/ Telegram处理
 */
@RestController
@RequestMapping("/bot/telegram")
public class TelegramBotHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotHandler.class);
    private final CommandDispatcher dispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramBotHandler(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleUpdate(@RequestBody String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode message = root.path("message");
            if (message.isMissingNode()) return ResponseEntity.ok(Map.of("ok", true));

            String text = message.path("text").asText("");
            String chatId = message.path("chat").path("id").asText("");
            String firstName = message.path("from").path("first_name").asText("用户");
            String userId = message.path("from").path("id").asText("");

            if (text.isEmpty()) return ResponseEntity.ok(Map.of("ok", true));

            BotMessage botMsg = new BotMessage(text, "telegram");
            botMsg.setSenderId(userId);
            botMsg.setSenderName(firstName);
            botMsg.setGroupId(chatId);

            String reply = dispatcher.dispatch(botMsg);
            // Telegram需要主动发送回复(通过sendMessage API)
            log.info("Telegram消息处理: {} -> 回复长度: {}", text.substring(0, Math.min(20, text.length())), reply.length());

            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("Telegram Webhook处理失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }
}
