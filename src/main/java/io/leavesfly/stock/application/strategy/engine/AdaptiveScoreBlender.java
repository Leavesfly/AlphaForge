package io.leavesfly.stock.application.strategy.engine;

import java.util.Map;

/**
 * 自适应评分混合器 — 替代固定比例的 LLM/策略线性混合。
 *
 * 核心思路：根据策略引擎的置信度和市场环境的清晰度，动态调整
 * LLM 分析分与策略评分的混合权重，而非使用固定的 0.6/0.4 比例。
 *
 * 调整逻辑：
 * 1. 策略置信度：命中权重占比越高（earnedWeight/maxWeight），策略评分越可信
 * 2. 市场清晰度：明确趋势（牛市/熊市）下策略更可靠，震荡市 LLM 更有优势
 * 3. LLM 置信度：LLM 给出"高"置信度时提升其权重
 *
 * 最终 llmRatio 被限制在 [minRatio, maxRatio] 区间内，防止极端偏移。
 */
public class AdaptiveScoreBlender {

    private final double baseRatio;
    private final double strategyConfidenceImpact;
    private final double marketClarityImpact;
    private final double llmConfidenceImpact;
    private final double minRatio;
    private final double maxRatio;

    public AdaptiveScoreBlender(double baseRatio, double strategyConfidenceImpact,
                                 double marketClarityImpact, double llmConfidenceImpact,
                                 double minRatio, double maxRatio) {
        this.baseRatio = baseRatio;
        this.strategyConfidenceImpact = strategyConfidenceImpact;
        this.marketClarityImpact = marketClarityImpact;
        this.llmConfidenceImpact = llmConfidenceImpact;
        this.minRatio = minRatio;
        this.maxRatio = maxRatio;
    }

    /** 默认配置 */
    public static AdaptiveScoreBlender defaultConfig() {
        return new AdaptiveScoreBlender(0.5, 0.2, 0.15, 0.1, 0.2, 0.8);
    }

    /**
     * 计算自适应混合后的最终分数。
     *
     * @param llmScore           LLM 分析评分（0~100），若为 null 或 50 视为无明确观点
     * @param strategyScore      策略引擎综合评分（0~100）
     * @param earnedWeight       策略命中权重
     * @param maxWeight          策略总权重
     * @param marketContext      市场上下文（含 market_sentiment 等字段）
     * @param llmConfidence      LLM 置信度文本："高" / "中等" / "低" / null
     * @return 混合后的分数（0~100）及使用的 llmRatio
     */
    public BlendResult blend(Integer llmScore, int strategyScore,
                              int earnedWeight, int maxWeight,
                              Map<String, Object> marketContext, String llmConfidence) {
        // LLM 无明确观点时直接用策略分
        if (llmScore == null || llmScore == 50) {
            return new BlendResult(strategyScore, 0, "strategy_only");
        }

        double llmRatio = baseRatio;

        // 1. 策略置信度调整：命中权重占比越高，策略越可信，LLM 权重越低
        if (maxWeight > 0) {
            double strategyConfidence = (double) earnedWeight / maxWeight;
            llmRatio -= strategyConfidence * strategyConfidenceImpact;
        }

        // 2. 市场清晰度调整：明确趋势下策略更可靠
        String marketClarity = assessMarketClarity(marketContext);
        llmRatio += marketClarityImpact * switch (marketClarity) {
            case "uncertain" -> 1;   // 震荡市 → LLM 更有优势
            case "clear_bull" -> -0.5; // 牛市 → 策略更可靠
            case "clear_bear" -> -0.5; // 熊市 → 策略更可靠
            default -> 0;
        };

        // 3. LLM 置信度调整
        if (llmConfidence != null) {
            llmRatio += llmConfidenceImpact * switch (llmConfidence) {
                case "高" -> 1;
                case "低" -> -1;
                default -> 0;
            };
        }

        // 限制在安全区间
        llmRatio = Math.max(minRatio, Math.min(maxRatio, llmRatio));

        int blendedScore = (int) Math.round(llmScore * llmRatio + strategyScore * (1 - llmRatio));
        String source = String.format("adaptive_blend(llm=%.0f%%, strategy=%.0f%%)",
                llmRatio * 100, (1 - llmRatio) * 100);
        return new BlendResult(blendedScore, llmRatio, source);
    }

    /** 评估市场清晰度 */
    private String assessMarketClarity(Map<String, Object> marketContext) {
        if (marketContext == null || marketContext.isEmpty()) {
            return "unknown";
        }
        String sentiment = String.valueOf(marketContext.getOrDefault("market_sentiment", ""));
        if (sentiment.contains("乐观") || sentiment.contains("回暖")) return "clear_bull";
        if (sentiment.contains("悲观") || sentiment.contains("谨慎")) return "clear_bear";
        if (sentiment.contains("震荡") || sentiment.contains("盘整")) return "uncertain";
        return "unknown";
    }

    /** 混合结果 */
    public record BlendResult(int score, double llmRatio, String source) {}
}
