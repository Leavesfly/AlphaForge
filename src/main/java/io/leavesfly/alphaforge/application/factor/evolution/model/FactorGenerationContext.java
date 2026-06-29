package io.leavesfly.alphaforge.application.factor.evolution.model;

import java.util.List;
import java.util.Map;

/**
 * 因子生成上下文 — 提供给 LLM 生成因子时的上下文信息
 *
 * 对应论文 FactorMiner 中的 "Task Context"：
 * 包含当前市场状态、已有因子表现、失败模式等，
 * 使 LLM 在生成新因子时拥有充分的环境感知。
 */
public class FactorGenerationContext {

    /** 当前市场阶段（bull / bear / consolidation） */
    private final String marketPhase;

    /** 评估股票池（用于 IC 计算的标的列表） */
    private final List<String> evaluationUniverse;

    /** 回测起始日期（ISO 格式） */
    private final String backtestStartDate;

    /** 回测结束日期 */
    private final String backtestEndDate;

    /** 当前已有的因子名称列表（避免重复生成） */
    private final List<String> existingFactorNames;

    /** 当前因子库中的因子分类 */
    private final Map<String, List<String>> existingCategories;

    /** 进化代数（第几轮进化） */
    private final int evolutionGeneration;

    /** 上一代 Top 因子（供变异参考） */
    private final List<FactorEvolutionRecord> topPerformers;

    /** 上一代失败模式（供反向变异参考） */
    private final List<FailurePattern> failurePatterns;

    /** 进化记忆提示文本（LLM 可读的经验总结） */
    private final String evolutionHint;

    /** 额外参数 */
    private final Map<String, Object> extraParams;

    public FactorGenerationContext(String marketPhase, List<String> evaluationUniverse,
                                    String backtestStartDate, String backtestEndDate,
                                    List<String> existingFactorNames,
                                    Map<String, List<String>> existingCategories,
                                    int evolutionGeneration,
                                    List<FactorEvolutionRecord> topPerformers,
                                    List<FailurePattern> failurePatterns,
                                    String evolutionHint,
                                    Map<String, Object> extraParams) {
        this.marketPhase = marketPhase;
        this.evaluationUniverse = evaluationUniverse;
        this.backtestStartDate = backtestStartDate;
        this.backtestEndDate = backtestEndDate;
        this.existingFactorNames = existingFactorNames;
        this.existingCategories = existingCategories;
        this.evolutionGeneration = evolutionGeneration;
        this.topPerformers = topPerformers;
        this.failurePatterns = failurePatterns;
        this.evolutionHint = evolutionHint;
        this.extraParams = extraParams;
    }

    public String getMarketPhase() { return marketPhase; }
    public List<String> getEvaluationUniverse() { return evaluationUniverse; }
    public String getBacktestStartDate() { return backtestStartDate; }
    public String getBacktestEndDate() { return backtestEndDate; }
    public List<String> getExistingFactorNames() { return existingFactorNames; }
    public Map<String, List<String>> getExistingCategories() { return existingCategories; }
    public int getEvolutionGeneration() { return evolutionGeneration; }
    public List<FactorEvolutionRecord> getTopPerformers() { return topPerformers; }
    public List<FailurePattern> getFailurePatterns() { return failurePatterns; }
    public String getEvolutionHint() { return evolutionHint; }
    public Map<String, Object> getExtraParams() { return extraParams; }

    /** 是否为首轮进化（无历史经验） */
    public boolean isFirstGeneration() {
        return evolutionGeneration == 0;
    }
}
