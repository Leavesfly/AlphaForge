package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.application.factor.evolution.model.FactorCandidate;
import io.leavesfly.alphaforge.application.factor.evolution.model.FactorGenerationContext;

import java.util.List;

/**
 * 因子生成器 Agent — LLM 驱动的因子自动发现与进化
 *
 * 对应论文：
 * - FactorMiner: "Self-Evolving Agent with Skills and Experience Memory for Financial Alpha Discovery"
 *   → 首轮通过 LLM 从零生成因子（generateInitialFactors）
 * - AlphaAgentEvo: "Evolution-Oriented Alpha Mining via Self-Evolving Agentic Reinforcement Learning"
 *   → 后续代通过变异/交叉/反向变异进化（mutateFactors / crossbreedFactors / inverseMutate）
 *
 * 设计原则：
 * 1. 与现有 SubAgent 体系兼容 — 可作为 SpecializedAgent 注册到 MultiAgentOrchestrator
 * 2. 利用 LlmPort.chatForStructuredOutput 确保因子表达式结构化输出
 * 3. 因子表达式为受限 DSL（安全沙箱执行），而非任意 Java 代码
 */
public interface FactorGeneratorAgent {

    /**
     * 首轮因子生成 — LLM 从零生成初始因子集合
     *
     * 参考 FactorMiner 的 "Skill Initialization"：
     * 根据市场阶段、已有因子库、因子分类需求，生成多样化的初始因子。
     *
     * @param context 生成上下文（含市场状态、已有因子、回测参数等）
     * @return 初始因子候选列表
     */
    List<FactorCandidate> generateInitialFactors(FactorGenerationContext context);

    /**
     * 因子变异 — 对 Top 因子进行变异生成下一代
     *
     * 参考 AlphaAgentEvo 的 "Mutation Operation"：
     * 对表现优秀的因子施加参数变异、表达式变异、条件特化等操作。
     *
     * @param topFactors 上一代最优因子列表
     * @param context    生成上下文
     * @return 变异后的因子候选列表
     */
    List<FactorCandidate> mutateFactors(List<FactorCandidate> topFactors, FactorGenerationContext context);

    /**
     * 因子交叉繁殖 — 将两个父代因子的特征组合
     *
     * 参考 AlphaAgentEvo 的 "Crossover Operation"：
     * 从 Top 因子中选取配对，交叉生成混合特征的新因子。
     *
     * @param parents 父代因子列表
     * @param context 生成上下文
     * @return 交叉繁殖后的因子候选列表
     */
    List<FactorCandidate> crossbreedFactors(List<FactorCandidate> parents, FactorGenerationContext context);

    /**
     * 反向变异 — 基于失败模式生成与错误因子相反方向的因子
     *
     * 参考 AlphaAgentEvo 的 "Inversion Mutation"：
     * 分析失败因子的共性问题，生成反向逻辑或条件修正的因子。
     *
     * @param failurePatterns 历史失败模式列表
     * @param context         生成上下文
     * @return 反向变异后的因子候选列表
     */
    List<FactorCandidate> inverseMutate(List<io.leavesfly.alphaforge.application.factor.evolution.model.FailurePattern> failurePatterns,
                                        FactorGenerationContext context);

    /**
     * 获取 Agent 名称（用于 MultiAgentOrchestrator 注册）
     */
    String getAgentName();

    /**
     * 获取 Agent 角色（固定为 "factor_generator"）
     */
    default String getRole() {
        return "factor_generator";
    }
}
