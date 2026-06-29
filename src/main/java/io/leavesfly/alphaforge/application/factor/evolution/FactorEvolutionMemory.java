package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.application.factor.evolution.model.FactorEvolutionRecord;
import io.leavesfly.alphaforge.application.factor.evolution.model.FailurePattern;
import io.leavesfly.alphaforge.application.factor.evolution.model.FactorGenerationContext;

import java.util.List;

/**
 * 因子进化记忆存储 — 跨代因子进化经验的持久化与检索
 *
 * 对应论文：
 * - FactorMiner: "Skills and Experience Memory"
 *   → 记录因子生命周期全链路，供 LLM 参考历史成功/失败经验
 * - AlphaAgentEvo: "Self-Evolving Memory"
 *   → 从失败因子中提取模式，驱动反向变异
 *
 * 与现有 ExperienceMemory 的关系：
 * - ExperienceMemory 记录信号级经验（技术指标快照 → 信号 → 效果）
 * - FactorEvolutionMemory 记录因子级经验（因子表达式 → IC/IR → 通过/淘汰）
 * - 两者通过 FactorExperienceBridge 统一注入 LLM prompt（见整合设计）
 */
public interface FactorEvolutionMemory {

    /**
     * 记录一次因子进化事件（生成/评估/提升/淘汰）
     *
     * @param record 因子进化记录
     */
    void recordEvolution(FactorEvolutionRecord record);

    /**
     * 获取指定因子的完整进化谱系
     *
     * @param factorId 因子 ID
     * @return 进化记录列表（按代数排序）
     */
    List<FactorEvolutionRecord> getEvolutionHistory(String factorId);

    /**
     * 获取全局 Top 表现因子（供变异参考）
     *
     * @param limit 返回数量
     * @return 按评估得分降序排列的因子记录
     */
    List<FactorEvolutionRecord> getTopPerformers(int limit);

    /**
     * 获取指定市场条件下的 Top 因子
     *
     * @param marketCondition 市场条件（如 "bull_trend", "high_volatility"）
     * @param limit           返回数量
     * @return 因子记录列表
     */
    List<FactorEvolutionRecord> getTopPerformersByCondition(String marketCondition, int limit);

    /**
     * 获取失败模式统计 — 从历史淘汰因子中提取共性特征
     *
     * @return 失败模式列表（按出现频率降序）
     */
    List<FailurePattern> getFailurePatterns();

    /**
     * 获取指定分类下的失败因子列表
     *
     * @param category 因子分类（momentum / mean_reversion 等）
     * @return 失败因子记录列表
     */
    List<FactorEvolutionRecord> getFailedFactorsByCategory(String category);

    /**
     * 构建进化记忆提示文本（注入到 LLM 因子生成 prompt 中）
     *
     * 参考 FactorMiner 的 "Experience Injection"：
     * 将历史成功因子的特征、失败因子的模式、当前市场条件下的最优因子
     * 格式化为 LLM 可读的提示文本。
     *
     * @param context 生成上下文
     * @return 进化记忆提示文本（无历史数据时返回空字符串）
     */
    String buildEvolutionHint(FactorGenerationContext context);

    /**
     * 获取当前进化代数
     */
    int getCurrentGeneration();

    /**
     * 检查收敛条件 — 连续 N 代无显著提升
     *
     * @param minGenerations 最小进化代数（避免过早收敛）
     * @param threshold      提升阈值（IC 提升幅度）
     * @return 是否已收敛
     */
    boolean isConverged(int minGenerations, double threshold);

    /**
     * 获取已提升到生产库的因子列表
     */
    List<FactorEvolutionRecord> getPromotedFactors();
}
