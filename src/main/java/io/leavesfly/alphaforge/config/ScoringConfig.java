package io.leavesfly.alphaforge.config;

/**
 * 评分系统配置 — LLM 与策略引擎的评分混合参数
 *
 * 包含：
 * - 基础混合比例（LLM 评分 vs 策略引擎评分）
 * - 自适应混合参数（根据策略置信度、市场清晰度、LLM 置信度动态调整）
 * - 买卖评分阈值
 *
 * 从 AppConfig 中提取，使评分参数集中管理、易于调优。
 */
public class ScoringConfig {

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

    // ===== Getters & Setters =====

    public double getLlmScoreBlendRatio() { return llmScoreBlendRatio; }
    public void setLlmScoreBlendRatio(double llmScoreBlendRatio) { this.llmScoreBlendRatio = llmScoreBlendRatio; }

    public int getBuyScoreThreshold() { return buyScoreThreshold; }
    public void setBuyScoreThreshold(int buyScoreThreshold) { this.buyScoreThreshold = buyScoreThreshold; }

    public int getSellScoreThreshold() { return sellScoreThreshold; }
    public void setSellScoreThreshold(int sellScoreThreshold) { this.sellScoreThreshold = sellScoreThreshold; }

    public boolean isAdaptiveBlendEnabled() { return adaptiveBlendEnabled; }
    public void setAdaptiveBlendEnabled(boolean adaptiveBlendEnabled) { this.adaptiveBlendEnabled = adaptiveBlendEnabled; }

    public double getAdaptiveBlendBaseRatio() { return adaptiveBlendBaseRatio; }
    public void setAdaptiveBlendBaseRatio(double adaptiveBlendBaseRatio) { this.adaptiveBlendBaseRatio = adaptiveBlendBaseRatio; }

    public double getAdaptiveStrategyConfidenceImpact() { return adaptiveStrategyConfidenceImpact; }
    public void setAdaptiveStrategyConfidenceImpact(double adaptiveStrategyConfidenceImpact) { this.adaptiveStrategyConfidenceImpact = adaptiveStrategyConfidenceImpact; }

    public double getAdaptiveMarketClarityImpact() { return adaptiveMarketClarityImpact; }
    public void setAdaptiveMarketClarityImpact(double adaptiveMarketClarityImpact) { this.adaptiveMarketClarityImpact = adaptiveMarketClarityImpact; }

    public double getAdaptiveLlmConfidenceImpact() { return adaptiveLlmConfidenceImpact; }
    public void setAdaptiveLlmConfidenceImpact(double adaptiveLlmConfidenceImpact) { this.adaptiveLlmConfidenceImpact = adaptiveLlmConfidenceImpact; }

    public double getAdaptiveBlendMinRatio() { return adaptiveBlendMinRatio; }
    public void setAdaptiveBlendMinRatio(double adaptiveBlendMinRatio) { this.adaptiveBlendMinRatio = adaptiveBlendMinRatio; }

    public double getAdaptiveBlendMaxRatio() { return adaptiveBlendMaxRatio; }
    public void setAdaptiveBlendMaxRatio(double adaptiveBlendMaxRatio) { this.adaptiveBlendMaxRatio = adaptiveBlendMaxRatio; }

    /**
     * 从环境变量加载评分配置
     *
     * @param envGetter 环境变量读取函数（key, defaultValue → value）
     */
    public void loadFromEnv(EnvGetter envGetter) {
        llmScoreBlendRatio = envGetter.getDouble("LLM_SCORE_BLEND_RATIO", 0.6);
        buyScoreThreshold = envGetter.getInt("BUY_SCORE_THRESHOLD", 70);
        sellScoreThreshold = envGetter.getInt("SELL_SCORE_THRESHOLD", 30);

        adaptiveBlendEnabled = envGetter.getBool("ADAPTIVE_BLEND_ENABLED", true);
        adaptiveBlendBaseRatio = envGetter.getDouble("ADAPTIVE_BLEND_BASE_RATIO", 0.5);
        adaptiveStrategyConfidenceImpact = envGetter.getDouble("ADAPTIVE_STRATEGY_CONFIDENCE_IMPACT", 0.2);
        adaptiveMarketClarityImpact = envGetter.getDouble("ADAPTIVE_MARKET_CLARITY_IMPACT", 0.15);
        adaptiveLlmConfidenceImpact = envGetter.getDouble("ADAPTIVE_LLM_CONFIDENCE_IMPACT", 0.1);
        adaptiveBlendMinRatio = envGetter.getDouble("ADAPTIVE_BLEND_MIN_RATIO", 0.2);
        adaptiveBlendMaxRatio = envGetter.getDouble("ADAPTIVE_BLEND_MAX_RATIO", 0.8);
    }

    /** 环境变量读取函数接口 */
    @FunctionalInterface
    public interface EnvGetter {
        String get(String key, String defaultValue);

        default int getInt(String key, int defaultValue) {
            String v = get(key, "");
            if (v.isEmpty()) return defaultValue;
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return defaultValue; }
        }

        default double getDouble(String key, double defaultValue) {
            String v = get(key, "");
            if (v.isEmpty()) return defaultValue;
            try { return Double.parseDouble(v); } catch (NumberFormatException e) { return defaultValue; }
        }

        default boolean getBool(String key, boolean defaultValue) {
            String v = get(key, "");
            if (v.isEmpty()) return defaultValue;
            return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
        }
    }
}
