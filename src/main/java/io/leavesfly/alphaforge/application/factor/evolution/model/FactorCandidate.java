package io.leavesfly.alphaforge.application.factor.evolution.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 因子候选 — LLM 生成的一个待评估因子
 *
 * 对应论文 FactorMiner 中的 "Skill" 概念和 AlphaAgentEvo 中的 "Alpha Expression"。
 * 每个因子候选携带完整的遗传信息（父代、变异类型、生成代数），
 * 使进化过程可追溯、可复现。
 */
public class FactorCandidate {

    /** 因子唯一标识（UUID） */
    private final String factorId;

    /** 因子名称（人类可读，如 "vol_weighted_momentum_20d"） */
    private final String factorName;

    /** 因子计算表达式（Java 代码片段或 DSL 表达式） */
    private final String factorExpression;

    /** 因子类型 */
    private final FactorType factorType;

    /** 因子分类（momentum / mean_reversion / volatility / volume / trend / custom） */
    private final String category;

    /** 因子描述（LLM 生成的自然语言说明） */
    private final String description;

    /** 生成代数（第几轮进化，0 = 初始生成） */
    private final int generationRound;

    /** 父代因子 ID（变异/交叉的来源，INITIAL 时为 null） */
    private final String parentFactorId;

    /** 第二父代 ID（仅 CROSSBREED 时有值） */
    private final String secondParentFactorId;

    /** 变异类型 */
    private final MutationType mutationType;

    /** LLM 生成的因子参数（如周期、阈值等） */
    private final Map<String, Object> parameters;

    /** 适用市场条件（如 "bull_trend", "high_volatility"） */
    private final String marketCondition;

    /** 创建时间 */
    private final LocalDateTime createdAt;

    private FactorCandidate(Builder builder) {
        this.factorId = builder.factorId != null ? builder.factorId : UUID.randomUUID().toString();
        this.factorName = builder.factorName;
        this.factorExpression = builder.factorExpression;
        this.factorType = builder.factorType;
        this.category = builder.category;
        this.description = builder.description;
        this.generationRound = builder.generationRound;
        this.parentFactorId = builder.parentFactorId;
        this.secondParentFactorId = builder.secondParentFactorId;
        this.mutationType = builder.mutationType;
        this.parameters = builder.parameters;
        this.marketCondition = builder.marketCondition;
        this.createdAt = LocalDateTime.now();
    }

    // ===== Getters =====

    public String getFactorId() { return factorId; }
    public String getFactorName() { return factorName; }
    public String getFactorExpression() { return factorExpression; }
    public FactorType getFactorType() { return factorType; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public int getGenerationRound() { return generationRound; }
    public String getParentFactorId() { return parentFactorId; }
    public String getSecondParentFactorId() { return secondParentFactorId; }
    public MutationType getMutationType() { return mutationType; }
    public Map<String, Object> getParameters() { return parameters; }
    public String getMarketCondition() { return marketCondition; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** 是否为初始生成（无父代） */
    public boolean isInitial() {
        return mutationType == MutationType.INITIAL;
    }

    /** 是否为交叉繁殖 */
    public boolean isCrossbreed() {
        return mutationType == MutationType.CROSSBREED;
    }

    // ===== Builder =====

    public static class Builder {
        private String factorId;
        private String factorName;
        private String factorExpression;
        private FactorType factorType = FactorType.SIMPLE;
        private String category = "custom";
        private String description = "";
        private int generationRound = 0;
        private String parentFactorId;
        private String secondParentFactorId;
        private MutationType mutationType = MutationType.INITIAL;
        private Map<String, Object> parameters;
        private String marketCondition = "any";

        public Builder factorId(String factorId) { this.factorId = factorId; return this; }
        public Builder factorName(String factorName) { this.factorName = factorName; return this; }
        public Builder factorExpression(String factorExpression) { this.factorExpression = factorExpression; return this; }
        public Builder factorType(FactorType factorType) { this.factorType = factorType; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder generationRound(int generationRound) { this.generationRound = generationRound; return this; }
        public Builder parentFactorId(String parentFactorId) { this.parentFactorId = parentFactorId; return this; }
        public Builder secondParentFactorId(String secondParentFactorId) { this.secondParentFactorId = secondParentFactorId; return this; }
        public Builder mutationType(MutationType mutationType) { this.mutationType = mutationType; return this; }
        public Builder parameters(Map<String, Object> parameters) { this.parameters = parameters; return this; }
        public Builder marketCondition(String marketCondition) { this.marketCondition = marketCondition; return this; }

        public FactorCandidate build() {
            if (factorName == null || factorName.isBlank()) {
                throw new IllegalArgumentException("factorName 不能为空");
            }
            if (factorExpression == null || factorExpression.isBlank()) {
                throw new IllegalArgumentException("factorExpression 不能为空");
            }
            return new FactorCandidate(this);
        }
    }
}
