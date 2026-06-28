package io.leavesfly.alphaforge.domain.service.port;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM 调用端口（依赖倒置）
 *
 * application 层通过此端口调用大语言模型，具体实现由 infrastructure.llm 提供。
 */
public interface LlmPort {

    /** 调用LLM进行股票分析 */
    String analyzeStock(Map<String, Object> context);

    /** 基础对话 */
    String chat(String systemPrompt, String userMessage);

    /** 多轮消息对话 */
    String chatWithMessages(List<Map<String, String>> messages);

    /** 视觉对话（图片+文本） */
    String chatWithVision(String prompt, String base64Image, String mimeType);

    /** 流式多轮消息对话，逐块回调 */
    String streamChatWithMessages(List<Map<String, String>> messages, Consumer<String> onChunk);

    // ==================== 新增：原生 Function Calling ====================

    /**
     * 带原生 Function Calling 的对话（消息使用 Object 类型以支持 tool role）
     *
     * @param messages 消息列表（支持 system/user/assistant/tool 角色，assistant 可含 tool_calls）
     * @param tools    工具定义列表（OpenAI Function Calling 格式），为空则不启用工具
     * @return LLM 响应（含文本内容和可能的 tool_calls）
     */
    LlmResponse chatWithFunctionCalling(List<Map<String, Object>> messages,
                                        List<Map<String, Object>> tools);

    /**
     * 结构化输出对话（强制 LLM 返回符合 JSON Schema 的 JSON）
     *
     * @param messages    消息列表
     * @param jsonSchema  期望的 JSON Schema（描述输出结构）
     * @return LLM 返回的 JSON 字符串
     */
    String chatForStructuredOutput(List<Map<String, Object>> messages,
                                   Map<String, Object> jsonSchema);

    // ==================== 响应类型 ====================

    /**
     * LLM 响应（可能包含工具调用）
     */
    class LlmResponse {
        private final String content;
        private final List<FunctionCall> toolCalls;

        public LlmResponse(String content, List<FunctionCall> toolCalls) {
            this.content = content;
            this.toolCalls = toolCalls;
        }

        public String getContent() { return content; }
        public List<FunctionCall> getToolCalls() { return toolCalls; }
        public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }

        /** 快速构建一个无工具调用的响应 */
        public static LlmResponse textOnly(String content) {
            return new LlmResponse(content, null);
        }
    }

    /**
     * 函数调用（对应 OpenAI tool_calls 中的单个条目）
     */
    class FunctionCall {
        private final String id;
        private final String name;
        private final String arguments; // JSON 字符串

        public FunctionCall(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getArguments() { return arguments; }
    }
}
