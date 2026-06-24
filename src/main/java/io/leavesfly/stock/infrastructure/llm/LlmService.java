package io.leavesfly.stock.infrastructure.llm;

import io.leavesfly.stock.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * LLM服务 - 统一调用多提供商大语言模型
 * 
 * 对应Python版本的 src/llm/ 模块
 * 支持: OpenAI、Gemini、Anthropic、DeepSeek等兼容OpenAI API格式的提供商
 * 通过统一的Chat Completion API调用
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** 当前使用的渠道索引 */
    private int currentChannelIndex = 0;

    public LlmService(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
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
            log.error("未配置LLM渠道，请设置 LLM_API 和 LLM_API_KEY");
            return "[错误] 未配置LLM服务";
        }

        // 尝试所有渠道(带故障切换)
        for (int attempt = 0; attempt < channels.size(); attempt++) {
            int idx = (currentChannelIndex + attempt) % channels.size();
            AppConfig.LlmChannelConfig channel = channels.get(idx);

            try {
                String result = callLlmApi(channel, messages);
                if (result != null && !result.isEmpty()) {
                    currentChannelIndex = idx; // 记住成功的渠道
                    return result;
                }
            } catch (Exception e) {
                log.warn("LLM渠道 {} ({}) 调用失败: {}", idx, channel.getModel(), e.getMessage());
                // 切换到下一个渠道
            }
        }

        log.error("所有LLM渠道均失败");
        return "[错误] LLM服务不可用";
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

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new RuntimeException("LLM API返回错误: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            
            // 解析响应 (OpenAI格式)
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText("");
                
                // 记录token使用量
                JsonNode usage = root.path("usage");
                if (!usage.isMissingNode()) {
                    log.info("Token使用: prompt={}, completion={}, total={}",
                            usage.path("prompt_tokens").asInt(),
                            usage.path("completion_tokens").asInt(),
                            usage.path("total_tokens").asInt());
                }
                return content;
            }
            
            // Gemini格式兼容
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                return candidates.get(0).path("content").path("parts").get(0).path("text").asText("");
            }

            throw new RuntimeException("无法解析LLM响应: " + responseBody.substring(0, Math.min(200, responseBody.length())));
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
            String err = "[错误] 未配置LLM服务";
            onChunk.accept(err);
            return err;
        }

        for (int attempt = 0; attempt < channels.size(); attempt++) {
            int idx = (currentChannelIndex + attempt) % channels.size();
            AppConfig.LlmChannelConfig channel = channels.get(idx);
            try {
                String result = callLlmStreamApi(channel, messages, onChunk);
                if (result != null && !result.isEmpty()) {
                    currentChannelIndex = idx;
                    return result;
                }
            } catch (Exception e) {
                log.warn("LLM流式渠道 {} ({}) 调用失败: {}", idx, channel.getModel(), e.getMessage());
            }
        }

        String err = "[错误] LLM服务不可用";
        onChunk.accept(err);
        return err;
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

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new RuntimeException("LLM API返回错误: " + response.code() + " - " + errorBody);
            }

            StringBuilder fullContent = new StringBuilder();
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
                        } catch (Exception e) {
                            // 忽略解析失败的行（如空行、注释等）
                        }
                    }
                }
            }
            return fullContent.toString();
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
