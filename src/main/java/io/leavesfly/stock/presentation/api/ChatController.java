package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.service.chat.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;

/**
 * AI对话 API
 *
 * Controller只负责HTTP协议处理和SSE事件推送，
 * 业务逻辑（会话管理、对话执行、消息持久化）下沉到ChatService。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // ===== 会话管理 =====

    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> listSessions() {
        return ResponseEntity.ok(chatService.listSessions());
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody(required = false) Map<String, Object> body) {
        String title = (body != null && body.get("title") != null) ? body.get("title").toString() : null;
        return ResponseEntity.ok(chatService.createSession(title));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<?> getSessionMessages(@PathVariable String sessionId) {
        if (!chatService.sessionExists(sessionId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(chatService.getMessages(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ===== 对话 =====

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        String sessionId = body.get("sessionId") != null ? body.get("sessionId").toString() : null;
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");

        String reply = chatService.chat(message, sessionId, history);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("model", "ai-assistant");
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(300_000L);

        String message = (String) body.get("message");
        if (message == null || message.isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"message is required\"}"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        String sessionId = body.get("sessionId") != null ? body.get("sessionId").toString() : null;
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");

        chatService.chatStream(message, sessionId, history, new ChatService.StreamCallback() {
            @Override
            public void onToolCall(String toolName, Map<String, Object> args, String result, long durationMs) {
                Map<String, Object> toolInfo = new LinkedHashMap<>();
                toolInfo.put("name", toolName);
                toolInfo.put("args", args);
                toolInfo.put("result", result.length() > 500 ? result.substring(0, 500) + "..." : result);
                toolInfo.put("durationMs", durationMs);
                try {
                    emitter.send(SseEmitter.event().name("tool").data(objectMapper.writeValueAsString(toolInfo)));
                } catch (IOException e) {
                    log.debug("SSE发送工具事件失败: {}", e.getMessage());
                }
            }

            @Override
            public void onChunk(String chunk) {
                try {
                    emitter.send(SseEmitter.event().name("chunk").data(chunk));
                } catch (IOException e) {
                    log.debug("SSE发送失败(客户端可能已断开): {}", e.getMessage());
                }
            }

            @Override
            public void onComplete() {
                try {
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                } catch (IOException e) {
                    log.debug("SSE完成事件发送失败: {}", e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(error));
                } catch (IOException ignored) {}
                emitter.completeWithError(new RuntimeException(error));
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onCompletion(() -> log.debug("SSE连接完成"));
        return emitter;
    }
}
