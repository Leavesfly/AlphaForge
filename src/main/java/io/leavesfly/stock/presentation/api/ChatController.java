package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.agent.LlmToolAdapter;
import io.leavesfly.stock.application.agent.skills.SkillsLoader;
import io.leavesfly.stock.application.agent.tools.ToolRegistry;
import io.leavesfly.stock.infrastructure.llm.LlmService;
import io.leavesfly.stock.infrastructure.persistence.ChatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI对话 API
 * 支持普通JSON响应和SSE流式输出
 * 支持会话管理与消息持久化
 */
@RestController
@RequestMapping("/api/v1/agent")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final LlmService llmService;
    private final ChatRepository chatRepository;
    private final LlmToolAdapter llmToolAdapter;
    private final ToolRegistry toolRegistry;
    private final SkillsLoader skillsLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一位专业的AI股票分析助手。你可以帮用户：
            • 分析个股（技术面/基本面/舆情）
            • 解读行情与市场动向
            • 运行策略回测
            • 设置价格告警
            • 回答投资相关问题
            请用中文回复，回答要专业、简洁、有数据支撑。

            当需要获取实时数据时，你可以使用工具调用。格式为：
            [TOOL_CALL]{"name":"工具名","args":{参数}}[/TOOL_CALL]

            可用工具：
            %s

            每次只调用一个工具，收到工具结果后再给出最终分析。

            ## 技能（Skills）
            以下技能扩展了你的分析能力。当用户任务与某个技能描述匹配时，
            先调用 skills(action='invoke', name='技能名') 获取执行指令，再按指令调用其他工具完成分析。

            %s

            ## 技能使用规则
            1. 当用户任务与某个技能的 description 高度匹配时，主动调用 skills(action='invoke', name='技能名')
            2. 加载技能后，严格按照技能指令中的步骤执行
            3. 如果没有匹配的技能，直接使用工具回答用户问题
            4. 遇到无法处理的任务时，可使用 skills(action='install', repo='owner/repo') 从GitHub安装新技能
            5. 可使用 skills(action='remove', name='技能名') 删除不需要的已安装技能
            """;

    public ChatController(LlmService llmService, ChatRepository chatRepository,
                          LlmToolAdapter llmToolAdapter, ToolRegistry toolRegistry,
                          SkillsLoader skillsLoader) {
        this.llmService = llmService;
        this.chatRepository = chatRepository;
        this.llmToolAdapter = llmToolAdapter;
        this.toolRegistry = toolRegistry;
        this.skillsLoader = skillsLoader;
    }

    /** 动态生成System Prompt（工具列表+技能摘要动态注入） */
    private String buildSystemPrompt() {
        return String.format(SYSTEM_PROMPT_TEMPLATE,
                toolRegistry.getToolSummaryText(),
                skillsLoader.buildSkillsSummary());
    }

    // ===== 会话管理 API =====

    /** 获取会话列表 */
    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> listSessions() {
        List<Map<String, Object>> sessions = chatRepository.findSessions(100);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : sessions) {
            result.add(formatSession(row));
        }
        return ResponseEntity.ok(result);
    }

    /** 创建新会话 */
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody(required = false) Map<String, Object> body) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String title = (body != null && body.get("title") != null) ? body.get("title").toString() : "新对话";
        if (title.isBlank()) title = "新对话";
        LocalDateTime now = LocalDateTime.now();

        chatRepository.insertSession(sessionId, title, now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("title", title);
        result.put("messageCount", 0);
        result.put("createdAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.put("lastActive", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return ResponseEntity.ok(result);
    }

    /** 获取会话消息列表 */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<?> getSessionMessages(@PathVariable String sessionId) {
        Map<String, Object> session = chatRepository.findSessionById(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> rawMessages = chatRepository.findMessagesBySessionId(sessionId);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, Object> row : rawMessages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", row.get("role"));
            msg.put("content", row.get("content"));
            Object createdAt = row.get("created_at");
            msg.put("createdAt", createdAt != null ? createdAt.toString() : null);
            messages.add(msg);
        }
        return ResponseEntity.ok(messages);
    }

    /** 删除会话（含消息） */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        chatRepository.deleteMessagesBySessionId(sessionId);
        chatRepository.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ===== 对话 API =====

    /** AI对话 - 普通JSON响应 */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }

        String sessionId = body.get("sessionId") != null ? body.get("sessionId").toString() : null;
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");

        List<Map<String, String>> messages = buildMessages(message, history);
        String reply = llmService.chatWithMessages(messages);

        // 持久化
        if (sessionId != null) {
            LocalDateTime now = LocalDateTime.now();
            String userMsgId = UUID.randomUUID().toString().replace("-", "");
            String assistantMsgId = UUID.randomUUID().toString().replace("-", "");
            chatRepository.insertMessage(sessionId, userMsgId, "user", message, null, now);
            chatRepository.insertMessage(sessionId, assistantMsgId, "assistant", reply, null, now);
            int count = chatRepository.countMessagesBySessionId(sessionId);
            String title = generateTitle(message);
            chatRepository.updateSessionActive(sessionId, count, title, LocalDateTime.now());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("model", "ai-assistant");
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    /** AI对话 - SSE流式输出 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(300_000L); // 300秒超时（工具调用可能耗时较长）

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

        List<Map<String, String>> messages = buildMessages(message, history);

        // 先持久化用户消息
        if (sessionId != null) {
            LocalDateTime now = LocalDateTime.now();
            String userMsgId = UUID.randomUUID().toString().replace("-", "");
            chatRepository.insertMessage(sessionId, userMsgId, "user", message, null, now);
        }

        executor.execute(() -> {
            StringBuilder fullReply = new StringBuilder();
            try {
                // 阶段1: 工具调用循环（非流式），通过 SSE 发送 tool 事件
                List<Map<String, String>> workingMessages = new ArrayList<>(messages);
                LlmToolAdapter.ToolCallSession toolSession = llmToolAdapter.executeToolLoop(workingMessages, 5, (toolName, args, result, durationMs) -> {
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
                });

                // 有工具调用时分块发送，没有工具调用时走真正的流式 API
                String response = toolSession.getFinalResponse();
                if (response != null) {
                    // 工具调用后的最终回复，分块发送模拟流式
                    fullReply.append(response);
                    int chunkSize = 8;
                    for (int i = 0; i < response.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, response.length());
                        String chunk = response.substring(i, end);
                        try {
                            emitter.send(SseEmitter.event().name("chunk").data(chunk));
                        } catch (IOException e) {
                            log.debug("SSE发送失败(客户端可能已断开): {}", e.getMessage());
                        }
                    }
                } else {
                    // 没有工具调用，走真正的流式 API（逐字输出）
                    llmService.streamChatWithMessages(workingMessages, chunk -> {
                        fullReply.append(chunk);
                        try {
                            emitter.send(SseEmitter.event().name("chunk").data(chunk));
                        } catch (IOException e) {
                            log.debug("SSE发送失败(客户端可能已断开): {}", e.getMessage());
                        }
                    });
                }

                // 持久化 assistant 消息
                if (sessionId != null) {
                    String assistantMsgId = UUID.randomUUID().toString().replace("-", "");
                    chatRepository.insertMessage(sessionId, assistantMsgId, "assistant", fullReply.toString(), null, LocalDateTime.now());
                    int count = chatRepository.countMessagesBySessionId(sessionId);
                    String title = generateTitle(message);
                    chatRepository.updateSessionActive(sessionId, count, title, LocalDateTime.now());
                }

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

    // ===== 辅助方法 =====

    private List<Map<String, String>> buildMessages(String userMessage, List<Map<String, String>> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt()));

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

    /** 根据用户首条消息生成会话标题 */
    private String generateTitle(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return "新对话";
        String title = userMessage.trim();
        if (title.length() > 30) {
            title = title.substring(0, 30) + "...";
        }
        return title;
    }

    /** 格式化会话信息返回给前端 */
    private Map<String, Object> formatSession(Map<String, Object> row) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("sessionId", row.get("session_id"));
        session.put("title", row.get("title") != null ? row.get("title") : "新对话");
        session.put("messageCount", row.get("message_count") != null ? row.get("message_count") : 0);
        Object createdAt = row.get("created_at");
        Object lastActive = row.get("last_active");
        session.put("createdAt", createdAt != null ? createdAt.toString() : null);
        session.put("lastActive", lastActive != null ? lastActive.toString() : null);
        return session;
    }
}
