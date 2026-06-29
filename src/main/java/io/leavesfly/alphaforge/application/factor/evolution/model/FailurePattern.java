package io.leavesfly.alphaforge.application.factor.evolution.model;

/**
 * 因子失败模式 — 从历史失败因子中提取的共性特征
 *
 * 对应论文 AlphaAgentEvo 中的 "Negative Reward Signal"：
 * 让 LLM 在后续生成中避开已知的失败路径。
 */
public class FailurePattern {

    /** 失败模式签名（如 "high_volatility + momentum_5d + bull_market"） */
    private final String patternSignature;

    /** 该模式出现的次数 */
    private final int occurrenceCount;

    /** 平均 IC（通常为负或接近零） */
    private final double avgIC;

    /** 平均评估得分 */
    private final double avgScore;

    /** 典型失败原因描述 */
    private final String failureDescription;

    /** 涉及的因子分类 */
    private final String factorCategory;

    public FailurePattern(String patternSignature, int occurrenceCount,
                           double avgIC, double avgScore,
                           String failureDescription, String factorCategory) {
        this.patternSignature = patternSignature;
        this.occurrenceCount = occurrenceCount;
        this.avgIC = avgIC;
        this.avgScore = avgScore;
        this.failureDescription = failureDescription;
        this.factorCategory = factorCategory;
    }

    public String getPatternSignature() { return patternSignature; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public double getAvgIC() { return avgIC; }
    public double getAvgScore() { return avgScore; }
    public String getFailureDescription() { return failureDescription; }
    public String getFactorCategory() { return factorCategory; }
}
