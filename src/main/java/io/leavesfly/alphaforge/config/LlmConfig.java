package io.leavesfly.alphaforge.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 配置 — 独立管理大语言模型相关配置
 *
 * 从 AppConfig 中解耦，遵循接口隔离原则。
 * 负责加载 LLM API、模型、密钥、多渠道等配置。
 */
@Component
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    private final EnvVarProvider envVarProvider;

    private String llmApi;
    private String llmModel;
    private String llmApiKey;
    private double llmTemperature = 0.7;
    private int llmMaxTokens = 8000;
    private int llmTimeout = 120;
    private List<LlmChannelConfig> llmChannels = new ArrayList<>();

    public LlmConfig(EnvVarProvider envVarProvider) {
        this.envVarProvider = envVarProvider;
    }

    @PostConstruct
    public void init() {
        loadConfig();
        log.info("LLM配置加载完成: model={}, channels={}", llmModel, llmChannels.size());
    }

    private void loadConfig() {
        llmApi = envVarProvider.get("LLM_API", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        llmModel = envVarProvider.get("LLM_MODEL", "qwen-plus");
        llmApiKey = envVarProvider.get("LLM_API_KEY", "");
        llmTemperature = envVarProvider.getDouble("LLM_TEMPERATURE", 0.7);
        llmMaxTokens = envVarProvider.getInt("LLM_MAX_TOKENS", 8000);
        llmTimeout = envVarProvider.getInt("LLM_TIMEOUT", 120);
        parseLlmChannels();

        if (llmApiKey == null || llmApiKey.isEmpty()) {
            log.error("LLM_API_KEY 未配置 - AI分析功能将不可用");
        }
    }

    // ========== Getter ==========

    public String getLlmApi() { return llmApi; }
    public String getLlmModel() { return llmModel; }
    public String getLlmApiKey() { return llmApiKey; }
    public double getLlmTemperature() { return llmTemperature; }
    public int getLlmMaxTokens() { return llmMaxTokens; }
    public int getLlmTimeout() { return llmTimeout; }
    public List<LlmChannelConfig> getLlmChannels() { return llmChannels; }

    // ========== LLM 多渠道解析 ==========

    private void parseLlmChannels() {
        llmChannels.clear();
        String channelsStr = envVarProvider.get("LLM_CHANNELS", "");
        if (channelsStr.isEmpty() && !llmApi.isEmpty()) {
            LlmChannelConfig channel = new LlmChannelConfig();
            channel.setApi(llmApi);
            channel.setModel(llmModel);
            channel.setApiKey(llmApiKey);
            channel.setTemperature(llmTemperature);
            channel.setMaxTokens(llmMaxTokens);
            channel.setTimeout(llmTimeout);
            llmChannels.add(channel);
        } else if (!channelsStr.isEmpty()) {
            for (String channelUri : channelsStr.split(",")) {
                LlmChannelConfig channel = parseLlmUri(channelUri.trim());
                if (channel != null) {
                    llmChannels.add(channel);
                }
            }
        }
    }

    private LlmChannelConfig parseLlmUri(String uri) {
        try {
            LlmChannelConfig config = new LlmChannelConfig();
            if (!uri.contains("://")) {
                config.setModel(uri);
                config.setApi(llmApi);
                config.setApiKey(llmApiKey);
                config.setTemperature(llmTemperature);
                config.setMaxTokens(llmMaxTokens);
                config.setTimeout(llmTimeout);
                return config;
            }
            int schemeEnd = uri.indexOf("://");
            String provider = uri.substring(0, schemeEnd);
            String rest = uri.substring(schemeEnd + 3);
            String apiKey = "";
            String endpoint = "";
            String model = "";
            if (rest.contains("@")) {
                apiKey = rest.substring(0, rest.indexOf("@"));
                rest = rest.substring(rest.indexOf("@") + 1);
            }
            if (rest.contains("/")) {
                int slashIdx = rest.lastIndexOf("/");
                endpoint = rest.substring(0, slashIdx);
                model = rest.substring(slashIdx + 1);
            } else {
                model = rest;
            }
            config.setProvider(provider);
            config.setApi(endpoint.isEmpty() ? llmApi : "https://" + endpoint);
            config.setModel(model);
            config.setApiKey(apiKey.isEmpty() ? llmApiKey : apiKey);
            config.setTemperature(llmTemperature);
            config.setMaxTokens(llmMaxTokens);
            config.setTimeout(llmTimeout);
            return config;
        } catch (Exception e) {
            log.error("解析LLM渠道URI失败: {}", uri, e);
            return null;
        }
    }

    /** LLM 渠道配置 */
    public static class LlmChannelConfig {
        private String provider;
        private String api;
        private String model;
        private String apiKey;
        private double temperature = 0.7;
        private int maxTokens = 8000;
        private int timeout = 120;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApi() { return api; }
        public void setApi(String api) { this.api = api; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
    }
}
