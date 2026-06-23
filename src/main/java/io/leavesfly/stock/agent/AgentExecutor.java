package io.leavesfly.stock.agent;

import io.leavesfly.stock.agent.tools.AgentToolRegistry;
import io.leavesfly.stock.llm.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent执行器 - ReAct循环
 * 
 * 对应Python版本的 src/agent/executor.py
 * 实现: 思考 → 行动 → 观察 → 思考 循环
 * 支持LLM Tool Calling/Function Calling
 */
@Component
public class AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);
    private final LlmService llmService;
    private final AgentToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 最大迭代次数(防止无限循环) */
    private static final int MAX_ITERATIONS = 10;

    public AgentExecutor(LlmService llmService, AgentToolRegistry toolRegistry) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行Agent推理循环
     *
     * @param userQuery  用户问题
     * @param systemPrompt 系统提示
     * @return Agent最终回答
     */
    public AgentResult execute(String userQuery, String systemPrompt) {
        log.info("Agent开始执行: {}", userQuery.substring(0, Math.min(50, userQuery.length())));
        long startTime = System.currentTimeMillis();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userQuery));

        List<String> toolCalls = new ArrayList<>();
        int iterations = 0;

        while (iterations < MAX_ITERATIONS) {
            iterations++;
            String response = llmService.chatWithMessages(messages);

            // 检查是否包含工具调用(简单解析)
            if (response.contains("[TOOL_CALL]")) {
                // 解析工具调用
                String toolCall = extractToolCall(response);
                if (toolCall != null) {
                    Map<String, Object> parsed = parseToolCall(toolCall);
                    if (parsed != null) {
                        String toolName = (String) parsed.get("name");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = (Map<String, Object>) parsed.get("args");
                        
                        log.debug("Agent调用工具: {} args={}", toolName, args);
                        String toolResult = toolRegistry.executeTool(toolName, args != null ? args : Map.of());
                        toolCalls.add(toolName + " -> " + toolResult.substring(0, Math.min(100, toolResult.length())));

                        // 将工具结果添加到消息历史
                        messages.add(Map.of("role", "assistant", "content", response));
                        messages.add(Map.of("role", "user", "content", 
                                "[工具返回结果] " + toolName + ": " + toolResult));
                        continue;
                    }
                }
            }

            // 没有工具调用，返回最终结果
            long elapsed = System.currentTimeMillis() - startTime;
            return new AgentResult(response, iterations, toolCalls, elapsed);
        }

        // 达到最大迭代次数
        long elapsed = System.currentTimeMillis() - startTime;
        return new AgentResult("分析已完成(达到最大推理轮次)", iterations, toolCalls, elapsed);
    }

    /**
     * 提取工具调用内容
     */
    private String extractToolCall(String response) {
        int start = response.indexOf("[TOOL_CALL]");
        int end = response.indexOf("[/TOOL_CALL]");
        if (start >= 0 && end > start) {
            return response.substring(start + "[TOOL_CALL]".length(), end).trim();
        }
        return null;
    }

    /**
     * 解析工具调用JSON
     */
    private Map<String, Object> parseToolCall(String toolCall) {
        try {
            JsonNode node = objectMapper.readTree(toolCall);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", node.path("name").asText());
            if (node.has("args")) {
                result.put("args", objectMapper.convertValue(node.get("args"), Map.class));
            }
            return result;
        } catch (Exception e) {
            log.debug("解析工具调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Agent执行结果
     */
    public static class AgentResult {
        private final String response;
        private final int iterations;
        private final List<String> toolCalls;
        private final long durationMs;

        public AgentResult(String response, int iterations, List<String> toolCalls, long durationMs) {
            this.response = response;
            this.iterations = iterations;
            this.toolCalls = toolCalls;
            this.durationMs = durationMs;
        }

        public String getResponse() { return response; }
        public int getIterations() { return iterations; }
        public List<String> getToolCalls() { return toolCalls; }
        public long getDurationMs() { return durationMs; }
    }
}
