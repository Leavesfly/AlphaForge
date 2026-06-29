package io.leavesfly.alphaforge.application.factor.evolution;

import java.util.List;
import java.util.Map;

/**
 * 因子经验桥接器 — 统一因子级经验与信号级经验的注入通道
 *
 * 设计目的：
 * AlphaForge 已有两套记忆系统：
 * 1. ExperienceMemory — 信号级经验（技术指标快照 → 信号 → 效果）
 * 2. FactorEvolutionMemory — 因子级经验（因子表达式 → IC/IR → 通过/淘汰）
 *
 * 本接口将两者统一聚合，生成一个完整的 LLM prompt 注入文本，
 * 使 LLM 在分析股票时同时获得：
 * - "上次在类似技术条件下，信号效果如何"（来自 ExperienceMemory）
 * - "哪些因子在当前市场条件下表现最好/最差"（来自 FactorEvolutionMemory）
 *
 * 这对应论文 FactorMiner 中的 "Dual-Track Experience Injection"：
 * 短期经验（信号反馈）+ 长期经验（因子进化）共同驱动分析改进。
 */
public interface FactorExperienceBridge {

    /**
     * 构建统一经验提示文本 — 注入到股票分析 LLM prompt 中
     *
     * @param stockCode        股票代码
     * @param currentConditions 当前技术指标快照
     * @param marketPhase      当前市场阶段
     * @return 统一经验提示文本
     */
    String buildUnifiedExperienceHint(String stockCode,
                                       Map<String, Object> currentConditions,
                                       String marketPhase);

    /**
     * 构建因子分析建议 — 基于当前市场条件推荐使用哪些进化因子
     *
     * @param marketPhase 当前市场阶段
     * @param limit       推荐因子数量
     * @return 推荐因子列表（名称 + 简述 + 历史 IC）
     */
    List<FactorRecommendation> recommendFactors(String marketPhase, int limit);

    /**
     * 当信号被评估后，同时更新因子进化记忆
     *
     * 如果信号是基于某个进化因子生成的，则该因子的实际表现
     * 也会被反馈到 FactorEvolutionMemory 中，形成闭环。
     *
     * @param stockCode      股票代码
     * @param factorIds      涉及的因子 ID 列表
     * @param signalOutcome  信号评估结果（correct/incorrect/partial）
     * @param returnPct      实际收益率
     */
    void propagateSignalFeedback(String stockCode,
                                  List<String> factorIds,
                                  String signalOutcome,
                                  Double returnPct);

    /**
     * 因子推荐项
     */
    record FactorRecommendation(
            String factorId,
            String factorName,
            String description,
            double ic,
            double sharpeRatio,
            double overallScore,
            String marketCondition
    ) {}
}
