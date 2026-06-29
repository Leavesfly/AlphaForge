package io.leavesfly.alphaforge.infrastructure.llm;

import io.leavesfly.alphaforge.config.AppConfig;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * LLM服务 - 统一调用多提供商大语言模型
 *
 * 支持: OpenAI、Gemini、Anthropic、DeepSeek等兼容OpenAI API格式的提供商
 * 通过统一的Chat Completion API调用
 */
@Service
public class LlmService implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LlmUsageTracker usageTracker;
    private final LlmRetryExecutor retryExecutor;
    private final LlmTokenEstimator tokenEstimator;
    private final LlmResponseParser responseParser;
    private final LlmChannelManager channelManager;

    /** 可选依赖：监控指标埋点 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LlmMetrics llmMetrics;

    /** 可选依赖：响应缓存 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LlmResponseCache responseCache;

    public LlmService(AppConfig config, LlmUsageTracker usageTracker) {
        this.config = config;
        this.usageTracker = usageTracker;
        this.objectMapper = new ObjectMapper();
        this.retryExecutor = new LlmRetryExecutor();
        this.tokenEstimator = new LlmTokenEstimator();
        this.responseParser = new LlmResponseParser(objectMapper, tokenEstimator);
        this.channelManager = new LlmChannelManager(config.getLlmChannels());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(config.getLlmTimeout(), TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 调用LLM进行股票分析
     *
     * @param context 分析上下文(股票数据、技术指标、新闻等)
     * @return LLM分析结果文本
     */
    public String analyzeStock(Map<String, Object> context) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildAnalysisPrompt(context);
        return chat(systemPrompt, userPrompt);
    }

    /**
     * 通用对话接口
     *
     * @param systemPrompt 系统提示
     * @param userMessage  用户消息
     * @return LLM回复
     */
    public String chat(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));
        return chatWithMessages(messages);
    }

    /**
     * 带完整消息历史的对话接口
     *
     * @param messages 消息列表
     * @return LLM回复
     */
    public String chatWithMessages(List<Map<String, String>> messages) {
        List<AppConfig.LlmChannelConfig> channels = config.getLlmChannels();
        if (channels.isEmpty()) {
            throw new LlmException.LlmUnavailableException("未配置LLM渠道，请设置 LLM_API 和 LLM_API_KEY");
        }

        // 查询缓存
        String cacheKey = null;
        if (responseCache != null && responseCache.isEnabled()) {
            cacheKey = responseCache.buildKey(config.getLlmModel(), messages);
            String cached = responseCache.get(cacheKey);
            if (cached != null) {
                log.debug("LLM缓存命中，直接返回缓存结果");
                return cached;
            }
        }

        // 尝试所有渠道(带故障切换)，每个渠道内部带重试
        Exception lastException = null;
        for (int attempt = 0; attempt < channels.size(); attempt++) {
            int idx = channelManager.getChannelIndex(attempt);
            AppConfig.LlmChannelConfig channel = channels.get(idx);

            try {
                String result = retryExecutor.executeWithRetry(
                        () -> executeApiCall(() -> callLlmApi(channel, messages), channel.getModel()),
                        "chatWithMessages:" + channel.getModel());
                if (result != null && !result.isEmpty()) {
                    channelManager.markSuccess(idx); // 记住成功的渠道
                    // 写入缓存
                    if (responseCache != null && cacheKey != null) {
                        responseCache.put(cacheKey, result);
                    }
                    return result;
                }
            } catch (LlmException e) {
                log.warn("LLM渠道 {} ({}) 调用失败(含重试): {}", idx, channel.getModel(), e.getMessage());
                lastException = e;
                // 不可重试的异常（认证/解析）直接跳出，不切换渠道
                if (e instanceof LlmException.LlmAuthException) break;
            } catch (Exception e) {
                log.warn("LLM渠道 {} ({}) 调用失败: {}", idx, channel.getModel(), e.getMessage());
                lastException = e;
            }
        }

        throw new LlmException.LlmUnavailableException(
                "所有LLM渠道均失败", lastException);
    }

    /**
     * 调用单个LLM API
     */
    private String callLlmApi(AppConfig.LlmChannelConfig channel, List<Map<String, String>> messages) throws Exception {
        String apiUrl = resolveApiUrl(channel);
        
        // 构建请求体 (OpenAI兼容格式)
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", channel.getModel());
        requestBody.put("temperature", channel.getTemperature());
        requestBody.put("max_tokens", channel.getMaxTokens());

        ArrayNode messagesArray = requestBody.putArray("messages");
        for (Map<String, String> msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.get("role"));
            msgNode.put("content", msg.get("content"));
        }

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + channel.getApiKey())
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        log.debug("调用LLM: model={}, url={}", channel.getModel(), apiUrl);
        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw buildHttpException(response, channel.getModel());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            long durationMs = System.currentTimeMillis() - startTime;
            
            // 解析响应 (OpenAI格式)
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText("");
                
                // 记录token使用量
                JsonNode usage = root.path("usage");
                int promptTokens, completionTokens;
                if (!usage.isMissingNode()) {
                    promptTokens = usage.path("prompt_tokens").asInt();
                    completionTokens = usage.path("completion_tokens").asInt();
                    log.info("Token使用: prompt={}, completion={}, total={}",
                            promptTokens, completionTokens,
                            usage.path("total_tokens").asInt());
                } else {
                    // 部分供应商不返回usage，粗略估算
                    promptTokens = estimateTokens(messages);
                    completionTokens = estimateTokens(content);
                    log.debug("Token估算(供应商未返回usage): prompt~={}, completion~={}", promptTokens, completionTokens);
                }
                usageTracker.recordUsage(channel.getModel(), channel.getProvider(),
                        promptTokens, completionTokens, durationMs);
                recordMetrics(channel.getModel(), "chatWithMessages", true, durationMs,
                        promptTokens, completionTokens);
                return content;
            }
            
            // Gemini格式兼容
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                String content = candidates.get(0).path("content").path("parts").get(0).path("text").asText("");
                JsonNode usage = root.path("usage_metadata");
                int promptTokens, completionTokens;
                if (!usage.isMissingNode()) {
                    promptTokens = usage.path("prompt_token_count").asInt();
                    completionTokens = usage.path("candidates_token_count").asInt();
                } else {
                    promptTokens = estimateTokens(messages);
                    completionTokens = estimateTokens(content);
                }
                usageTracker.recordUsage(channel.getModel(), channel.getProvider(),
                        promptTokens, completionTokens, durationMs);
                return content;
            }

            throw new LlmException.LlmParseException(
                    "无法解析LLM响应: " + responseBody.substring(0, Math.min(200, responseBody.length())),
                    channel.getModel());
        }
    }

    // ==================== 新增：原生 Function Calling ====================

    /**
     * 带原生 Function Calling 的对话
     *
     * 使用 OpenAI 标准 tools/tool_choice 参数，LLM 原生返回 tool_calls 结构，
     * 无需脆弱的文本标记解析。
     */
    @Override
    public LlmPort.LlmResponse chatWithFunctionCalling(List<Map<String, Object>> messages,
                                                        List<Map<String, Object>> tools) {
        List<AppConfig.LlmChannelConfig> channels = config.getLlmChannels();
        if (channels.isEmpty()) {
            throw new LlmException.LlmUnavailableException("未配置LLM渠道，请设置 LLM_API 和 LLM_API_KEY");
        }

        Exception lastException = null;
        for (int attempt = 0; attempt < channels.size(); attempt++) {
            int idx = channelManager.getChannelIndex(attempt);
            AppConfig.LlmChannelConfig channel = channels.get(idx);
            try {
                LlmPort.LlmResponse result = retryExecutor.executeWithRetry(
                        () -> executeApiCall(() -> callLlmApiWithTools(channel, messages, tools), channel.getModel()),
                        "chatWithFunctionCalling:" + channel.getModel());
                if (result != null && (result.hasToolCalls()
                        || (result.getContent() != null && !result.getContent().isEmpty()))) {
                    channelManager.markSuccess(idx);
                    return result;
                }
            } catch (LlmException e) {
                log.warn("LLM渠道 {} ({}) Function Calling失败(含重试): {}", idx, channel.getModel(), e.getMessage());
                lastException = e;
                if (e instanceof LlmException.LlmAuthException) break;
            } catch (Exception e) {
                log.warn("LLM渠道 {} ({}) Function Calling失败: {}", idx, channel.getModel(), e.getMessage());
                lastException = e;
            }
        }

        throw new LlmException.LlmUnavailableException(
                "所有LLM渠道均失败(Function Calling)", lastException);
    }

    /**
     * 调用 LLM API（带工具定义）
     */
    private LlmPort.LlmResponse callLlmApiWithTools(AppConfig.LlmChannelConfig channel,
                                                     List<Map<String, Object>> messages,
                                                     List<Map<String, Object>> tools) throws Exception {
        String apiUrl = resolveApiUrl(channel);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", channel.getModel());
        requestBody.put("temperature", channel.getTemperature());
        requestBody.put("max_tokens", channel.getMaxTokens());

        // 消息（Object 类型以支持 tool role 和 assistant.tool_calls）
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
                } else if (value instanceof Number) {
                    msgNode.set(key, objectMapper.valueToTree(value));
                } else if (value instanceof Boolean b) {
                    msgNode.put(key, b.booleanValue());
                } else {
                    msgNode.set(key, objectMapper.valueToTree(value));
                }
            }
        }

        // 工具定义（OpenAI Function Calling 格式）
        if (tools != null && !tools.isEmpty()) {
            requestBody.set("tools", objectMapper.valueToTree(tools));
            requestBody.put("tool_choice", "auto");
        }

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + channel.getApiKey())
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        log.debug("调用LLM(FC): model={}, url={}, tools={}", channel.getModel(), apiUrl,
                tools != null ? tools.size() : 0);
        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw buildHttpException(response, channel.getModel());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            long durationMs = System.currentTimeMillis() - startTime;

            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                String content = message.path("content").asText("");

                // 解析原生 tool_calls
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

                // 记录 Token 使用量
                JsonNode usage = root.path("usage");
                int promptTokens, completionTokens;
                if (!usage.isMissingNode()) {
                    promptTokens = usage.path("prompt_tokens").asInt();
                    completionTokens = usage.path("completion_tokens").asInt();
                    log.info("Token使用(FC): prompt={}, completion={}", promptTokens, completionTokens);
                } else {
                    promptTokens = estimateMessagesTokens(messages);
                    completionTokens = estimateTokens(content);
                }
                usageTracker.recordUsage(channel.getModel(), channel.getProvider(),
                        promptTokens, completionTokens, durationMs);

                log.debug("LLM(FC)响应: content_len={}, tool_calls={}",
                        content.length(), functionCalls.size());
                return new LlmPort.LlmResponse(content,
                        functionCalls.isEmpty() ? null : functionCalls);
            }

            throw new LlmException.LlmParseException(
                    "无法解析LLM响应: " +
                    responseBody.substring(0, Math.min(200, responseBody.length())),
                    channel.getModel());
        }
    }

    // ==================== 新增：结构化输出 ====================

    /**
     * 结构化输出对话 — 强制 LLM 返回 JSON
     *
     * 使用 response_format: json_object 模式（OpenAI 兼容），
     * 同时在 system prompt 中注入 JSON Schema 约束输出格式。
     */
    @Override
    public String chatForStructuredOutput(List<Map<String, Object>> messages,
                                           Map<String, Object> jsonSchema) {
        List<AppConfig.LlmChannelConfig> channels = config.getLlmChannels();
        if (channels.isEmpty()) {
            throw new LlmException.LlmUnavailableException("未配置LLM渠道");
        }

        Exception lastException = null;
        for (int attempt = 0; attempt < channels.size(); attempt++) {
            int idx = channelManager.getChannelIndex(attempt);
            AppConfig.LlmChannelConfig channel = channels.get(idx);
            try {
                String result = retryExecutor.executeWithRetry(
                        () -> executeApiCall(() -> callLlmApiForJson(channel, messages, jsonSchema), channel.getModel()),
                        "chatForStructuredOutput:" + channel.getModel());
                if (result != null && !result.isEmpty() && !"{}".equals(result)) {
                    channelManager.markSuccess(idx);
                    return result;
                }
            } catch (LlmException e) {
                log.warn("LLM渠道 {} 结构化输出失败(含重试): {}", idx, e.getMessage());
                lastException = e;
                if (e instanceof LlmException.LlmAuthException) break;
            } catch (Exception e) {
                log.warn("LLM渠道 {} 结构化输出失败: {}", idx, e.getMessage());
                lastException = e;
            }
        }

        throw new LlmException.LlmUnavailableException(
                "所有LLM渠道均失败(结构化输出)", lastException);
    }

    /**
     * 调用 LLM API（结构化输出模式）
     */
    private String callLlmApiForJson(AppConfig.LlmChannelConfig channel,
                                     List<Map<String, Object>> messages,
                                     Map<String, Object> jsonSchema) throws Exception {
        String apiUrl = resolveApiUrl(channel);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", channel.getModel());
        requestBody.put("temperature", 0.3); // 结构化输出用低温度
        requestBody.put("max_tokens", channel.getMaxTokens());

        // 消息
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
                } else {
                    msgNode.set(key, objectMapper.valueToTree(value));
                }
            }
        }

        // response_format: 强制 JSON 输出（兼容大多数 OpenAI 兼容 API）
        ObjectNode responseFormat = requestBody.putObject("response_format");
        responseFormat.put("type", "json_object");

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + channel.getApiKey())
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        log.debug("调用LLM(JSON): model={}", channel.getModel());
        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw buildHttpException(response, channel.getModel());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            long durationMs = System.currentTimeMillis() - startTime;

            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText("");

                // 记录 Token 使用量
                JsonNode usage = root.path("usage");
                int promptTokens, completionTokens;
                if (!usage.isMissingNode()) {
                    promptTokens = usage.path("prompt_tokens").asInt();
                    completionTokens = usage.path("completion_tokens").asInt();
                } else {
                    promptTokens = estimateMessagesTokens(messages);
                    completionTokens = estimateTokens(content);
                }
                usageTracker.recordUsage(channel.getModel(), channel.getProvider(),
                        promptTokens, completionTokens, durationMs);

                // 验证返回的是有效 JSON
                try {
                    objectMapper.readTree(content);
                    return content;
                } catch (Exception e) {
                    // 尝试从文本中提取 JSON（LLM 可能包裹 markdown 代码块）
                    String extracted = extractJsonFromText(content);
                    if (!"{}".equals(extracted)) {
                        return extracted;
                    }
                    throw new LlmException.LlmParseException(
                            "LLM 未返回有效 JSON: " +
                            content.substring(0, Math.min(200, content.length())),
                            channel.getModel());
                }
            }
            return "{}";
        }
    }

    /** 从文本中提取 JSON（处理 LLM 可能包裹 markdown 代码块的情况） */
    private String extractJsonFromText(String text) {
        return responseParser.extractJsonFromText(text);
    }

    /** 估算消息列表的 Token 数（支持 Object 类型消息） */
    private int estimateMessagesTokens(List<Map<String, Object>> messages) {
        return tokenEstimator.estimateMessagesTokens(messages);
    }

    /** 粗略估算文本的 token 数（约4字符≈1 token） */
    private int estimateTokens(List<Map<String, String>> messages) {
        return tokenEstimator.estimateMessagesTokensStr(messages);
    }

    private int estimateTokens(String text) {
        return tokenEstimator.estimateTokens(text);
    }

    /**
     * Vision API调用 - 支持图片输入
     * 通过OpenAI兼容格式的多模态接口识别图片内容
     *
     * @param prompt 文本提示
     * @param base64Image Base64编码的图片
     * @param mimeType 图片MIME类型
     * @return LLM回复
     */
    public String chatWithVision(String prompt, String base64Image, String mimeType) {
        List<AppConfig.LlmChannelConfig> channels = config.getLlmChannels();
        if (channels.isEmpty()) {
            throw new LlmException.LlmUnavailableException("未配置LLM渠道");
        }

        AppConfig.LlmChannelConfig channel = channelManager.getCurrentChannel();
        String apiUrl = resolveApiUrl(channel);

        try {
            // 构建多模态请求体 (OpenAI Vision兼容格式)
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", channel.getModel());
            requestBody.put("max_tokens", 2000);

            ArrayNode messagesArray = requestBody.putArray("messages");
            ObjectNode userMsg = messagesArray.addObject();
            userMsg.put("role", "user");

            ArrayNode contentArray = userMsg.putArray("content");
            // 文本部分
            ObjectNode textPart = contentArray.addObject();
            textPart.put("type", "text");
            textPart.put("text", prompt);
            // 图片部分
            ObjectNode imagePart = contentArray.addObject();
            imagePart.put("type", "image_url");
            ObjectNode imageUrl = imagePart.putObject("image_url");
            imageUrl.put("url", "data:" + mimeType + ";base64," + base64Image);

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.get("application/json"));

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .header("Authorization", "Bearer " + channel.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw buildHttpException(response, channel.getModel());
                }
                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    return choices.get(0).path("message").path("content").asText("");
                }
                return "[]";
            }
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Vision API调用失败: " + e.getMessage(), null, e);
        }
    }

    /**
     * 流式对话接口 - 支持SSE逐字输出
     *
     * @param messages 消息列表
     * @param onChunk  每收到一个文本片段时的回调
     * @return 完整回复文本
     */
    public String streamChatWithMessages(List<Map<String, String>> messages, Consumer<String> onChunk) {
        List<AppConfig.LlmChannelConfig> channels = config.getLlmChannels();
        if (channels.isEmpty()) {
            throw new LlmException.LlmUnavailableException("未配置LLM渠道");
        }

        Exception lastException = null;
        for (int attempt = 0; attempt < channels.size(); attempt++) {
            int idx = channelManager.getChannelIndex(attempt);
            AppConfig.LlmChannelConfig channel = channels.get(idx);
            try {
                String result = executeApiCall(
                        () -> callLlmStreamApi(channel, messages, onChunk), channel.getModel());
                if (result != null && !result.isEmpty()) {
                    channelManager.markSuccess(idx);
                    return result;
                }
            } catch (LlmException e) {
                log.warn("LLM流式渠道 {} ({}) 调用失败: {}", idx, channel.getModel(), e.getMessage());
                lastException = e;
                if (e instanceof LlmException.LlmAuthException) break;
            } catch (Exception e) {
                log.warn("LLM流式渠道 {} ({}) 调用失败: {}", idx, channel.getModel(), e.getMessage());
                lastException = e;
            }
        }

        throw new LlmException.LlmUnavailableException(
                "所有LLM流式渠道均失败", lastException);
    }

    /**
     * 调用单个LLM API (流式)
     */
    private String callLlmStreamApi(AppConfig.LlmChannelConfig channel,
                                    List<Map<String, String>> messages,
                                    Consumer<String> onChunk) throws Exception {
        String apiUrl = resolveApiUrl(channel);

        // 构建请求体 - 开启stream
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", channel.getModel());
        requestBody.put("temperature", channel.getTemperature());
        requestBody.put("max_tokens", channel.getMaxTokens());
        requestBody.put("stream", true);
        // 请求在最后一个chunk中返回usage信息
        ObjectNode streamOptions = requestBody.putObject("stream_options");
        streamOptions.put("include_usage", true);

        ArrayNode messagesArray = requestBody.putArray("messages");
        for (Map<String, String> msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.get("role"));
            msgNode.put("content", msg.get("content"));
        }

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + channel.getApiKey())
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        log.debug("流式调用LLM: model={}, url={}", channel.getModel(), apiUrl);
        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw buildHttpException(response, channel.getModel());
            }

            StringBuilder fullContent = new StringBuilder();
            int streamPromptTokens = 0;
            int streamCompletionTokens = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) break;

                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                            if (!delta.isMissingNode() && !delta.isNull()) {
                                String chunk = delta.asText();
                                fullContent.append(chunk);
                                onChunk.accept(chunk);
                            }
                            // 解析最后一个chunk中的usage信息
                            JsonNode usageNode = node.path("usage");
                            if (!usageNode.isMissingNode()) {
                                streamPromptTokens = usageNode.path("prompt_tokens").asInt();
                                streamCompletionTokens = usageNode.path("completion_tokens").asInt();
                            }
                        } catch (Exception e) {
                            // 忽略解析失败的行（如空行、注释等）
                        }
                    }
                }
            }
            long durationMs = System.currentTimeMillis() - startTime;
            // 记录token使用量
            if (streamPromptTokens > 0 || streamCompletionTokens > 0) {
                usageTracker.recordUsage(channel.getModel(), channel.getProvider(),
                        streamPromptTokens, streamCompletionTokens, durationMs);
                log.info("流式Token使用: prompt={}, completion={}", streamPromptTokens, streamCompletionTokens);
            } else {
                // 供应商未返回usage，粗略估算
                int estPrompt = estimateTokens(messages);
                int estCompletion = estimateTokens(fullContent.toString());
                usageTracker.recordUsage(channel.getModel(), channel.getProvider(),
                        estPrompt, estCompletion, durationMs);
                log.debug("流式Token估算: prompt~={}, completion~={}", estPrompt, estCompletion);
            }
            return fullContent.toString();
        }
    }

    // ==================== 监控指标辅助方法 ====================

    /** 记录 LLM 调用指标（当 LlmMetrics 可用时） */
    private void recordMetrics(String model, String method, boolean success,
                               long durationMs, int promptTokens, int completionTokens) {
        if (llmMetrics == null) return;
        try {
            llmMetrics.recordCallDuration(model, method, durationMs);
            llmMetrics.recordCallResult(model, method, success);
            llmMetrics.recordTokenUsage(model, "prompt", promptTokens);
            llmMetrics.recordTokenUsage(model, "completion", completionTokens);
        } catch (Exception e) {
            log.debug("记录指标失败: {}", e.getMessage());
        }
    }

    // ==================== 异常处理辅助方法 ====================

    /**
     * 根据 HTTP 响应构建对应的 LLM 异常类型
     * - 401/403 → LlmAuthException（不可重试）
     * - 429 → LlmRateLimitException（可重试，含 Retry-After）
     * - 5xx → LlmException（可重试）
     * - 其他 → LlmException（不可重试）
     */
    private LlmException buildHttpException(Response response, String model) {
        int code = response.code();
        String errorBody = "unknown";
        try {
            if (response.body() != null) {
                errorBody = response.body().string();
            }
        } catch (Exception ignored) {
            // 响应体读取失败时使用默认值
        }

        String msg = String.format("LLM API返回错误: %d - %s", code,
                errorBody.length() > 500 ? errorBody.substring(0, 500) : errorBody);

        return switch (code) {
            case 401, 403 -> new LlmException.LlmAuthException(msg, model);
            case 429 -> {
                long retryAfter = parseRetryAfter(response);
                yield new LlmException.LlmRateLimitException(msg, model, retryAfter);
            }
            case 500, 502, 503, 504 ->
                    new LlmException(msg, model, new RuntimeException("服务端错误: " + code));
            default -> new LlmException(msg, model);
        };
    }

    /** 从响应头解析 Retry-After 值（秒转毫秒） */
    private long parseRetryAfter(Response response) {
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null && !retryAfter.isEmpty()) {
            try {
                return Long.parseLong(retryAfter) * 1000L;
            } catch (NumberFormatException ignored) {
                // 可能是 HTTP-date 格式，暂不解析
            }
        }
        return 0;
    }

    /**
     * 函数式接口：允许抛出 checked exception 的 LLM API 调用
     */
    @FunctionalInterface
    private interface LlmApiCall<T> {
        T call() throws Exception;
    }

    /**
     * 执行 API 调用，将 checked exception 包装为 LlmException
     * 使其可在 Supplier<T> lambda 中使用（配合 retryExecutor）
     */
    private <T> T executeApiCall(LlmApiCall<T> call, String model) {
        try {
            return call.call();
        } catch (LlmException e) {
            throw e;
        } catch (java.net.SocketTimeoutException e) {
            throw new LlmException.LlmTimeoutException("请求超时: " + e.getMessage(), model, e);
        } catch (java.io.IOException e) {
            throw new LlmException("网络IO异常: " + e.getMessage(), model, e);
        } catch (Exception e) {
            throw new LlmException("LLM调用异常: " + e.getMessage(), model, e);
        }
    }

    /**
     * 解析API URL
     */
    private String resolveApiUrl(AppConfig.LlmChannelConfig channel) {
        String api = channel.getApi();
        if (api == null || api.isEmpty()) {
            // 默认OpenAI
            return "https://api.openai.com/v1/chat/completions";
        }
        // 确保URL以/chat/completions结尾
        if (!api.endsWith("/chat/completions")) {
            if (!api.endsWith("/")) api += "/";
            if (!api.contains("/v1/")) api += "v1/";
            api += "chat/completions";
        }
        return api;
    }

    /**
     * 构建系统提示
     */
    private String buildSystemPrompt() {
        return """
                你是一位专业的股票分析师，拥有丰富的技术分析和基本面分析经验。
                请根据提供的股票数据进行全面分析，包括：
                1. 技术面分析：趋势判断、关键技术指标解读、形态识别
                2. 基本面评估：估值水平、行业地位
                3. 消息面影响：重要新闻对股价的潜在影响
                4. 综合评分和交易信号
                5. 风险评估和操作建议
                
                请给出明确的交易信号(strong_buy/buy/neutral/sell/strong_sell)和评分(0-100)。
                分析要客观、专业，注重数据支撑。
                """;
    }

    /**
     * 构建分析提示
     */
    private String buildAnalysisPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下股票:\n\n");
        prompt.append("## 基本信息\n");
        prompt.append("- 股票代码: ").append(context.get("stock_code")).append("\n");
        prompt.append("- 股票名称: ").append(context.get("stock_name")).append("\n");
        prompt.append("- 市场: ").append(context.get("market")).append("\n");
        prompt.append("- 分析日期: ").append(context.get("analysis_date")).append("\n\n");

        prompt.append("## 历史行情数据\n");
        prompt.append(context.get("history_data")).append("\n");

        // 实时行情
        Object quote = context.get("realtime_quote");
        if (quote instanceof Map && !((Map<?, ?>) quote).isEmpty()) {
            prompt.append("## 实时行情\n");
            ((Map<?, ?>) quote).forEach((k, v) -> 
                    prompt.append("- ").append(k).append(": ").append(v).append("\n"));
            prompt.append("\n");
        }

        // 技术分析
        Object tech = context.get("technical_analysis");
        if (tech instanceof Map && !((Map<?, ?>) tech).isEmpty()) {
            prompt.append("## 技术指标\n");
            ((Map<?, ?>) tech).forEach((k, v) -> 
                    prompt.append("- ").append(k).append(": ").append(v).append("\n"));
            prompt.append("\n");
        }

        // 新闻
        Object news = context.get("news");
        if (news instanceof List && !((List<?>) news).isEmpty()) {
            prompt.append("## 相关新闻\n");
            for (Object item : (List<?>) news) {
                if (item instanceof Map) {
                    Map<?, ?> newsItem = (Map<?, ?>) item;
                    prompt.append("- ").append(newsItem.get("title")).append("\n");
                }
            }
            prompt.append("\n");
        }

        prompt.append("请给出完整的分析报告，包括综合评分、交易信号和操作建议。");
        return prompt.toString();
    }
}
