package io.leavesfly.alphaforge.application.service.chat;

import io.leavesfly.alphaforge.application.agent.kernel.AgentKernel;
import io.leavesfly.alphaforge.application.agent.kernel.AgentResult;
import io.leavesfly.alphaforge.application.agent.kernel.AgentTask;
import io.leavesfly.alphaforge.application.agent.kernel.AgentTaskType;
import io.leavesfly.alphaforge.application.agent.kernel.NextStep;
import io.leavesfly.alphaforge.application.agent.kernel.NextStepAdvisor;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import io.leavesfly.alphaforge.domain.repository.chat.ChatRepository;
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
 * 对话服务 - AI对话的应用编排与会话管理
 *
 * 职责：
 * - 会话管理CRUD（创建/查询/删除会话和消息）
 * - 对话执行委托 AgentKernel 认知轨（普通对话 + 流式对话，含工具调用循环）
 * - 消息持久化（用户消息 + assistant消息 + 会话更新）
 *
 * 流式对话通过 StreamCallback 回调通知调用方发送SSE事件，
 * 内部则通过 {@link AgentKernel.StreamListener} 与内核桥接，
 * 使Controller只需关注HTTP协议和事件推送。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmPort llmService;
    private final ChatRepository chatRepository;
    private final AgentKernel agentKernel;
    private final NextStepAdvisor nextStepAdvisor;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatService(LlmPort llmService, ChatRepository chatRepository, AgentKernel agentKernel,
                       NextStepAdvisor nextStepAdvisor) {
        this.llmService = llmService;
        this.chatRepository = chatRepository;
        this.agentKernel = agentKernel;
        this.nextStepAdvisor = nextStepAdvisor;
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

    /** 普通对话（非流式）— 经 AgentKernel 认知轨执行（含 ReAct 工具循环，无工具时回退普通对话） */
    public String chat(String message, String sessionId, List<Map<String, String>> history) {
        AgentTask task = AgentTask.of(AgentTaskType.CHAT)
                .goal(message)
                .input("history", history)
                .maxToolCalls(5)
                .build();
        AgentResult result = agentKernel.run(task);
        String reply = result.getOutput();

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
        // 认知轨：流式对话统一经 AgentKernel 执行（工具循环 + 最终回复流式输出）
        AgentTask task = AgentTask.of(AgentTaskType.CHAT)
                .goal(message)
                .input("history", history)
                .maxToolCalls(5)
                .build();

        // 先持久化用户消息
        if (sessionId != null) {
            saveUserMessage(sessionId, message);
        }

        executor.execute(() -> {
            try {
                List<String> calledTools = new ArrayList<>();
                String fullReply = agentKernel.runChatStreaming(task, new AgentKernel.StreamListener() {
                    @Override
                    public void onToolCall(String toolName, Map<String, Object> args, String result, long durationMs) {
                        calledTools.add(toolName);
                        callback.onToolCall(toolName, args, result, durationMs);
                    }

                    @Override
                    public void onChunk(String chunk) {
                        callback.onChunk(chunk);
                    }
                });

                // 持久化 assistant 消息
                if (sessionId != null) {
                    saveAssistantMessage(sessionId, fullReply);
                }

                // 链式引导：按本轮实际调用的工具给建议（done 之前下发，空建议不打扰）
                if (nextStepAdvisor != null) {
                    try {
                        List<NextStep> steps = nextStepAdvisor.adviseForChatTools(calledTools);
                        if (!steps.isEmpty()) {
                            callback.onNextSteps(steps);
                        }
                    } catch (Exception e) {
                        log.warn("next_steps 生成失败（不影响对话）: {}", e.getMessage());
                    }
                }

                callback.onComplete();
            } catch (Exception e) {
                log.error("流式对话异常: {}", e.getMessage());
                callback.onError(e.getMessage());
            }
        });
    }

    // ===== 内部方法 =====

    /** 持久化用户消息 */
    private void saveUserMessage(String sessionId, String message) {
        int existing = chatRepository.countMessagesBySessionId(sessionId);
        String msgId = UUID.randomUUID().toString().replace("-", "");
        chatRepository.insertMessage(sessionId, msgId, "user", message, null, LocalDateTime.now());
        // 首条用户消息：用其内容作为会话标题
        if (existing == 0) {
            int count = chatRepository.countMessagesBySessionId(sessionId);
            chatRepository.updateSessionActive(sessionId, count, buildSessionTitle(message), LocalDateTime.now());
        }
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
        session.put("title", buildSessionTitle(row.get("title") != null ? row.get("title").toString() : null));
        session.put("messageCount", row.get("message_count") != null ? row.get("message_count") : 0);
        Object createdAt = row.get("created_at");
        Object lastActive = row.get("last_active");
        session.put("createdAt", createdAt != null ? createdAt.toString() : null);
        session.put("lastActive", lastActive != null ? lastActive.toString() : null);
        return session;
    }

    /** 由文本生成简短的会话标题（去除多余空白并截断） */
    private String buildSessionTitle(String raw) {
        if (raw == null) return "新对话";
        String t = raw.replaceAll("\\s+", " ").trim();
        if (t.isEmpty() || "新对话".equals(t)) return "新对话";
        return t.length() > 24 ? t.substring(0, 24) + "…" : t;
    }

    /**
     * 流式对话回调接口
     */
    public interface StreamCallback {
        void onToolCall(String toolName, Map<String, Object> args, String result, long durationMs);
        void onChunk(String chunk);

        /** 链式引导建议（done 之前回调；default 空实现保证既有回调向后兼容） */
        default void onNextSteps(List<NextStep> steps) {
        }

        void onComplete();
        void onError(String error);
    }
}
