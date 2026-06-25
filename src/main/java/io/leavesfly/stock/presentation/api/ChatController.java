package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.infrastructure.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI对话 API
 * 支持普通JSON响应和SSE流式输出
 */
@RestController
@RequestMapping("/api/v1/agent")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final LlmService llmService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final String SYSTEM_PROMPT = """
            你是一位专业的AI股票分析助手。你可以帮用户：
            • 分析个股（技术面/基本面/舆情）
            • 解读行情与市场动向
            • 运行策略回测
            • 设置价格告警
            • 回答投资相关问题
            请用中文回复，回答要专业、简洁、有数据支撑。
            """;

    public ChatController(LlmService llmService) {
        this.llmService = llmService;
    }

    /** AI对话 - 普通JSON响应 */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");

        List<Map<String, String>> messages = buildMessages(message, history);
        String reply = llmService.chatWithMessages(messages);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("model", "ai-assistant");
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    /** AI对话 - SSE流式输出 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(120_000L); // 120秒超时

        String message = (String) body.get("message");
        if (message == null || message.isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"message is required\"}"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");

        List<Map<String, String>> messages = buildMessages(message, history);

        executor.execute(() -> {
            try {
                llmService.streamChatWithMessages(messages, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
                    } catch (IOException e) {
                        log.debug("SSE发送失败(客户端可能已断开): {}", e.getMessage());
                    }
                });
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式对话异常: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onCompletion(() -> log.debug("SSE连接完成"));
        return emitter;
    }

    private List<Map<String, String>> buildMessages(String userMessage, List<Map<String, String>> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        // 加入历史消息
        if (history != null && !history.isEmpty()) {
            for (Map<String, String> msg : history) {
                if (msg.containsKey("role") && msg.containsKey("content")) {
                    messages.add(msg);
                }
            }
        }

        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }
}
