package io.leavesfly.alphaforge.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.leavesfly.alphaforge.config.LlmConfig;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * LLM HTTP 请求构建器 — 统一封装 OkHttp 请求体构建逻辑
 *
 * 提取 LlmService 中 callLlmApi / callLlmApiWithTools / callLlmApiForJson / callLlmStreamApi
 * 四个方法中重复的请求体构建代码。
 */
public class LlmRequestBuilder {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    private final ObjectMapper objectMapper;

    public LlmRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 构建基础请求体（model, temperature, max_tokens）
     */
    public ObjectNode buildBaseRequest(LlmConfig.LlmChannelConfig channel) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", channel.getModel());
        requestBody.put("temperature", channel.getTemperature());
        requestBody.put("max_tokens", channel.getMaxTokens());
        return requestBody;
    }

    /**
     * 构建基础请求体（自定义温度）
     */
    public ObjectNode buildBaseRequest(LlmConfig.LlmChannelConfig channel, double temperature) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", channel.getModel());
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", channel.getMaxTokens());
        return requestBody;
    }

    /**
     * 添加 String 消息数组到请求体
     */
    public void addStringMessages(ObjectNode requestBody, List<Map<String, String>> messages) {
        ArrayNode messagesArray = requestBody.putArray("messages");
        for (Map<String, String> msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.get("role"));
            msgNode.put("content", msg.get("content"));
        }
    }

    /**
     * 添加 Object 消息数组到请求体（支持 tool role 和 assistant.tool_calls）
     */
    public void addObjectMessages(ObjectNode requestBody, List<Map<String, Object>> messages) {
        ArrayNode messagesArray = requestBody.putArray("messages");
        for (Map<String, Object> msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            for (Map.Entry<String, Object> entry : msg.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value == null) {
                    msgNode.putNull(key);
                } else if (value instanceof String s) {
                    msgNode.put(key, s);
                } else if (value instanceof Boolean b) {
                    msgNode.put(key, b.booleanValue());
                } else if (value instanceof Number) {
                    msgNode.set(key, objectMapper.valueToTree(value));
                } else {
                    msgNode.set(key, objectMapper.valueToTree(value));
                }
            }
        }
    }

    /**
     * 添加工具定义到请求体（OpenAI Function Calling 格式）
     */
    public void addTools(ObjectNode requestBody, List<Map<String, Object>> tools) {
        if (tools != null && !tools.isEmpty()) {
            requestBody.set("tools", objectMapper.valueToTree(tools));
            requestBody.put("tool_choice", "auto");
        }
    }

    /**
     * 添加 JSON 结构化输出格式约束
     */
    public void addJsonResponseFormat(ObjectNode requestBody) {
        ObjectNode responseFormat = requestBody.putObject("response_format");
        responseFormat.put("type", "json_object");
    }

    /**
     * 启用流式输出
     */
    public void enableStream(ObjectNode requestBody) {
        requestBody.put("stream", true);
        ObjectNode streamOptions = requestBody.putObject("stream_options");
        streamOptions.put("include_usage", true);
    }

    /**
     * 构建最终的 OkHttp Request 对象
     */
    public Request buildRequest(String apiUrl, String apiKey, ObjectNode requestBody) throws Exception {
        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                JSON_MEDIA_TYPE);

        return new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
    }
}
