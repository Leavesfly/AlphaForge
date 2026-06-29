package io.leavesfly.alphaforge.application.agent.debate;

import java.util.List;

/**
 * 辩论论点 — 一个 Agent 在某轮辩论中的观点
 *
 * 对应论文 ContestTrade 的 "Contest Argument" 和 FactorMAD 的 "Debate Message"。
 * 每个 Agent 看到其他 Agent 的结论后，形成支持或反对的论点。
 */
public class DebateArgument {

    /** Agent 名称 */
    private final String agentName;

    /** Agent 角色（technical / fundamental / risk / sentiment） */
    private final String role;

    /** 立场：SUPPORT（支持）/ OPPOSE（反对）/ NEUTRAL（中立） */
    private final Stance stance;

    /** 论点内容（详细论证） */
    private final String argument;

    /** 针对的对手 Agent 名称（null 表示针对整体结论） */
    private final String targetAgent;

    /** 关键证据列表 */
    private final List<String> evidence;

    /** 该 Agent 原始评分 */
    private final Integer originalScore;

    /** 该 Agent 原始信号 */
    private final String originalSignal;

    public enum Stance {
        SUPPORT,   // 支持当前综合结论
        OPPOSE,    // 反对当前综合结论
        NEUTRAL    // 中立，提出补充视角
    }

    public DebateArgument(String agentName, String role, Stance stance,
                           String argument, String targetAgent,
                           List<String> evidence,
                           Integer originalScore, String originalSignal) {
        this.agentName = agentName;
        this.role = role;
        this.stance = stance;
        this.argument = argument;
        this.targetAgent = targetAgent;
        this.evidence = evidence != null ? evidence : List.of();
        this.originalScore = originalScore;
        this.originalSignal = originalSignal;
    }

    public String getAgentName() { return agentName; }
    public String getRole() { return role; }
    public Stance getStance() { return stance; }
    public String getArgument() { return argument; }
    public String getTargetAgent() { return targetAgent; }
    public List<String> getEvidence() { return evidence; }
    public Integer getOriginalScore() { return originalScore; }
    public String getOriginalSignal() { return originalSignal; }

    @Override
    public String toString() {
        return String.format("[%s/%s] %s → %s", agentName, stance, argument,
                targetAgent != null ? "(针对 " + targetAgent + ")" : "");
    }
}
