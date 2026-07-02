package io.leavesfly.alphaforge.application.strategy.generator;

import java.util.List;

/**
 * 策略生成上下文 — 封装 LLM 生成策略 YAML 所需的全部输入信息
 *
 * 包含：
 * - 用户自然语言描述
 * - 当前市场环境（可选）
 * - 参考策略列表（few-shot）
 * - 回测反馈信息（迭代优化时使用）
 */
public class StrategyGenerationContext {

    /** 用户的自然语言策略描述 */
    private String userDescription;
    /** 策略 ID 建议（可选，LLM 可参考） */
    private String suggestedId;
    /** 策略分类建议：technical/fundamental/sentiment/event */
    private String category;
    /** 当前市场阶段：bull/bear/range/recovery */
    private String marketPhase;
    /** 参考策略名称列表（用于 few-shot 注入） */
    private List<String> referenceStrategyIds;
    /** 上次回测结果摘要（迭代优化时注入） */
    private String lastBacktestSummary;
    /** 用户额外要求 */
    private String additionalRequirements;

    public String getUserDescription() { return userDescription; }
    public void setUserDescription(String userDescription) { this.userDescription = userDescription; }

    public String getSuggestedId() { return suggestedId; }
    public void setSuggestedId(String suggestedId) { this.suggestedId = suggestedId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getMarketPhase() { return marketPhase; }
    public void setMarketPhase(String marketPhase) { this.marketPhase = marketPhase; }

    public List<String> getReferenceStrategyIds() { return referenceStrategyIds; }
    public void setReferenceStrategyIds(List<String> referenceStrategyIds) { this.referenceStrategyIds = referenceStrategyIds; }

    public String getLastBacktestSummary() { return lastBacktestSummary; }
    public void setLastBacktestSummary(String lastBacktestSummary) { this.lastBacktestSummary = lastBacktestSummary; }

    public String getAdditionalRequirements() { return additionalRequirements; }
    public void setAdditionalRequirements(String additionalRequirements) { this.additionalRequirements = additionalRequirements; }

    /** 是否为迭代优化模式（有回测反馈） */
    public boolean isIterative() {
        return lastBacktestSummary != null && !lastBacktestSummary.isBlank();
    }
}
