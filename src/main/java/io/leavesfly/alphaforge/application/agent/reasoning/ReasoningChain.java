package io.leavesfly.alphaforge.application.agent.reasoning;

import java.util.List;

/**
 * 结构化推理链 — 强制 LLM 分步推理，提升分析质量
 *
 * 对应论文 Trading-R1 和 RETuning 的核心思想：
 * 通过结构化推理（Chain-of-Thought）提升 LLM 量化分析的准确性和可解释性。
 *
 * 推理六步法：
 * 1. 观察（OBSERVE）    — 客观数据描述（指标数值、行情走势等）
 * 2. 判断（ASSESS）     — 基于数据的初步判断（趋势方向、估值高低等）
 * 3. 假设（HYPOTHESIZE）— 形成可验证的假设（如"短期可能反弹"）
 * 4. 验证（VERIFY）     — 多维度交叉验证假设（多指标互证）
 * 5. 风险（RISK_CHECK） — 反面论证/风险检查（假设不成立时的情况）
 * 6. 结论（CONCLUDE）   — 最终判断 + 信号 + 评分 + 置信度
 *
 * 每步推理必须：
 * - 基于上一步的结论，不允许跳步
 * - 引用具体数据作为支撑
 * - 在"验证"步骤需要至少 2 个独立证据
 * - 在"风险"步骤必须提出至少 1 个反面论点
 */
public class ReasoningChain {

    /** 推理步骤列表（按顺序） */
    private final List<ReasoningStep> steps;

    /** 推理框架类型 */
    private final ReasoningFramework framework;

    public enum ReasoningFramework {
        /** 单维度分析框架（用于 SubAgent） */
        SINGLE_DIMENSIONAL,
        /** 多维度综合框架（用于 MultiAgentOrchestrator 综合） */
        MULTI_DIMENSIONAL,
        /** 因子分析框架（用于因子评估） */
        FACTOR_ANALYSIS
    }

    public ReasoningChain(ReasoningFramework framework, List<ReasoningStep> steps) {
        this.framework = framework;
        this.steps = steps;
    }

    public List<ReasoningStep> getSteps() { return steps; }
    public ReasoningFramework getFramework() { return framework; }

    /**
     * 获取单维度分析的标准推理链
     */
    public static ReasoningChain forSingleDimension(String dimension) {
        return new ReasoningChain(ReasoningFramework.SINGLE_DIMENSIONAL, List.of(
                new ReasoningStep("1. 观察", "客观数据描述",
                        "列出当前可见的" + dimension + "数据，包括具体数值和变化趋势。仅陈述事实，不做判断。"),
                new ReasoningStep("2. 判断", "初步判断",
                        "基于上述数据，给出" + dimension + "维度的初步判断（如趋势方向、强弱程度等）。"),
                new ReasoningStep("3. 假设", "形成假设",
                        "基于初步判断，提出一个可验证的假设（如'短期内可能上涨'或'估值偏高'）。假设必须具体且可证伪。"),
                new ReasoningStep("4. 验证", "交叉验证",
                        "使用至少 2 个独立指标/数据点验证假设。如果证据矛盾，说明矛盾之处。"),
                new ReasoningStep("5. 风险", "反面论证",
                        "提出至少 1 个可能导致假设不成立的因素。考虑极端情况。"),
                new ReasoningStep("6. 结论", "最终判断",
                        "综合以上分析，给出最终结论。包括信号、评分、置信度和关键发现。")
        ));
    }

    /**
     * 获取多维度综合的标准推理链
     */
    public static ReasoningChain forMultiDimensional() {
        return new ReasoningChain(ReasoningFramework.MULTI_DIMENSIONAL, List.of(
                new ReasoningStep("1. 汇总", "各维度结论汇总",
                        "逐一列出各 Agent 的核心结论（技术面/基本面/风控/舆情），包括信号、评分和置信度。"),
                new ReasoningStep("2. 一致性", "一致性检查",
                        "检查各维度结论是否一致。如果存在分歧，明确指出分歧点和各方立场。"),
                new ReasoningStep("3. 权衡", "权重分配",
                        "根据当前市场阶段，确定各维度的权重。如牛市重技术面，熊市重风控。"),
                new ReasoningStep("4. 综合", "综合判断",
                        "基于加权后的各维度结论，形成综合判断。明确说明被采纳和被否决的观点。"),
                new ReasoningStep("5. 风险", "风险审查",
                        "审视综合结论的风险点。风控 Agent 的反对意见应被特别重视。"),
                new ReasoningStep("6. 决策", "最终决策",
                        "给出最终交易信号、评分、置信度和操作建议。")
        ));
    }
}
