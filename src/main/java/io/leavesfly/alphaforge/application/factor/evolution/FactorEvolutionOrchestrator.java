package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.application.factor.evolution.model.EvolutionResult;

/**
 * 因子进化编排器 — 驱动 生成→评估→选择→变异 的完整进化闭环
 *
 * 对应论文：
 * - FactorMiner: "Self-Evolving Agent" — Agent 自主驱动因子发现循环
 * - AlphaAgentEvo: "Agentic RL Framework" — RL 驱动的进化编排
 *
 * 进化闭环流程：
 *
 *   ┌──→ generateInitialFactors (第 0 代)
 *   │         │
 *   │         ▼
 *   │    evaluateFactors (IC/IR/回测)
 *   │         │
 *   │         ▼
 *   │    selectTopFactors (按综合评分排序)
 *   │         │
 *   │         ▼
 *   │    recordEvolution (写入记忆)
 *   │         │
 *   │         ▼
 *   │    promoteValidated (提升到因子库)
 *   │         │
 *   │         ▼
 *   │    checkConvergence ──→ 未收敛 ──→ mutateFactors + crossbreedFactors + inverseMutate
 *   │         │                                    │
 *   │         │                                    └──→ 下一代 candidates
 *   │    已收敛
 *   │         │
 *   │         ▼
 *   └──  return EvolutionResult (最终结果)
 *
 * 与现有系统的集成点：
 * 1. FactorGeneratorAgent 实现 SubAgent 接口 → 可注册到 MultiAgentOrchestrator
 * 2. FactorEvaluator 复用 BacktestSimulator → 因子深度回测
 * 3. FactorEvolutionMemory 整合 ExperienceMemory → 统一经验注入
 * 4. 提升的因子注册到 FactorLibrary → 参与实盘分析信号生成
 */
public interface FactorEvolutionOrchestrator {

    /**
     * 执行一轮完整的进化循环
     *
     * @param config 进化配置
     * @return 进化结果
     */
    EvolutionResult runEvolutionCycle(FactorEvolutionConfig config);

    /**
     * 使用默认配置执行进化循环
     */
    default EvolutionResult runEvolutionCycle() {
        return runEvolutionCycle(FactorEvolutionConfig.defaultConfig());
    }

    /**
     * 执行多轮进化（直到收敛或达到最大代数）
     *
     * @param config        进化配置
     * @param maxGenerations 最大进化代数（覆盖 config 中的设置）
     * @return 最终一代的进化结果
     */
    EvolutionResult runMultiGenerationEvolution(FactorEvolutionConfig config, int maxGenerations);

    /**
     * 将已验证的因子提升到生产因子库
     *
     * 提升后，因子将通过 FactorLibrary.calculate() 参与实盘信号生成，
     * 并在后续分析 Pipeline 中被自动调用。
     *
     * @param factorId 因子 ID
     * @return 是否提升成功
     */
    boolean promoteFactor(String factorId);

    /**
     * 获取当前进化状态摘要
     *
     * @return 状态摘要（含当前代数、累计生成数、累计提升数、Top IC 等）
     */
    String getEvolutionStatusSummary();
}
