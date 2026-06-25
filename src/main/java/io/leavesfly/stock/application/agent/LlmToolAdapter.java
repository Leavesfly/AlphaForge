package io.leavesfly.stock.application.agent;

import io.leavesfly.stock.infrastructure.llm.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.stock.application.agent.tools.AgentToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LLM工具调用适配器
 *
 * 实现OpenAI Function Calling格式的工具调用解析和执行
 */
@Component
public class LlmToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(LlmToolAdapter.class);
    private final LlmService llmService;
    private final AgentToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmToolAdapter(LlmService llmService, AgentToolRegistry toolRegistry) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行带工具调用的LLM对话
     * 支持OpenAI Function Calling协议
     *
     * @param messages     消息历史
     * @param maxToolCalls 最大工具调用次数
     * @return 最终回复
     */
    public ToolCallResult chatWithTools(List<Map<String, String>> messages, int maxToolCalls) {
        List<String> toolCallLog = new ArrayList<>();
        int toolCalls = 0;

        while (toolCalls < maxToolCalls) {
            String response = llmService.chatWithMessages(messages);

            // 检测工具调用
            List<ToolCall> calls = parseToolCalls(response);
            if (calls.isEmpty()) {
                // 没有工具调用，返回最终回复
                return new ToolCallResult(response, toolCallLog, toolCalls);
            }

            // 执行工具调用
            for (ToolCall call : calls) {
                toolCalls++;
                log.debug("执行工具调用 #{}: {}({})", toolCalls, call.name, call.arguments);
                String result = toolRegistry.executeTool(call.name, call.arguments);
                toolCallLog.add(call.name + " → " + truncate(result, 200));

                // 将工具结果注入消息历史
                messages.add(Map.of("role", "assistant", "content", response));
                messages.add(Map.of("role", "tool", "content",
                        String.format("[%s 调用结果]\n%s", call.name, result)));
            }
        }

        // 超出最大工具调用次数，请求最终总结
        messages.add(Map.of("role", "user", "content", "请根据以上所有工具调用结果，给出最终综合分析结论。"));
        String finalResponse = llmService.chatWithMessages(messages);
        return new ToolCallResult(finalResponse, toolCallLog, toolCalls);
    }

    /**
     * 解析LLM回复中的工具调用
     * 支持多种格式: JSON格式、标记格式
     */
    private List<ToolCall> parseToolCalls(String response) {
        List<ToolCall> calls = new ArrayList<>();

        // 格式1: [TOOL_CALL]{"name":"xxx","args":{}}[/TOOL_CALL]
        int idx = 0;
        while (true) {
            int start = response.indexOf("[TOOL_CALL]", idx);
            if (start < 0) break;
            int end = response.indexOf("[/TOOL_CALL]", start);
            if (end < 0) break;

            String json = response.substring(start + "[TOOL_CALL]".length(), end).trim();
            ToolCall call = parseToolCallJson(json);
            if (call != null) calls.add(call);
            idx = end + "[/TOOL_CALL]".length();
        }

        // 格式2: ```tool_call\n{"name":"xxx","arguments":{}}```
        if (calls.isEmpty()) {
            int tcStart = response.indexOf("```tool_call");
            if (tcStart >= 0) {
                int tcEnd = response.indexOf("```", tcStart + 12);
                if (tcEnd > tcStart) {
                    String json = response.substring(tcStart + 12, tcEnd).trim();
                    ToolCall call = parseToolCallJson(json);
                    if (call != null) calls.add(call);
                }
            }
        }

        return calls;
    }

    private ToolCall parseToolCallJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String name = node.path("name").asText(node.path("function").asText(""));
            if (name.isEmpty()) return null;
            
            Map<String, Object> args = new LinkedHashMap<>();
            JsonNode argsNode = node.has("args") ? node.get("args") : node.get("arguments");
            if (argsNode != null && argsNode.isObject()) {
                args = objectMapper.convertValue(argsNode, Map.class);
            }
            return new ToolCall(name, args);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** 工具调用 */
    public static class ToolCall {
        public final String name;
        public final Map<String, Object> arguments;
        public ToolCall(String name, Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }

    /** 工具调用结果 */
    public static class ToolCallResult {
        private final String response;
        private final List<String> toolCallLog;
        private final int totalToolCalls;

        public ToolCallResult(String response, List<String> toolCallLog, int totalToolCalls) {
            this.response = response;
            this.toolCallLog = toolCallLog;
            this.totalToolCalls = totalToolCalls;
        }

        public String getResponse() { return response; }
        public List<String> getToolCallLog() { return toolCallLog; }
        public int getTotalToolCalls() { return totalToolCalls; }
    }
}
