package io.leavesfly.alphaforge.application.service.chat;

import io.leavesfly.alphaforge.application.agent.LlmToolAdapter;
import io.leavesfly.alphaforge.application.agent.ReActAgent;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import io.leavesfly.alphaforge.infrastructure.persistence.chat.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 对话服务 - AI对话的核心业务逻辑
 *
 * 从ChatController下沉的职责：
 * - 会话管理CRUD（创建/查询/删除会话和消息）
 * - 消息构建（system prompt + history + user）
 * - 对话执行（普通对话 + 流式对话含工具调用循环）
 * - 消息持久化（用户消息 + assistant消息 + 会话更新）
 *
 * 流式对话通过 StreamCallback 回调通知调用方发送SSE事件，
 * 使Controller只需关注HTTP协议和事件推送。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmPort llmService;
    private final ChatRepository chatRepository;
    private final ReActAgent reactAgent;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatService(LlmPort llmService, ChatRepository chatRepository, ReActAgent reactAgent) {
        this.llmService = llmService;
        this.chatRepository = chatRepository;
        this.reactAgent = reactAgent;
    }

    /** 优雅关闭线程池，避免应用停止时线程泄漏 */
    @PreDestroy
    public void shutdown() {
        log.info("ChatService 正在关闭线程池...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("ChatService 线程池强制关闭（仍有任务未完成）");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ===== 会话管理 =====

    /** 获取会话列表 */
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> sessions = chatRepository.findSessions(100);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : sessions) {
            result.add(formatSession(row));
        }
        return result;
    }

    /** 创建新会话 */
    public Map<String, Object> createSession(String title) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        if (title == null || title.isBlank()) title = "新对话";
        LocalDateTime now = LocalDateTime.now();
        chatRepository.insertSession(sessionId, title, now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("title", title);
        result.put("messageCount", 0);
        result.put("createdAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.put("lastActive", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return result;
    }

    /** 获取会话消息列表 */
    public List<Map<String, Object>> getMessages(String sessionId) {
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
        return messages;
    }

    /** 检查会话是否存在 */
    public boolean sessionExists(String sessionId) {
        return chatRepository.findSessionById(sessionId) != null;
    }

    /** 删除会话（含消息） */
    public void deleteSession(String sessionId) {
        chatRepository.deleteMessagesBySessionId(sessionId);
        chatRepository.deleteSession(sessionId);
    }

    // ===== 对话 =====

    /** 普通对话（非流式，无工具调用） */
    public String chat(String message, String sessionId, List<Map<String, String>> history) {
        List<Map<String, String>> messages = buildMessages(message, history);
        String reply = llmService.chatWithMessages(messages);

        if (sessionId != null) {
            saveUserMessage(sessionId, message);
            saveAssistantMessage(sessionId, reply);
        }
        return reply;
    }

    /** 简单对话（无会话持久化，供 Bot 等无状态场景使用） */
    public String chat(String systemPrompt, String userMessage) {
        return llmService.chat(systemPrompt, userMessage);
    }

    /**
     * 流式对话（含工具调用循环，通过回调通知调用方）
     *
     * 异步执行，调用方通过 StreamCallback 接收：
     * - onToolCall: 工具调用事件
     * - onChunk: 文本片段
     * - onComplete: 完成
     * - onError: 错误
     */
    public void chatStream(String message, String sessionId, List<Map<String, String>> history,
                           StreamCallback callback) {
        List<Map<String, String>> messages = buildMessages(message, history);

        // 先持久化用户消息
        if (sessionId != null) {
            saveUserMessage(sessionId, message);
        }

        executor.execute(() -> {
            StringBuilder fullReply = new StringBuilder();
            try {
                // 阶段1: 工具调用循环（非流式），通过回调发送 tool 事件
                List<Map<String, String>> workingMessages = new ArrayList<>(messages);
                LlmToolAdapter.ToolCallSession toolSession = reactAgent.execute(workingMessages, 5,
                        (toolName, args, result, durationMs) -> callback.onToolCall(toolName, args, result, durationMs));

                // 有工具调用时分块发送，没有工具调用时走真正的流式 API
                String response = toolSession.getFinalResponse();
                if (response != null) {
                    // 工具调用后的最终回复，分块发送模拟流式
                    fullReply.append(response);
                    int chunkSize = 8;
                    for (int i = 0; i < response.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, response.length());
                        callback.onChunk(response.substring(i, end));
                    }
                } else {
                    // 没有工具调用，走真正的流式 API（逐字输出）
                    llmService.streamChatWithMessages(workingMessages, chunk -> {
                        fullReply.append(chunk);
                        callback.onChunk(chunk);
                    });
                }

                // 持久化 assistant 消息
                if (sessionId != null) {
                    saveAssistantMessage(sessionId, fullReply.toString());
                }

                callback.onComplete();
            } catch (Exception e) {
                log.error("流式对话异常: {}", e.getMessage());
                callback.onError(e.getMessage());
            }
        });
    }

    // ===== 内部方法 =====

    /** 构建消息列表（system prompt + history + user） */
    private List<Map<String, String>> buildMessages(String userMessage, List<Map<String, String>> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", reactAgent.buildSystemPrompt()));
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

    /** 持久化用户消息 */
    private void saveUserMessage(String sessionId, String message) {
        String msgId = UUID.randomUUID().toString().replace("-", "");
        chatRepository.insertMessage(sessionId, msgId, "user", message, null, LocalDateTime.now());
    }

    /** 持久化assistant消息并更新会话 */
    private void saveAssistantMessage(String sessionId, String reply) {
        String msgId = UUID.randomUUID().toString().replace("-", "");
        chatRepository.insertMessage(sessionId, msgId, "assistant", reply, null, LocalDateTime.now());
        int count = chatRepository.countMessagesBySessionId(sessionId);
        chatRepository.updateSessionActive(sessionId, count, null, LocalDateTime.now());
    }

    /** 格式化会话信息 */
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

    /**
     * 流式对话回调接口
     */
    public interface StreamCallback {
        void onToolCall(String toolName, Map<String, Object> args, String result, long durationMs);
        void onChunk(String chunk);
        void onComplete();
        void onError(String error);
    }
}
