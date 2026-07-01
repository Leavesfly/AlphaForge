package io.leavesfly.alphaforge.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 评分系统配置 — LLM 与策略引擎的评分混合参数，独立 Spring Bean
 */
@Component
public class ScoringConfig {

    private final EnvVarProvider env;

    public ScoringConfig(EnvVarProvider env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        llmScoreBlendRatio = env.getDouble("LLM_SCORE_BLEND_RATIO", 0.6);
        buyScoreThreshold = env.getInt("BUY_SCORE_THRESHOLD", 70);
        sellScoreThreshold = env.getInt("SELL_SCORE_THRESHOLD", 30);
        adaptiveBlendEnabled = env.getBool("ADAPTIVE_BLEND_ENABLED", true);
        adaptiveBlendBaseRatio = env.getDouble("ADAPTIVE_BLEND_BASE_RATIO", 0.5);
        adaptiveStrategyConfidenceImpact = env.getDouble("ADAPTIVE_STRATEGY_CONFIDENCE_IMPACT", 0.2);
        adaptiveMarketClarityImpact = env.getDouble("ADAPTIVE_MARKET_CLARITY_IMPACT", 0.15);
        adaptiveLlmConfidenceImpact = env.getDouble("ADAPTIVE_LLM_CONFIDENCE_IMPACT", 0.1);
        adaptiveBlendMinRatio = env.getDouble("ADAPTIVE_BLEND_MIN_RATIO", 0.2);
        adaptiveBlendMaxRatio = env.getDouble("ADAPTIVE_BLEND_MAX_RATIO", 0.8);
    }

    // ===== 基础混合比例 =====
    private double llmScoreBlendRatio = 0.6;
    private int buyScoreThreshold = 70;
    private int sellScoreThreshold = 30;

    // ===== 自适应评分混合 =====
    private boolean adaptiveBlendEnabled = true;
    private double adaptiveBlendBaseRatio = 0.5;
    private double adaptiveStrategyConfidenceImpact = 0.2;
    private double adaptiveMarketClarityImpact = 0.15;
    private double adaptiveLlmConfidenceImpact = 0.1;
    private double adaptiveBlendMinRatio = 0.2;
    private double adaptiveBlendMaxRatio = 0.8;

    // ===== Getters =====
    public double getLlmScoreBlendRatio() { return llmScoreBlendRatio; }
    public int getBuyScoreThreshold() { return buyScoreThreshold; }
    public int getSellScoreThreshold() { return sellScoreThreshold; }
    public boolean isAdaptiveBlendEnabled() { return adaptiveBlendEnabled; }
    public double getAdaptiveBlendBaseRatio() { return adaptiveBlendBaseRatio; }
    public double getAdaptiveStrategyConfidenceImpact() { return adaptiveStrategyConfidenceImpact; }
    public double getAdaptiveMarketClarityImpact() { return adaptiveMarketClarityImpact; }
    public double getAdaptiveLlmConfidenceImpact() { return adaptiveLlmConfidenceImpact; }
    public double getAdaptiveBlendMinRatio() { return adaptiveBlendMinRatio; }
    public double getAdaptiveBlendMaxRatio() { return adaptiveBlendMaxRatio; }
}
