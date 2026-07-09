package io.leavesfly.alphaforge.application.strategy.lifecycle;

import io.leavesfly.alphaforge.application.evaluation.StrategyQualityScore;
import io.leavesfly.alphaforge.application.strategy.model.WalkForwardResult;

/**
 * 策略晋升评估结论。
 */
public class PromotionDecision {

    private final String strategyId;
    private final boolean promotable;
    private final StrategyQualityScore.QualityGrade grade;
    private final double overallScore;
    private final boolean walkForwardPassed;
    private final String reason;
    private final StrategyQualityScore qualityScore;
    private final WalkForwardResult walkForward;

    public PromotionDecision(String strategyId, boolean promotable,
                             StrategyQualityScore.QualityGrade grade, double overallScore,
                             boolean walkForwardPassed, String reason,
                             StrategyQualityScore qualityScore, WalkForwardResult walkForward) {
        this.strategyId = strategyId;
        this.promotable = promotable;
        this.grade = grade;
        this.overallScore = overallScore;
        this.walkForwardPassed = walkForwardPassed;
        this.reason = reason;
        this.qualityScore = qualityScore;
        this.walkForward = walkForward;
    }

    public String getStrategyId() { return strategyId; }
    public boolean isPromotable() { return promotable; }
    public StrategyQualityScore.QualityGrade getGrade() { return grade; }
    public double getOverallScore() { return overallScore; }
    public boolean isWalkForwardPassed() { return walkForwardPassed; }
    public String getReason() { return reason; }
    public StrategyQualityScore getQualityScore() { return qualityScore; }
    public WalkForwardResult getWalkForward() { return walkForward; }
}
