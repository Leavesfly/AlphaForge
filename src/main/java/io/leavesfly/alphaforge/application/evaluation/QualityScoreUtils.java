package io.leavesfly.alphaforge.application.evaluation;

/**
 * 质量评分工具类 — 提供多维加权评分的通用工具方法
 *
 * 从原 AbstractQualityScorer 中提取的公共工具方法，
 * 替代继承关系，符合"组合优于继承"原则。
 */
public final class QualityScoreUtils {

    private QualityScoreUtils() {}

    /**
     * 将分数钳制到 0-100 范围
     */
    public static double clampScore(double score) {
        return Math.max(0, Math.min(100, score));
    }

    /**
     * 应用额外惩罚（如幻觉数量惩罚）
     *
     * @param baseScore       基础评分
     * @param penaltyPerIssue 每个问题的惩罚分
     * @param issueCount      问题数量
     * @return 惩罚后的评分（钳制到 0-100）
     */
    public static double applyPenalty(double baseScore, double penaltyPerIssue, int issueCount) {
        return clampScore(baseScore - penaltyPerIssue * issueCount);
    }
}
