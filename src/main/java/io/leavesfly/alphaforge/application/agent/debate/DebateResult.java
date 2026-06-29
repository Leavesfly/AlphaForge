package io.leavesfly.alphaforge.application.agent.debate;

import io.leavesfly.alphaforge.application.agent.SubAgent;

import java.util.List;

/**
 * 辩论完整结果 — 包含所有轮次记录和最终裁决
 */
public class DebateResult {

    /** 各 Agent 首轮独立分析结果 */
    private final List<SubAgent.AgentResult> initialResults;

    /** 交叉质询轮次 */
    private final DebateRound crossExaminationRound;

    /** 裁判裁决 */
    private final DebateVerdict verdict;

    /** 原始综合文本（无辩论时 LLM 综合给出的） */
    private final String originalSynthesis;

    /** 辩论总耗时（毫秒） */
    private final long totalDurationMs;

    /** 是否启用了辩论（false 表示回退到普通模式） */
    private final boolean debateEnabled;

    public DebateResult(List<SubAgent.AgentResult> initialResults,
                         DebateRound crossExaminationRound,
                         DebateVerdict verdict,
                         String originalSynthesis,
                         long totalDurationMs,
                         boolean debateEnabled) {
        this.initialResults = initialResults;
        this.crossExaminationRound = crossExaminationRound;
        this.verdict = verdict;
        this.originalSynthesis = originalSynthesis;
        this.totalDurationMs = totalDurationMs;
        this.debateEnabled = debateEnabled;
    }

    public List<SubAgent.AgentResult> getInitialResults() { return initialResults; }
    public DebateRound getCrossExaminationRound() { return crossExaminationRound; }
    public DebateVerdict getVerdict() { return verdict; }
    public String getOriginalSynthesis() { return originalSynthesis; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public boolean isDebateEnabled() { return debateEnabled; }

    /** 获取最终信号（优先使用辩论裁决） */
    public String getFinalSignal() {
        return verdict != null ? verdict.getFinalSignal() : extractSignalFromSynthesis();
    }

    /** 获取最终评分 */
    public int getFinalScore() {
        return verdict != null ? verdict.getFinalScore() : extractScoreFromSynthesis();
    }

    /** 获取最终分析文本 */
    public String getFinalAnalysis() {
        if (verdict != null) {
            return verdict.getReasoning();
        }
        return originalSynthesis;
    }

    private String extractSignalFromSynthesis() {
        if (originalSynthesis == null) return "neutral";
        // 尝试从 JSON 中提取 signal
        try {
            int idx = originalSynthesis.indexOf("\"signal\"");
            if (idx >= 0) {
                int colon = originalSynthesis.indexOf(":", idx);
                int quote1 = originalSynthesis.indexOf("\"", colon + 1);
                int quote2 = originalSynthesis.indexOf("\"", quote1 + 1);
                if (quote1 >= 0 && quote2 > quote1) {
                    return originalSynthesis.substring(quote1 + 1, quote2);
                }
            }
        } catch (Exception ignored) {}
        return "neutral";
    }

    private int extractScoreFromSynthesis() {
        if (originalSynthesis == null) return 50;
        try {
            int idx = originalSynthesis.indexOf("\"score\"");
            if (idx >= 0) {
                int colon = originalSynthesis.indexOf(":", idx);
                int numStart = colon + 1;
                while (numStart < originalSynthesis.length() &&
                        !Character.isDigit(originalSynthesis.charAt(numStart)) &&
                        originalSynthesis.charAt(numStart) != '-') numStart++;
                int numEnd = numStart;
                while (numEnd < originalSynthesis.length() &&
                        (Character.isDigit(originalSynthesis.charAt(numEnd)) ||
                         originalSynthesis.charAt(numEnd) == '.')) numEnd++;
                return (int) Double.parseDouble(originalSynthesis.substring(numStart, numEnd));
            }
        } catch (Exception ignored) {}
        return 50;
    }
}
