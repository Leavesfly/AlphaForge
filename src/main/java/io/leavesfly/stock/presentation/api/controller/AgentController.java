package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.infrastructure.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    /** 会话管理(简单内存实现) */
    private final Map<String, List<Map<String, String>>> sessions = new LinkedHashMap<>();

    public AgentController(LlmService llmService, AppConfig config) {
        this.llmService = llmService;
        this.config = config;
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
        List<Map<String, String>> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        
        // 添加系统提示(如果是新会话)
        if (history.isEmpty()) {
            history.add(Map.of("role", "system", "content", 
                    "你是一个专业的股票分析助手。你可以回答关于股票分析、技术指标、投资策略等相关问题。"));
        }

        // 添加用户消息
        history.add(Map.of("role", "user", "content", message));

        // 调用LLM
        String reply = llmService.chatWithMessages(history);
        
        // 添加助手回复到历史
        history.add(Map.of("role", "assistant", "content", reply));

        // 限制会话历史长度
        while (history.size() > 21) { // system + 10轮对话
            history.remove(1); // 保留system，移除最早的对话
        }

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
        List<Map<String, String>> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        if (history.isEmpty()) {
            history.add(Map.of("role", "system", "content",
                    "你是一个专业的股票分析助手。你可以回答关于股票分析、技术指标、投资策略等相关问题。"));
        }
        history.add(Map.of("role", "user", "content", message));

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
                while (history.size() > 21) {
                    history.remove(1);
                }
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
        List<Map<String, Object>> sessionList = new ArrayList<>();
        sessions.forEach((id, history) -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("session_id", id);
            info.put("title", history.size() > 1 ? history.get(1).getOrDefault("content", "新对话").toString().substring(0, Math.min(30, history.get(1).getOrDefault("content", "").toString().length())) : "新对话");
            info.put("message_count", history.size());
            info.put("created_at", null);
            info.put("last_active", null);
            sessionList.add(info);
        });
        if (sessionList.size() > limit) sessionList.subList(limit, sessionList.size()).clear();
        return ResponseEntity.ok(Map.of("sessions", sessionList));
    }

    /**
     * 获取会话消息
     * GET /api/v1/agent/chat/sessions/{sessionId}
     */
    @GetMapping("/chat/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionMessages(@PathVariable String sessionId) {
        List<Map<String, String>> history = sessions.get(sessionId);
        if (history == null) return ResponseEntity.notFound().build();
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 1; i < history.size(); i++) {
            Map<String, String> msg = history.get(i);
            messages.add(Map.of(
                "id", String.valueOf(i),
                "role", msg.getOrDefault("role", "user"),
                "content", msg.getOrDefault("content", ""),
                "created_at", ""
            ));
        }
        return ResponseEntity.ok(Map.of("messages", messages));
    }

    /**
     * 删除会话
     * DELETE /api/v1/agent/chat/sessions/{sessionId}
     */
    @DeleteMapping("/chat/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        sessions.remove(sessionId);
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
}
