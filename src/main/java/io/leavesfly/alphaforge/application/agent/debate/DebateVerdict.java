package io.leavesfly.alphaforge.application.agent.debate;

import java.util.List;

/**
 * 辩论裁决 — 裁判 Agent 的最终判决
 *
 * 对应论文 ContestTrade 的 "Contest Result"：
 * 裁判综合所有 Agent 的独立分析、交叉质询论点，
 * 做出最终交易决策。
 *
 * 裁判不是简单的"少数服从多数"，而是：
 * 1. 评估各方论点的逻辑强度和数据支撑
 * 2. 风控 Agent 的反对意见有"一票否决权"（可降级信号）
 * 3. 当多方分歧巨大时，自动降低置信度
 */
public class DebateVerdict {

    /** 最终信号（strong_buy/buy/neutral/sell/strong_sell） */
    private final String finalSignal;

    /** 最终评分（0-100） */
    private final int finalScore;

    /** 最终置信度（高/中等/低） */
    private final String confidence;

    /** 裁判推理过程 */
    private final String reasoning;

    /** 关键结论 */
    private final List<String> keyConclusions;

    /** 风险提示 */
    private final String riskNote;

    /** 操作建议 */
    private final String operationAdvice;

    /** 被采纳的 Agent 名称列表 */
    private final List<String> adoptedAgents;

    /** 被否决的 Agent 名称列表 */
    private final List<String> rejectedAgents;

    /** 辩论共识度（0-1，1=完全一致，0=完全分歧） */
    private final double consensusLevel;

    /** 原始综合评分（无辩论时 LLM 综合给出的评分，用于对比） */
    private final int originalScore;

    /** 评分调整幅度（finalScore - originalScore） */
    public int getScoreAdjustment() {
        return finalScore - originalScore;
    }

    /** 信号是否被辩论降级（如从 buy 降为 neutral） */
    public boolean isSignalDowngraded() {
        return !finalSignal.equals(originalSignal);
    }

    private final String originalSignal;

    public DebateVerdict(String finalSignal, int finalScore, String confidence,
                          String reasoning, List<String> keyConclusions,
                          String riskNote, String operationAdvice,
                          List<String> adoptedAgents, List<String> rejectedAgents,
                          double consensusLevel,
                          int originalScore, String originalSignal) {
        this.finalSignal = finalSignal;
        this.finalScore = finalScore;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.keyConclusions = keyConclusions != null ? keyConclusions : List.of();
        this.riskNote = riskNote;
        this.operationAdvice = operationAdvice;
        this.adoptedAgents = adoptedAgents != null ? adoptedAgents : List.of();
        this.rejectedAgents = rejectedAgents != null ? rejectedAgents : List.of();
        this.consensusLevel = consensusLevel;
        this.originalScore = originalScore;
        this.originalSignal = originalSignal;
    }

    public String getFinalSignal() { return finalSignal; }
    public int getFinalScore() { return finalScore; }
    public String getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public List<String> getKeyConclusions() { return keyConclusions; }
    public String getRiskNote() { return riskNote; }
    public String getOperationAdvice() { return operationAdvice; }
    public List<String> getAdoptedAgents() { return adoptedAgents; }
    public List<String> getRejectedAgents() { return rejectedAgents; }
    public double getConsensusLevel() { return consensusLevel; }
    public int getOriginalScore() { return originalScore; }
    public String getOriginalSignal() { return originalSignal; }
}
