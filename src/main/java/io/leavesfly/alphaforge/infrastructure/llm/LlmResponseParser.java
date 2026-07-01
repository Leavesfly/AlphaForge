package io.leavesfly.alphaforge.infrastructure.llm;

import io.leavesfly.alphaforge.domain.service.exception.LlmException;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import io.leavesfly.alphaforge.util.CommonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 响应解析器 — 统一解析 OpenAI / Gemini 格式的 API 响应
 *
 * 职责：
 * - 解析 OpenAI 格式响应（choices/message/content + tool_calls + usage）
 * - 解析 Gemini 格式响应（candidates/content/parts/text + usage_metadata）
 * - 从文本中提取 JSON（处理 markdown 代码块包裹）
 *
 * 无状态，线程安全。
 */
public class LlmResponseParser {

    private final ObjectMapper objectMapper;
    private final LlmTokenEstimator tokenEstimator;

    public LlmResponseParser(ObjectMapper objectMapper, LlmTokenEstimator tokenEstimator) {
        this.objectMapper = objectMapper;
        this.tokenEstimator = tokenEstimator;
    }

    // ===== 解析结果数据类 =====

    /**
     * 解析后的 LLM 响应
     */
    public static class ParsedResponse {
        private final String content;
        private final List<LlmPort.FunctionCall> toolCalls;
        private final int promptTokens;
        private final int completionTokens;

        public ParsedResponse(String content, List<LlmPort.FunctionCall> toolCalls,
                              int promptTokens, int completionTokens) {
            this.content = content;
            this.toolCalls = toolCalls;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }

        public String getContent() { return content; }
        public List<LlmPort.FunctionCall> getToolCalls() { return toolCalls; }
        public int getPromptTokens() { return promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
        public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
    }

    // ===== OpenAI 格式解析 =====

    /**
     * 解析 OpenAI 兼容格式的响应
     *
     * @param root         响应 JSON 根节点
     * @param messagesForEstimate 消息列表（用于 Token 估算后备）
     * @param model        模型名称（用于异常信息）
     * @return 解析结果
     */
    public ParsedResponse parseOpenAiResponse(JsonNode root,
                                               List<Map<String, ?>> messagesForEstimate,
                                               String model) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new LlmException.LlmParseException("无法解析LLM响应: " + CommonUtils.truncate(root.toString(), 200), model);
        }

        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText("");

        // 解析 tool_calls
        List<LlmPort.FunctionCall> functionCalls = parseToolCalls(message);

        // 解析 Token 使用量
        int[] tokens = parseOpenAiUsage(root, messagesForEstimate, content);

        return new ParsedResponse(content, functionCalls, tokens[0], tokens[1]);
    }

    /** 解析 OpenAI 格式的 tool_calls */
    private List<LlmPort.FunctionCall> parseToolCalls(JsonNode message) {
        List<LlmPort.FunctionCall> functionCalls = new ArrayList<>();
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode tc : toolCallsNode) {
                String id = tc.path("id").asText("");
                JsonNode fn = tc.path("function");
                String name = fn.path("name").asText("");
                String args = fn.path("arguments").asText("{}");
                if (!name.isEmpty()) {
                    functionCalls.add(new LlmPort.FunctionCall(id, name, args));
                }
            }
        }
        return functionCalls;
    }

    /** 解析 OpenAI usage 节点，返回 [promptTokens, completionTokens] */
    private int[] parseOpenAiUsage(JsonNode root, List<Map<String, ?>> messagesForEstimate, String content) {
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode()) {
            return new int[]{
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt()
            };
        }
        // 后备估算
        int promptTokens = estimateMessagesTokens(messagesForEstimate);
        int completionTokens = tokenEstimator.estimateTokens(content);
        return new int[]{promptTokens, completionTokens};
    }

    // ===== Gemini 格式解析 =====

    /**
     * 解析 Gemini 格式的响应
     *
     * @param root         响应 JSON 根节点
     * @param messagesForEstimate 消息列表（用于 Token 估算后备）
     * @param model        模型名称
     * @return 解析结果，null 表示不是 Gemini 格式
     */
    public ParsedResponse parseGeminiResponse(JsonNode root,
                                               List<Map<String, ?>> messagesForEstimate,
                                               String model) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return null; // 不是 Gemini 格式
        }

        String content = candidates.get(0).path("content").path("parts").get(0).path("text").asText("");

        // 解析 Token 使用量
        JsonNode usage = root.path("usage_metadata");
        int promptTokens, completionTokens;
        if (!usage.isMissingNode()) {
            promptTokens = usage.path("prompt_token_count").asInt();
            completionTokens = usage.path("candidates_token_count").asInt();
        } else {
            promptTokens = estimateMessagesTokens(messagesForEstimate);
            completionTokens = tokenEstimator.estimateTokens(content);
        }

        return new ParsedResponse(content, null, promptTokens, completionTokens);
    }

    // ===== JSON 提取 =====

    /**
     * 从文本中提取 JSON（处理 LLM 可能包裹 markdown 代码块的情况）
     *
     * @param text LLM 返回的原始文本
     * @return 提取的 JSON 字符串，无法提取时返回 "{}"
     */
    public String extractJsonFromText(String text) {
        String extracted = CommonUtils.extractJsonFromText(text);
        return extracted != null ? extracted : "{}";
    }

    /** 验证字符串是否为有效 JSON */
    public boolean isValidJson(String text) {
        try {
            objectMapper.readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ===== 辅助方法 =====

    private int estimateMessagesTokens(List<Map<String, ?>> messages) {
        if (messages == null || messages.isEmpty()) return 1;
        int total = 0;
        for (Map<String, ?> msg : messages) {
            Object content = msg.get("content");
            if (content instanceof String s) {
                total += s.length();
            } else if (content != null) {
                total += content.toString().length();
            }
        }
        return Math.max(1, total / 4);
    }
}