package com.stock.api.controller;

import com.stock.config.AppConfig;
import com.stock.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * 清除会话
     * DELETE /api/v1/agent/session/{sessionId}
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> clearSession(@PathVariable String sessionId) {
        sessions.remove(sessionId);
        return ResponseEntity.ok(Map.of("status", "cleared", "session_id", sessionId));
    }
}
