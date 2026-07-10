package io.leavesfly.alphaforge.infrastructure.llm;

import io.leavesfly.alphaforge.config.LlmConfig;
import io.leavesfly.alphaforge.domain.service.exception.LlmException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * LLM HTTP 传输层 — 封装 OkHttp 请求执行、SSE 流式读取、HTTP 状态到 {@link LlmException} 的映射。
 *
 * <p>从 {@code LlmService} 抽出的高内聚协作者：{@code LlmService} 专注端口语义
 * （对话 / Function Calling / 结构化输出）与用量、指标记录，而本类只负责
 * “把请求打出去、把响应读回来、把网络/HTTP 错误翻译成领域异常”。</p>
 */
public class LlmHttpClient {

    private static final Logger log = LoggerFactory.getLogger(LlmHttpClient.class);

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LlmRequestBuilder requestBuilder;

    public LlmHttpClient(OkHttpClient httpClient, ObjectMapper objectMapper, LlmRequestBuilder requestBuilder) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.requestBuilder = requestBuilder;
    }

    /** HTTP 调用结果 */
    public record CallResult(JsonNode root, long durationMs) {}

    /** 流式调用结果（累积内容 + 供应商返回的用量，未返回时为 0） */
    public record StreamResult(String content, int promptTokens, int completionTokens, long durationMs) {}

    /** 函数式接口：允许抛出 checked exception 的 LLM API 调用 */
    @FunctionalInterface
    public interface LlmApiCall<T> {
        T call() throws Exception;
    }

    /**
     * 构建 HTTP 请求并执行，返回解析后的 JSON 响应。
     */
    public CallResult executeHttpRequest(LlmConfig.LlmChannelConfig channel,
                                         ObjectNode requestBody) throws Exception {
        Request request = requestBuilder.buildRequest(resolveApiUrl(channel), channel.getApiKey(), requestBody);
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
     * 执行流式（SSE）请求：逐字回调 {@code onChunk}，返回累积内容与供应商用量。
     */
    public StreamResult executeStreamRequest(LlmConfig.LlmChannelConfig channel,
                                             ObjectNode requestBody,
                                             Consumer<String> onChunk) throws Exception {
        Request request = requestBuilder.buildRequest(resolveApiUrl(channel), channel.getApiKey(), requestBody);
        log.debug("流式调用LLM: model={}", channel.getModel());
        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw buildHttpException(response, channel.getModel());
            }

            StringBuilder fullContent = new StringBuilder();
            int promptTokens = 0;
            int completionTokens = 0;
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
                                promptTokens = usageNode.path("prompt_tokens").asInt();
                                completionTokens = usageNode.path("completion_tokens").asInt();
                            }
                        } catch (Exception e) {
                            // 忽略解析失败的行（如空行、注释等）
                        }
                    }
                }
            }
            long durationMs = System.currentTimeMillis() - startTime;
            return new StreamResult(fullContent.toString(), promptTokens, completionTokens, durationMs);
        }
    }

    /**
     * 执行 API 调用，将 checked exception 包装为 {@link LlmException}，
     * 使其可在 {@code Supplier<T>} lambda 中使用（配合重试执行器）。
     */
    public <T> T executeApiCall(LlmApiCall<T> call, String model) {
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
     * 根据 HTTP 响应构建对应的 LLM 异常类型。
     * - 401/403 → LlmAuthException（不可重试）
     * - 429 → LlmRateLimitException（可重试，含 Retry-After）
     * - 5xx → LlmException（可重试）
     * - 其他 → LlmException（不可重试）
     */
    public LlmException buildHttpException(Response response, String model) {
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
     * 解析 API URL — 确保以 /chat/completions 结尾（默认回退到 OpenAI）。
     */
    public String resolveApiUrl(LlmConfig.LlmChannelConfig channel) {
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
