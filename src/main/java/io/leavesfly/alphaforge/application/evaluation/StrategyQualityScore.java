package io.leavesfly.alphaforge.application.evaluation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略质量评分 — 多维度统一评估结果
 *
 * 对应论文 AlphaForgeBench 的核心思想：
 * 将策略回测的多个维度指标统一为一个可比较的质量评分。
 *
 * 评分维度（各 0-100）：
 * 1. 收益能力 — 年化收益、超额收益
 * 2. 风险控制 — 最大回撤、夏普比率
 * 3. 胜率质量 — 胜率、盈亏比
 * 4. 稳健性   — Walk-Forward 样本外表现、过拟合比率
 * 5. 成本效率 — 换手率、交易频率合理性
 * 6. 一致性   — 信号准确率、策略命中稳定性
 *
 * 综合质量等级：
 * A (≥85): 优秀 — 可加大配置
 * B (70-84): 良好 — 正常使用
 * C (55-69): 一般 — 需优化
 * D (<55): 较差 — 建议下线或重构
 */
public class StrategyQualityScore {

    /** 综合评分（0-100） */
    private final double overallScore;

    /** 质量等级 */
    private final QualityGrade grade;

    /** 各维度评分 */
    private final Map<String, DimensionScore> dimensionScores;

    /** 评分摘要文本 */
    private final String summary;

    /** 改进建议 */
    private final List<String> improvementSuggestions;

    public enum QualityGrade {
        A("优秀", 85, 100),
        B("良好", 70, 84),
        C("一般", 55, 69),
        D("较差", 0, 54);

        public final String label;
        public final int minScore;
        public final int maxScore;

        QualityGrade(String label, int minScore, int maxScore) {
            this.label = label;
            this.minScore = minScore;
            this.maxScore = maxScore;
        }

        public static QualityGrade fromScore(double score) {
            for (QualityGrade g : values()) {
                if (score >= g.minScore && score <= g.maxScore) return g;
            }
            return D;
        }
    }

    /**
     * 单维度评分
     */
    public record DimensionScore(
            String dimensionName,
            double score,
            double weight,
            String detail,
            List<String> issues
    ) {
        public DimensionScore(String name, double score, double weight, String detail) {
            this(name, score, weight, detail, List.of());
        }
    }

    public StrategyQualityScore(double overallScore,
                                  Map<String, DimensionScore> dimensionScores,
                                  String summary,
                                  List<String> improvementSuggestions) {
        this.overallScore = overallScore;
        this.grade = QualityGrade.fromScore(overallScore);
        this.dimensionScores = dimensionScores;
        this.summary = summary;
        this.improvementSuggestions = improvementSuggestions;
    }

    public double getOverallScore() { return overallScore; }
    public QualityGrade getGrade() { return grade; }
    public Map<String, DimensionScore> getDimensionScores() { return dimensionScores; }
    public String getSummary() { return summary; }
    public List<String> getImprovementSuggestions() { return improvementSuggestions; }

    /** 获取指定维度的评分 */
    public DimensionScore getDimension(String name) {
        return dimensionScores.get(name);
    }

    /** 转为 Map 便于 API 返回 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("overall_score", String.format("%.1f", overallScore));
        map.put("grade", grade.name() + " (" + grade.label + ")");
        Map<String, Object> dims = new LinkedHashMap<>();
        for (Map.Entry<String, DimensionScore> e : dimensionScores.entrySet()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("score", String.format("%.1f", e.getValue().score()));
            d.put("weight", String.format("%.0f%%", e.getValue().weight() * 100));
            d.put("detail", e.getValue().detail());
            d.put("issues", e.getValue().issues());
            dims.put(e.getKey(), d);
        }
        map.put("dimensions", dims);
        map.put("summary", summary);
        map.put("suggestions", improvementSuggestions);
        return map;
    }
}
