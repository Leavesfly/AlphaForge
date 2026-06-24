package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.infrastructure.llm.LlmService;
import io.leavesfly.stock.infrastructure.persistence.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Agent对话API控制器
 * 
 * 对应Python版本的 api/v1/endpoints/agent.py
 * 提供AI对话和交互式分析能力
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final LlmService llmService;
    private final AppConfig config;
    private final ChatRepository chatRepository;

    /** 内存缓存(加速LLM上下文构建,同时持久化到DB) */
    private final Map<String, List<Map<String, String>>> sessions = new LinkedHashMap<>();

    public AgentController(LlmService llmService, AppConfig config, ChatRepository chatRepository) {
        this.llmService = llmService;
        this.config = config;
        this.chatRepository = chatRepository;
    }

    /**
     * Agent对话
     * POST /api/v1/agent/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        String message = (String) request.getOrDefault("message", "");
        String sessionId = (String) request.getOrDefault("session_id", UUID.randomUUID().toString());

        if (message.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message不能为空"));
        }

        // 获取或创建会话
        List<Map<String, String>> history = getOrCreateSession(sessionId);

        // 添加用户消息
        history.add(Map.of("role", "user", "content", message));
        persistMessage(sessionId, "user", message);

        // 调用LLM
        String reply = llmService.chatWithMessages(history);
        
        // 添加助手回复到历史
        history.add(Map.of("role", "assistant", "content", reply));
        persistMessage(sessionId, "assistant", reply);

        // 限制内存中会话历史长度
        while (history.size() > 21) {
            history.remove(1);
        }

        // 更新会话元数据
        updateSessionMeta(sessionId, message, history.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reply", reply);
        response.put("session_id", sessionId);
        response.put("model", config.getLlmModel());
        return ResponseEntity.ok(response);
    }

    /**
     * Agent流式对话 (SSE)
     * POST /api/v1/agent/chat/stream
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> request) {
        String message = (String) request.getOrDefault("message", "");
        String sessionId = (String) request.getOrDefault("session_id", UUID.randomUUID().toString());

        SseEmitter emitter = new SseEmitter(180_000L); // 3分钟超时

        if (message.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("message不能为空"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // 获取或创建会话
        List<Map<String, String>> history = getOrCreateSession(sessionId);
        history.add(Map.of("role", "user", "content", message));
        persistMessage(sessionId, "user", message);

        // 异步执行流式LLM调用
        new Thread(() -> {
            try {
                String fullReply = llmService.streamChatWithMessages(history, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
                    } catch (IOException e) {
                        log.warn("发送SSE chunk失败: {}", e.getMessage());
                    }
                });

                // 流结束，发送done事件
                emitter.send(SseEmitter.event().name("done").data(sessionId));
                emitter.complete();

                // 更新会话历史
                history.add(Map.of("role", "assistant", "content", fullReply));
                persistMessage(sessionId, "assistant", fullReply);
                while (history.size() > 21) {
                    history.remove(1);
                }
                updateSessionMeta(sessionId, message, history.size());
            } catch (Exception e) {
                log.error("流式对话失败: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data("分析失败: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }, "sse-chat-" + sessionId).start();

        return emitter;
    }

    /**
     * 获取可用模型列表
     * GET /api/v1/agent/models
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getModels() {
        List<Map<String, Object>> models = new ArrayList<>();
        for (AppConfig.LlmChannelConfig channel : config.getLlmChannels()) {
            models.add(Map.of(
                    "model", channel.getModel(),
                    "provider", channel.getProvider() != null ? channel.getProvider() : "unknown"
            ));
        }
        return ResponseEntity.ok(Map.of("models", models, "current", config.getLlmModel()));
    }

    /**
     * 获取技能列表
     * GET /api/v1/agent/skills
     */
    @GetMapping("/skills")
    public ResponseEntity<Map<String, Object>> getSkills() {
        List<Map<String, String>> skills = List.of(
            Map.of("id", "chan_theory", "name", "缠论", "description", "缠论分析框架"),
            Map.of("id", "wave_theory", "name", "波浪理论", "description", "艾略特波浪分析"),
            Map.of("id", "bull_trend", "name", "牛趋势", "description", "趋势跟踪分析"),
            Map.of("id", "emotion_cycle", "name", "情绪周期", "description", "市场情绪分析"),
            Map.of("id", "box_oscillation", "name", "箱体震荡", "description", "箱体突破分析"),
            Map.of("id", "shrink_pullback", "name", "缩量回调", "description", "缩量回调买入策略")
        );
        return ResponseEntity.ok(Map.of("skills", skills, "default_skill_id", "bull_trend"));
    }

    /**
     * 获取会话列表
     * GET /api/v1/agent/chat/sessions
     */
    @GetMapping("/chat/sessions")
    public ResponseEntity<Map<String, Object>> getChatSessions(@RequestParam(defaultValue = "50") int limit) {
        try {
            List<Map<String, Object>> sessionList = chatRepository.findSessions(limit);
            return ResponseEntity.ok(Map.of("sessions", sessionList != null ? sessionList : List.of()));
        } catch (Exception e) {
            log.warn("查询会话列表失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("sessions", List.of()));
        }
    }

    /**
     * 获取会话消息
     * GET /api/v1/agent/chat/sessions/{sessionId}
     */
    @GetMapping("/chat/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionMessages(@PathVariable String sessionId) {
        try {
            List<Map<String, Object>> dbMessages = chatRepository.findMessagesBySessionId(sessionId);
            if (dbMessages == null || dbMessages.isEmpty()) {
                return ResponseEntity.ok(Map.of("messages", List.of()));
            }
            List<Map<String, Object>> messages = new ArrayList<>();
            for (Map<String, Object> msg : dbMessages) {
                String role = (String) msg.getOrDefault("role", "user");
                // 跳过system消息
                if ("system".equals(role)) continue;
                messages.add(Map.of(
                    "id", String.valueOf(msg.getOrDefault("id", "")),
                    "role", role,
                    "content", String.valueOf(msg.getOrDefault("content", "")),
                    "created_at", String.valueOf(msg.getOrDefault("created_at", ""))
                ));
            }
            return ResponseEntity.ok(Map.of("messages", messages));
        } catch (Exception e) {
            log.warn("查询会话消息失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("messages", List.of()));
        }
    }

    /**
     * 删除会话
     * DELETE /api/v1/agent/chat/sessions/{sessionId}
     */
    @DeleteMapping("/chat/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        sessions.remove(sessionId);
        try {
            chatRepository.deleteMessagesBySessionId(sessionId);
            chatRepository.deleteSession(sessionId);
        } catch (Exception e) {
            log.warn("删除会话持久化失败: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("status", "deleted", "session_id", sessionId));
    }

    /**
     * 快速发送(非流式)
     * POST /api/v1/agent/chat/send
     */
    @PostMapping("/chat/send")
    public ResponseEntity<Map<String, Object>> sendChat(@RequestBody Map<String, Object> request) {
        String content = (String) request.getOrDefault("content", "");
        if (content.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "content不能为空"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ===== 私有辅助方法 =====

    private static final String SYSTEM_PROMPT = "你是一个专业的股票分析助手。你可以回答关于股票分析、技术指标、投资策略等相关问题。请使用Markdown格式回复。";

    /** 获取或创建会话(优先从内存,其次从DB加载) */
    private List<Map<String, String>> getOrCreateSession(String sessionId) {
        List<Map<String, String>> history = sessions.get(sessionId);
        if (history != null) return history;

        // 尝试从DB恢复
        history = new ArrayList<>();
        history.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        try {
            List<Map<String, Object>> dbMessages = chatRepository.findMessagesBySessionId(sessionId);
            if (dbMessages != null && !dbMessages.isEmpty()) {
                for (Map<String, Object> msg : dbMessages) {
                    String role = (String) msg.getOrDefault("role", "user");
                    if ("system".equals(role)) continue;
                    history.add(Map.of("role", role, "content", String.valueOf(msg.getOrDefault("content", ""))));
                }
            } else {
                // 新会话,插入DB
                chatRepository.insertSession(sessionId, "新对话", LocalDateTime.now());
            }
        } catch (Exception e) {
            log.warn("加载会话历史失败: {}", e.getMessage());
            chatRepository.insertSession(sessionId, "新对话", LocalDateTime.now());
        }

        // 只保留最近的上下文给LLM
        if (history.size() > 21) {
            List<Map<String, String>> trimmed = new ArrayList<>();
            trimmed.add(history.get(0)); // system
            trimmed.addAll(history.subList(history.size() - 20, history.size()));
            history = trimmed;
        }
        sessions.put(sessionId, history);
        return history;
    }

    /** 持久化单条消息到DB */
    private void persistMessage(String sessionId, String role, String content) {
        try {
            chatRepository.insertMessage(sessionId, UUID.randomUUID().toString(), role, content, null, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("持久化消息失败: {}", e.getMessage());
        }
    }

    /** 更新会话元数据(标题、消息数等) */
    private void updateSessionMeta(String sessionId, String firstUserMsg, int messageCount) {
        try {
            String title = firstUserMsg.length() > 30 ? firstUserMsg.substring(0, 30) : firstUserMsg;
            chatRepository.updateSessionActive(sessionId, messageCount, title, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("更新会话元数据失败: {}", e.getMessage());
        }
    }
}
