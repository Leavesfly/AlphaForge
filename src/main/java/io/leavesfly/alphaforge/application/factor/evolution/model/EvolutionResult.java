package io.leavesfly.alphaforge.application.factor.evolution.model;

import java.util.List;

/**
 * 单轮进化结果 — 一次进化循环的完整输出
 */
public class EvolutionResult {

    /** 进化代数 */
    private final int generation;

    /** 本轮生成的因子候选数 */
    private final int candidatesGenerated;

    /** 本轮通过评估的因子数 */
    private final int candidatesPassed;

    /** 本轮被提升到生产库的因子数 */
    private final int candidatesPromoted;

    /** 本轮 Top 因子列表 */
    private final List<FactorEvaluation> topEvaluations;

    /** 本轮所有因子候选（含评估结果） */
    private final List<FactorCandidate> allCandidates;

    /** 本轮进化耗时（毫秒） */
    private final long durationMs;

    /** 是否达到收敛条件（连续 N 代无显著提升） */
    private final boolean converged;

    /** 收敛原因（若 converged=true） */
    private final String convergenceReason;

    public EvolutionResult(int generation, int candidatesGenerated, int candidatesPassed,
                            int candidatesPromoted, List<FactorEvaluation> topEvaluations,
                            List<FactorCandidate> allCandidates, long durationMs,
                            boolean converged, String convergenceReason) {
        this.generation = generation;
        this.candidatesGenerated = candidatesGenerated;
        this.candidatesPassed = candidatesPassed;
        this.candidatesPromoted = candidatesPromoted;
        this.topEvaluations = topEvaluations;
        this.allCandidates = allCandidates;
        this.durationMs = durationMs;
        this.converged = converged;
        this.convergenceReason = convergenceReason;
    }

    public int getGeneration() { return generation; }
    public int getCandidatesGenerated() { return candidatesGenerated; }
    public int getCandidatesPassed() { return candidatesPassed; }
    public int getCandidatesPromoted() { return candidatesPromoted; }
    public List<FactorEvaluation> getTopEvaluations() { return topEvaluations; }
    public List<FactorCandidate> getAllCandidates() { return allCandidates; }
    public long getDurationMs() { return durationMs; }
    public boolean isConverged() { return converged; }
    public String getConvergenceReason() { return convergenceReason; }

    @Override
    public String toString() {
        return String.format("EvolutionResult{gen=%d, generated=%d, passed=%d, promoted=%d, converged=%s, duration=%dms}",
                generation, candidatesGenerated, candidatesPassed, candidatesPromoted, converged, durationMs);
    }
}
