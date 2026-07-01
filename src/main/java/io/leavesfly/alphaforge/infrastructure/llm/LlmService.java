package io.leavesfly.alphaforge.infrastructure.llm;

import io.leavesfly.alphaforge.config.LlmConfig;
import io.leavesfly.alphaforge.domain.service.exception.LlmException;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * LLM服务 - 统一调用多提供商大语言模型
 *
 * 支持: OpenAI、Gemini、Anthropic、DeepSeek等兼容OpenAI API格式的提供商
 * 通过统一的Chat Completion API调用
 */
@Service
public class LlmService implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final LlmConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LlmUsageTracker usageTracker;
    private final LlmRetryExecutor retryExecutor;
    private final LlmTokenEstimator tokenEstimator;
    private final LlmResponseParser responseParser;
    private final LlmChannelManager channelManager;
    private final LlmRequestBuilder requestBuilder;

    /** 可选依赖：监控指标埋点 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LlmMetrics llmMetrics;

    /** 可选依赖：响应缓存 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LlmResponseCache responseCache;

    public LlmService(LlmConfig config, LlmUsageTracker usageTracker,
                       OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.usageTracker = usageTracker;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.retryExecutor = new LlmRetryExecutor();
        this.tokenEstimator = new LlmTokenEstimator();
        this.responseParser = new LlmResponseParser(objectMapper, tokenEstimator);
        this.channelManager = new LlmChannelManager(config.getLlmChannels());
        this.requestBuilder = new LlmRequestBuilder(objectMapper);
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
        List<LlmConfig.LlmChannelConfig> channels = config.getLlmChannels();
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

        final String finalCacheKey = cacheKey;
        String result = executeWithChannelFailover(
                channel -> retryExecutor.executeWithRetry(
                        () -> executeApiCall(() -> callLlmApi(channel, messages), channel.getModel()),
                        "chatWithMessages:" + channel.getModel()),
                r -> r != null && !r.isEmpty(),
                "chatWithMessages",
                "所有LLM渠道均失败");

        // 写入缓存
        if (responseCache != null && finalCacheKey != null) {
            responseCache.put(finalCacheKey, result);
        }
        return result;
    }

    /**
     * 调用单个LLM API
     */
    private String callLlmApi(LlmConfig.LlmChannelConfig channel, List<Map<String, String>> messages) throws Exception {
        ObjectNode requestBody = requestBuilder.buildBaseRequest(channel);
        requestBuilder.addStringMessages(requestBody, messages);

        log.debug("调用LLM: model={}", channel.getModel());
        CallResult cr = executeHttpRequest(channel, requestBody);

        // OpenAI格式
        JsonNode choices = cr.root().path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            String content = choices.get(0).path("message").path("content").asText("");
            recordUsageFromResponse(cr.root(), channel, cr.durationMs(),
                    estimateTokens(messages), estimateTokens(content));
            recordMetrics(channel.getModel(), "chatWithMessages", true, cr.durationMs(),
                    cr.root().path("usage").path("prompt_tokens").asInt(),
                    cr.root().path("usage").path("completion_tokens").asInt());
            return content;
        }

        // Gemini格式兼容
        JsonNode candidates = cr.root().path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            String content = candidates.get(0).path("content").path("parts").get(0).path("text").asText("");
            JsonNode usageMeta = cr.root().path("usage_metadata");
            int p = !usageMeta.isMissingNode() ? usageMeta.path("prompt_token_count").asInt() : estimateTokens(messages);
            int c = !usageMeta.isMissingNode() ? usageMeta.path("candidates_token_count").asInt() : estimateTokens(content);
            usageTracker.recordUsage(channel.getModel(), channel.getProvider(), p, c, cr.durationMs());
            return content;
        }

        throw new LlmException.LlmParseException(
                "无法解析LLM响应: " + cr.root().toString().substring(0, Math.min(200, cr.root().toString().length())),
                channel.getModel());
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
        return executeWithChannelFailover(
                channel -> retryExecutor.executeWithRetry(
                        () -> executeApiCall(() -> callLlmApiWithTools(channel, messages, tools), channel.getModel()),
                        "chatWithFunctionCalling:" + channel.getModel()),
                r -> r != null && (r.hasToolCalls()
                        || (r.getContent() != null && !r.getContent().isEmpty())),
                "Function Calling",
                "所有LLM渠道均失败(Function Calling)");
    }

    /**
     * 调用 LLM API（带工具定义）
     */
    private LlmPort.LlmResponse callLlmApiWithTools(LlmConfig.LlmChannelConfig channel,
                                                     List<Map<String, Object>> messages,
                                                     List<Map<String, Object>> tools) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", channel.getModel());
        requestBody.put("temperature", channel.getTemperature());
        requestBody.put("max_tokens", channel.getMaxTokens());
        addObjectMessages(requestBody, messages);

        if (tools != null && !tools.isEmpty()) {
            requestBody.set("tools", objectMapper.valueToTree(tools));
            requestBody.put("tool_choice", "auto");
        }

        log.debug("调用LLM(FC): model={}, tools={}", channel.getModel(),
                tools != null ? tools.size() : 0);
        CallResult cr = executeHttpRequest(channel, requestBody);

        JsonNode choices = cr.root().path("choices");
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

            recordUsageFromResponse(cr.root(), channel, cr.durationMs(),
                    estimateMessagesTokens(messages), estimateTokens(content));
            log.debug("LLM(FC)响应: content_len={}, tool_calls={}",
                    content.length(), functionCalls.size());
            return new LlmPort.LlmResponse(content,
                    functionCalls.isEmpty() ? null : functionCalls);
        }

        throw new LlmException.LlmParseException(
                "无法解析LLM响应: " + cr.root().toString().substring(0, Math.min(200, cr.root().toString().length())),
                channel.getModel());
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
        return executeWithChannelFailover(
                channel -> retryExecutor.executeWithRetry(
                        () -> executeApiCall(() -> callLlmApiForJson(channel, messages, jsonSchema), channel.getModel()),
                        "chatForStructuredOutput:" + channel.getModel()),
                r -> r != null && !r.isEmpty() && !"{}".equals(r),
                "结构化输出",
                "所有LLM渠道均失败(结构化输出)");
    }

    /**
     * 调用 LLM API（结构化输出模式）
     */
    private String callLlmApiForJson(LlmConfig.LlmChannelConfig channel,
                                     List<Map<String, Object>> messages,
                                     Map<String, Object> jsonSchema) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", channel.getModel());
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", channel.getMaxTokens());
        addObjectMessages(requestBody, messages);

        requestBody.putObject("response_format").put("type", "json_object");

        log.debug("调用LLM(JSON): model={}", channel.getModel());
        CallResult cr = executeHttpRequest(channel, requestBody);

        JsonNode choices = cr.root().path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            String content = choices.get(0).path("message").path("content").asText("");
            recordUsageFromResponse(cr.root(), channel, cr.durationMs(),
                    estimateMessagesTokens(messages), estimateTokens(content));

            // 验证返回的是有效 JSON
            try {
                objectMapper.readTree(content);
                return content;
            } catch (Exception e) {
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
     * 流式对话接口 - 支持SSE逐字输出
     *
     * @param messages 消息列表
     * @param onChunk  每收到一个文本片段时的回调
     * @return 完整回复文本
     */
    public String streamChatWithMessages(List<Map<String, String>> messages, Consumer<String> onChunk) {
        return executeWithChannelFailover(
                channel -> executeApiCall(
                        () -> callLlmStreamApi(channel, messages, onChunk), channel.getModel()),
                r -> r != null && !r.isEmpty(),
                "流式",
                "所有LLM流式渠道均失败");
    }

    /**
     * 调用单个LLM API (流式)
     */
    private String callLlmStreamApi(LlmConfig.LlmChannelConfig channel,
                                    List<Map<String, String>> messages,
                                    Consumer<String> onChunk) throws Exception {
        // 复用 LlmRequestBuilder 统一构建请求体
        ObjectNode requestBody = requestBuilder.buildBaseRequest(channel);
        requestBuilder.addStringMessages(requestBody, messages);
        requestBuilder.enableStream(requestBody);

        Request request = requestBuilder.buildRequest(
                resolveApiUrl(channel), channel.getApiKey(), requestBody);

        log.debug("流式调用LLM: model={}", channel.getModel());
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

    // ==================== 渠道故障切换模板 ====================

    /**
     * 函数式接口：按渠道执行 LLM 调用
     */
    @FunctionalInterface
    private interface ChannelExecutor<T> {
        T execute(LlmConfig.LlmChannelConfig channel) throws Exception;
    }

    /**
     * 渠道故障切换模板方法 — 遍历所有渠道，带重试和故障切换
     *
     * 统一封装 chatWithMessages / chatWithFunctionCalling / chatForStructuredOutput / streamChatWithMessages
     * 中重复的渠道遍历 + 故障切换 + 异常处理逻辑。
     *
     * @param executor      每个渠道的执行逻辑（含重试）
     * @param isSuccess     结果有效性判断
     * @param operationName  操作名称（用于日志）
     * @param errorMessage   全部失败时的异常消息
     * @return 第一个成功渠道的结果
     */
    private <T> T executeWithChannelFailover(
            ChannelExecutor<T> executor,
            Predicate<T> isSuccess,
            String operationName,
            String errorMessage) {

        List<LlmConfig.LlmChannelConfig> channels = config.getLlmChannels();
        if (channels.isEmpty()) {
            throw new LlmException.LlmUnavailableException("未配置LLM渠道，请设置 LLM_API 和 LLM_API_KEY");
        }

        Exception lastException = null;
        for (int attempt = 0; attempt < channels.size(); attempt++) {
            int idx = channelManager.getChannelIndex(attempt);
            LlmConfig.LlmChannelConfig channel = channels.get(idx);
            try {
                T result = executor.execute(channel);
                if (isSuccess.test(result)) {
                    channelManager.markSuccess(idx);
                    return result;
                }
            } catch (LlmException e) {
                log.warn("LLM渠道 {} ({}) {}失败(含重试): {}", idx, channel.getModel(), operationName, e.getMessage());
                lastException = e;
                if (e instanceof LlmException.LlmAuthException) break;
            } catch (Exception e) {
                log.warn("LLM渠道 {} ({}) {}失败: {}", idx, channel.getModel(), operationName, e.getMessage());
                lastException = e;
            }
        }

        throw new LlmException.LlmUnavailableException(errorMessage, lastException);
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

    // ==================== 公共调用辅助方法 ====================

    /** HTTP 调用结果 */
    private record CallResult(JsonNode root, long durationMs) {}

    /**
     * 构建 HTTP 请求并执行，返回解析后的 JSON 响应
     * 统一 3 种调用模式中重复的 HTTP 请求构建 + 执行 + 响应解析逻辑
     */
    private CallResult executeHttpRequest(LlmConfig.LlmChannelConfig channel,
                                            ObjectNode requestBody) throws Exception {
        String apiUrl = resolveApiUrl(channel);
        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + channel.getApiKey())
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        long startTime = System.currentTimeMillis();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw buildHttpException(response, channel.getModel());
            }
            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            return new CallResult(root, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 从响应中记录 Token 使用量（供应商未返回 usage 时使用估算值）
     */
    private void recordUsageFromResponse(JsonNode root, LlmConfig.LlmChannelConfig channel,
                                          long durationMs, int estPromptTokens, int estCompletionTokens) {
        JsonNode usage = root.path("usage");
        int promptTokens, completionTokens;
        if (!usage.isMissingNode()) {
            promptTokens = usage.path("prompt_tokens").asInt();
            completionTokens = usage.path("completion_tokens").asInt();
        } else {
            promptTokens = estPromptTokens;
            completionTokens = estCompletionTokens;
        }
        usageTracker.recordUsage(channel.getModel(), channel.getProvider(),
                promptTokens, completionTokens, durationMs);
    }

    /**
     * 将 Object 类型消息列表添加到请求体中
     */
    private void addObjectMessages(ObjectNode requestBody, List<Map<String, Object>> messages) {
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
                } else {
                    msgNode.set(key, objectMapper.valueToTree(value));
                }
            }
        }
    }

    /**
     * 解析API URL
     */
    private String resolveApiUrl(LlmConfig.LlmChannelConfig channel) {
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

}
