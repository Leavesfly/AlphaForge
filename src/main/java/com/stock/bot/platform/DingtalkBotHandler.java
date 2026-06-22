package com.stock.bot.platform;

import com.stock.bot.command.CommandDispatcher;
import com.stock.bot.model.BotMessage;
import com.stock.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 钉钉Bot Webhook处理器
 * 对应Python版本的 bot/platforms/dingtalk.py
 */
@RestController
@RequestMapping("/bot/dingtalk")
public class DingtalkBotHandler {

    private static final Logger log = LoggerFactory.getLogger(DingtalkBotHandler.class);
    private final CommandDispatcher dispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DingtalkBotHandler(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String msgType = root.path("msgtype").asText("text");
            String content = "";
            
            if ("text".equals(msgType)) {
                content = root.path("text").path("content").asText("").trim();
            }
            
            String senderId = root.path("senderStaffId").asText("");
            String senderNick = root.path("senderNick").asText("用户");

            if (content.isEmpty()) return ResponseEntity.ok(Map.of("msgtype", "empty"));

            BotMessage botMsg = new BotMessage(content, "dingtalk");
            botMsg.setSenderId(senderId);
            botMsg.setSenderName(senderNick);

            String reply = dispatcher.dispatch(botMsg);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("msgtype", "text");
            response.put("text", Map.of("content", reply));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("钉钉Webhook处理失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("msgtype", "text", "text", Map.of("content", "处理失败: " + e.getMessage())));
        }
    }
}
