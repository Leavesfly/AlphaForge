package io.leavesfly.alphaforge.application.agent;

import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import io.leavesfly.alphaforge.util.CommonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.agent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LLM工具调用适配器
 *
 * 优先使用 OpenAI 原生 Function Calling（tools/tool_choice 参数），
 * 当模型不支持时自动回退到文本标记解析模式。
 */
@Component
public class LlmToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(LlmToolAdapter.class);
    private final LlmPort llmService;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public LlmToolAdapter(LlmPort llmService, ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    // ==================== 公共入口 ====================

    /**
     * 执行带工具调用的LLM对话
     *
     * 优先使用原生 Function Calling，不支持时回退到文本标记模式。
     */
    public ToolCallResult chatWithTools(List<Map<String, String>> messages, int maxToolCalls) {
        return chatWithTools(messages, maxToolCalls, null);
    }

    /**
     * 执行带工具调用的LLM对话（支持回调通知）
     */
    public ToolCallResult chatWithTools(List<Map<String, String>> messages, int maxToolCalls,
                                         ToolCallCallback callback) {
        List<Map<String, Object>> tools = toolRegistry.getDefinitions();

        // 优先使用原生 Function Calling
        if (tools != null && !tools.isEmpty()) {
            try {
                ToolCallResult nativeResult = chatWithToolsNative(messages, tools, maxToolCalls, callback);
                if (nativeResult != null) {
                    return nativeResult;
                }
            } catch (Exception e) {
                log.warn("原生 Function Calling 失败，回退到文本标记模式: {}", e.getMessage());
            }
        }

        // Fallback: 文本标记方式
        return chatWithToolsLegacy(messages, maxToolCalls, callback);
    }

    /**
     * 执行工具调用循环（供流式输出场景使用）
     *
     * 如果没有工具调用，返回 finalResponse=null，调用方可用流式API获取回复。
     */
    public ToolCallSession executeToolLoop(List<Map<String, String>> messages, int maxToolCalls,
                                            ToolCallCallback callback) {
        List<Map<String, Object>> tools = toolRegistry.getDefinitions();

        if (tools != null && !tools.isEmpty()) {
            try {
                ToolCallSession nativeSession = executeToolLoopNative(messages, tools, maxToolCalls, callback);
                if (nativeSession != null) {
                    return nativeSession;
                }
            } catch (Exception e) {
                log.warn("原生 Function Calling 失败，回退到文本标记模式: {}", e.getMessage());
            }
        }

        return executeToolLoopLegacy(messages, maxToolCalls, callback);
    }

    // ==================== 原生 Function Calling 实现 ====================

    private ToolCallResult chatWithToolsNative(List<Map<String, String>> messages,
                                                List<Map<String, Object>> tools,
                                                int maxToolCalls, ToolCallCallback callback) {
        List<Map<String, Object>> objMessages = convertToObjMessages(messages);
        List<String> toolCallLog = new ArrayList<>();
        int toolCalls = 0;

        while (toolCalls < maxToolCalls) {
            LlmPort.LlmResponse response = llmService.chatWithFunctionCalling(objMessages, tools);

            if (!response.hasToolCalls()) {
                return new ToolCallResult(response.getContent(), toolCallLog, toolCalls);
            }

            // 将含 tool_calls 的 assistant 消息加入历史
            objMessages.add(buildAssistantWithToolCalls(response));
            toolCalls = executeNativeToolCalls(response, objMessages, toolCallLog, toolCalls, callback);
        }

        // 达到最大工具调用次数，请求最终总结
        objMessages.add(Map.of("role", "user", "content", (Object) "请根据以上所有工具调用结果，给出最终综合分析结论。"));
        LlmPort.LlmResponse finalResponse = llmService.chatWithFunctionCalling(objMessages, null);
        return new ToolCallResult(finalResponse.getContent(), toolCallLog, toolCalls);
    }

    private ToolCallSession executeToolLoopNative(List<Map<String, String>> messages,
                                                    List<Map<String, Object>> tools,
                                                    int maxToolCalls, ToolCallCallback callback) {
        List<Map<String, Object>> objMessages = convertToObjMessages(messages);
        List<String> toolCallLog = new ArrayList<>();
        int toolCalls = 0;

        while (toolCalls < maxToolCalls) {
            LlmPort.LlmResponse response = llmService.chatWithFunctionCalling(objMessages, tools);

            if (!response.hasToolCalls()) {
                if (toolCalls > 0) {
                    return new ToolCallSession(toolCallLog, toolCalls, response.getContent());
                } else {
                    return new ToolCallSession(toolCallLog, 0, null);
                }
            }

            objMessages.add(buildAssistantWithToolCalls(response));
            toolCalls = executeNativeToolCalls(response, objMessages, toolCallLog, toolCalls, callback);
        }

        return new ToolCallSession(toolCallLog, toolCalls, null);
    }

    /** 构建含 tool_calls 的 assistant 消息 */
    private Map<String, Object> buildAssistantWithToolCalls(LlmPort.LlmResponse response) {
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", response.getContent() != null ? response.getContent() : "");

        List<Map<String, Object>> toolCallsArray = new ArrayList<>();
        for (LlmPort.FunctionCall fc : response.getToolCalls()) {
            Map<String, Object> tc = new LinkedHashMap<>();
            tc.put("id", fc.getId());
            tc.put("type", "function");
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", fc.getName());
            fn.put("arguments", fc.getArguments());
            tc.put("function", fn);
            toolCallsArray.add(tc);
        }
        assistantMsg.put("tool_calls", toolCallsArray);
        return assistantMsg;
    }

    /** 执行原生 FC 返回的工具调用，返回更新后的 toolCalls 计数 */
    private int executeNativeToolCalls(LlmPort.LlmResponse response,
                                        List<Map<String, Object>> objMessages,
                                        List<String> toolCallLog, int toolCalls,
                                        ToolCallCallback callback) {
        for (LlmPort.FunctionCall fc : response.getToolCalls()) {
            toolCalls++;
            log.debug("执行工具调用(原生FC) #{}: {}({})", toolCalls, fc.getName(), fc.getArguments());
            long startTime = System.currentTimeMillis();

            Map<String, Object> args = parseArgumentsJson(fc.getArguments());
            String result;
            try {
                result = toolRegistry.execute(fc.getName(), args);
            } catch (ToolException e) {
                result = "工具调用失败: " + e.getMessage();
            } catch (Exception e) {
                result = "工具调用异常: " + e.getMessage();
            }
            long durationMs = System.currentTimeMillis() - startTime;
            toolCallLog.add(fc.getName() + " → " + CommonUtils.truncate(result, 200));

            if (callback != null) {
                callback.onToolCall(fc.getName(), args, result, durationMs);
            }

            // 将工具结果加入消息历史（标准 tool role）
            Map<String, Object> toolMsg = new LinkedHashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", fc.getId());
            toolMsg.put("content", result);
            objMessages.add(toolMsg);
        }
        return toolCalls;
    }

    // ==================== 文本标记 Fallback 实现（兼容不支持 FC 的模型） ====================

    private ToolCallResult chatWithToolsLegacy(List<Map<String, String>> messages,
                                                int maxToolCalls, ToolCallCallback callback) {
        List<String> toolCallLog = new ArrayList<>();
        int toolCalls = 0;

        while (toolCalls < maxToolCalls) {
            String response = llmService.chatWithMessages(messages);
            List<ToolCall> calls = parseToolCalls(response);
            if (calls.isEmpty()) {
                return new ToolCallResult(response, toolCallLog, toolCalls);
            }

            for (ToolCall call : calls) {
                toolCalls++;
                log.debug("执行工具调用(文本标记) #{}: {}({})", toolCalls, call.name, call.arguments);
                long startTime = System.currentTimeMillis();
                String result;
                try {
                    result = toolRegistry.execute(call.name, call.arguments);
                } catch (ToolException e) {
                    result = "工具调用失败: " + e.getMessage();
                }
                long durationMs = System.currentTimeMillis() - startTime;
                toolCallLog.add(call.name + " → " + CommonUtils.truncate(result, 200));
                
                if (callback != null) {
                    callback.onToolCall(call.name, call.arguments, result, durationMs);
                }

                messages.add(Map.of("role", "assistant", "content", response));
                messages.add(Map.of("role", "tool", "content",
                        String.format("[%s 调用结果]\n%s", call.name, result)));
            }
        }

        messages.add(Map.of("role", "user", "content", "请根据以上所有工具调用结果，给出最终综合分析结论。"));
        String finalResponse = llmService.chatWithMessages(messages);
        return new ToolCallResult(finalResponse, toolCallLog, toolCalls);
    }

    private ToolCallSession executeToolLoopLegacy(List<Map<String, String>> messages,
                                                    int maxToolCalls, ToolCallCallback callback) {
        List<String> toolCallLog = new ArrayList<>();
        int toolCalls = 0;

        while (toolCalls < maxToolCalls) {
            String response = llmService.chatWithMessages(messages);
            List<ToolCall> calls = parseToolCalls(response);

            if (calls.isEmpty()) {
                if (toolCalls > 0) {
                    return new ToolCallSession(toolCallLog, toolCalls, response);
                } else {
                    return new ToolCallSession(toolCallLog, 0, null);
                }
            }

            for (ToolCall call : calls) {
                toolCalls++;
                log.debug("执行工具调用(文本标记) #{}: {}({})", toolCalls, call.name, call.arguments);
                long startTime = System.currentTimeMillis();
                String result;
                try {
                    result = toolRegistry.execute(call.name, call.arguments);
                } catch (ToolException e) {
                    result = "工具调用失败: " + e.getMessage();
                }
                long durationMs = System.currentTimeMillis() - startTime;
                toolCallLog.add(call.name + " → " + CommonUtils.truncate(result, 200));
                
                if (callback != null) {
                    callback.onToolCall(call.name, call.arguments, result, durationMs);
                }

                messages.add(Map.of("role", "assistant", "content", response));
                messages.add(Map.of("role", "tool", "content",
                        String.format("[%s 调用结果]\n%s", call.name, result)));
            }
        }

        return new ToolCallSession(toolCallLog, toolCalls, null);
    }

    // ==================== 辅助方法 ====================

    /** 将 String 类型消息列表转为 Object 类型 */
    private List<Map<String, Object>> convertToObjMessages(List<Map<String, String>> messages) {
        List<Map<String, Object>> objMessages = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            objMessages.add(new LinkedHashMap<>(msg));
        }
        return objMessages;
    }

    /** 解析工具参数 JSON 字符串 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgumentsJson(String json) {
        if (json == null || json.isEmpty()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("解析工具参数失败: {} - {}", json, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** 解析LLM回复中的工具调用（文本标记格式，用于 fallback） */
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

    // ==================== 数据类 ====================

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

    /** 工具调用会话（用于流式场景） */
    public static class ToolCallSession {
        private final List<String> toolCallLog;
        private final int totalToolCalls;
        private final String finalResponse;

        public ToolCallSession(List<String> toolCallLog, int totalToolCalls, String finalResponse) {
            this.toolCallLog = toolCallLog;
            this.totalToolCalls = totalToolCalls;
            this.finalResponse = finalResponse;
        }

        public List<String> getToolCallLog() { return toolCallLog; }
        public int getTotalToolCalls() { return totalToolCalls; }
        public String getFinalResponse() { return finalResponse; }
        public boolean hasToolCalls() { return totalToolCalls > 0; }
    }

    /** 工具调用回调接口 */
    @FunctionalInterface
    public interface ToolCallCallback {
        void onToolCall(String toolName, Map<String, Object> args, String result, long durationMs);
    }
}
