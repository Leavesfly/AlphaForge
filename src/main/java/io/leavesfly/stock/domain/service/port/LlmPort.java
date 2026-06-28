package io.leavesfly.stock.domain.service.port;

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
}
