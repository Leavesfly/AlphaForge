package io.leavesfly.alphaforge.application.agent.debate;

import java.util.List;

/**
 * 辩论轮次 — 一轮交叉质询的完整记录
 *
 * 辩论流程：
 * Round 1: 独立分析（现有 SubAgent.analyze）→ 产生初步结论
 * Round 2: 交叉质询（各 Agent 看到他人结论后辩论）→ 产生 DebateArgument 列表
 * Round 3: 裁判裁决（LLM 综合所有论点给出最终判决）→ 产生 DebateVerdict
 */
public class DebateRound {

    /** 轮次序号（1=首轮独立分析, 2=交叉质询, 3=裁决） */
    private final int roundNumber;

    /** 轮次类型 */
    private final RoundType roundType;

    /** 本轮所有论点（仅 Round 2 有值） */
    private final List<DebateArgument> arguments;

    /** 本轮汇总文本 */
    private final String summary;

    public enum RoundType {
        INDEPENDENT_ANALYSIS,  // 首轮：独立分析
        CROSS_EXAMINATION,     // 第二轮：交叉质询
        VERDICT                // 第三轮：裁判裁决
    }

    public DebateRound(int roundNumber, RoundType roundType,
                        List<DebateArgument> arguments, String summary) {
        this.roundNumber = roundNumber;
        this.roundType = roundType;
        this.arguments = arguments != null ? arguments : List.of();
        this.summary = summary;
    }

    public int getRoundNumber() { return roundNumber; }
    public RoundType getRoundType() { return roundType; }
    public List<DebateArgument> getArguments() { return arguments; }
    public String getSummary() { return summary; }

    /** 统计各立场的论点数量 */
    public long countByStance(DebateArgument.Stance stance) {
        return arguments.stream().filter(a -> a.getStance() == stance).count();
    }
}
