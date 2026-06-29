package io.leavesfly.alphaforge.application.evaluation;

import java.util.List;
import java.util.Map;

/**
 * LLM 分析报告质量评估结果
 *
 * 对应论文 AlphaForgeBench 的 LLM 评估维度和 PHANTOM 的幻觉检测思想：
 * 对 LLM 生成的股票分析报告做自动化质量评估。
 *
 * 评估维度：
 * 1. 数据准确性 — 报告引用的数据是否与实际数据一致（幻觉检测）
 * 2. 逻辑一致性 — 信号与评分是否匹配、推理过程是否自洽
 * 3. 完整性     — 是否覆盖所有关键分析维度
 * 4. 可操作性   — 是否给出明确的入场/止损/目标价
 * 5. 风险披露   — 是否充分披露风险
 */
public class LlmAnalysisQuality {

    /** 综合质量评分（0-100） */
    private final double overallScore;

    /** 质量等级 */
    private final QualityLevel level;

    /** 各维度评分 */
    private final Map<String, DimensionResult> dimensions;

    /** 检测到的幻觉（报告声称但数据中不存在的数据点） */
    private final List<String> hallucinations;

    /** 逻辑矛盾列表 */
    private final List<String> logicalContradictions;

    /** 缺失的分析维度 */
    private final List<String> missingDimensions;

    /** 改进建议 */
    private final List<String> suggestions;

    public enum QualityLevel {
        EXCELLENT("优秀", 85),
        GOOD("良好", 70),
        ACCEPTABLE("可接受", 55),
        POOR("较差", 0);

        public final String label;
        public final int minScore;

        QualityLevel(String label, int minScore) {
            this.label = label;
            this.minScore = minScore;
        }

        public static QualityLevel fromScore(double score) {
            for (QualityLevel l : values()) {
                if (score >= l.minScore) return l;
            }
            return POOR;
        }
    }

    public record DimensionResult(
            String dimension,
            double score,
            String detail,
            List<String> issues
    ) {
        public DimensionResult(String dimension, double score, String detail) {
            this(dimension, score, detail, List.of());
        }
    }

    public LlmAnalysisQuality(double overallScore,
                                Map<String, DimensionResult> dimensions,
                                List<String> hallucinations,
                                List<String> logicalContradictions,
                                List<String> missingDimensions,
                                List<String> suggestions) {
        this.overallScore = overallScore;
        this.level = QualityLevel.fromScore(overallScore);
        this.dimensions = dimensions;
        this.hallucinations = hallucinations;
        this.logicalContradictions = logicalContradictions;
        this.missingDimensions = missingDimensions;
        this.suggestions = suggestions;
    }

    public double getOverallScore() { return overallScore; }
    public QualityLevel getLevel() { return level; }
    public Map<String, DimensionResult> getDimensions() { return dimensions; }
    public List<String> getHallucinations() { return hallucinations; }
    public List<String> getLogicalContradictions() { return logicalContradictions; }
    public List<String> getMissingDimensions() { return missingDimensions; }
    public List<String> getSuggestions() { return suggestions; }

    /** 是否存在幻觉 */
    public boolean hasHallucinations() { return !hallucinations.isEmpty(); }

    /** 是否存在逻辑矛盾 */
    public boolean hasLogicalContradictions() { return !logicalContradictions.isEmpty(); }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("overall_score", String.format("%.1f", overallScore));
        map.put("level", level.name() + " (" + level.label + ")");
        map.put("has_hallucinations", hasHallucinations());
        map.put("hallucinations", hallucinations);
        map.put("logical_contradictions", logicalContradictions);
        map.put("missing_dimensions", missingDimensions);
        map.put("suggestions", suggestions);
        Map<String, Object> dims = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, DimensionResult> e : dimensions.entrySet()) {
            Map<String, Object> d = new java.util.LinkedHashMap<>();
            d.put("score", String.format("%.1f", e.getValue().score()));
            d.put("detail", e.getValue().detail());
            d.put("issues", e.getValue().issues());
            dims.put(e.getKey(), d);
        }
        map.put("dimensions", dims);
        return map;
    }
}
