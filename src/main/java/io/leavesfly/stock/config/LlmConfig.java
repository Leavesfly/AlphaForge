package io.leavesfly.stock.config;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 相关配置
 */
public class LlmConfig {

    private String llmApi = "";
    private String llmModel = "gemini-2.0-flash";
    private String llmApiKey = "";
    private double llmTemperature = 0.7;
    private int llmMaxTokens = 8000;
    private int llmTimeout = 120;
    private List<AppConfig.LlmChannelConfig> llmChannels = new ArrayList<>();

    // Getters & Setters
    public String getLlmApi() { return llmApi; }
    public void setLlmApi(String llmApi) { this.llmApi = llmApi; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public String getLlmApiKey() { return llmApiKey; }
    public void setLlmApiKey(String llmApiKey) { this.llmApiKey = llmApiKey; }
    public double getLlmTemperature() { return llmTemperature; }
    public void setLlmTemperature(double llmTemperature) { this.llmTemperature = llmTemperature; }
    public int getLlmMaxTokens() { return llmMaxTokens; }
    public void setLlmMaxTokens(int llmMaxTokens) { this.llmMaxTokens = llmMaxTokens; }
    public int getLlmTimeout() { return llmTimeout; }
    public void setLlmTimeout(int llmTimeout) { this.llmTimeout = llmTimeout; }
    public List<AppConfig.LlmChannelConfig> getLlmChannels() { return llmChannels; }
    public void setLlmChannels(List<AppConfig.LlmChannelConfig> llmChannels) { this.llmChannels = llmChannels; }
}
