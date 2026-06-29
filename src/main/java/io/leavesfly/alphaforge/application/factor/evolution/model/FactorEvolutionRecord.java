package io.leavesfly.alphaforge.application.factor.evolution.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 因子进化记录 — 存储在进化记忆中的持久化记录
 *
 * 对应论文 FactorMiner 中的 "Experience Memory"：
 * 记录因子从生成到评估到淘汰/提升的完整生命周期，
 * 使 LLM 在后续生成新因子时能参考历史经验和失败模式。
 */
public class FactorEvolutionRecord {

    private final String recordId;
    private final String factorId;
    private final String factorName;
    private final String factorExpression;
    private final int generationRound;
    private final MutationType mutationType;
    private final String parentFactorId;
    private final String secondParentFactorId;
    private final FactorType factorType;
    private final String category;
    private final String marketCondition;

    // ===== 评估快照 =====
    private final double evaluationScore;
    private final double ic;
    private final double ir;
    private final double sharpeRatio;
    private final FactorStatus status;

    // ===== 失败分析（仅 status=DEPRECATED 时有值） =====
    private final String failureReason;
    private final List<String> failurePatterns;

    // ===== LLM 生成推理（供后续参考） =====
    private final String generationReasoning;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FactorEvolutionRecord(String factorId, String factorName, String factorExpression,
                                  int generationRound, MutationType mutationType,
                                  String parentFactorId, String secondParentFactorId,
                                  FactorType factorType, String category, String marketCondition,
                                  double evaluationScore, double ic, double ir, double sharpeRatio,
                                  FactorStatus status, String failureReason,
                                  List<String> failurePatterns, String generationReasoning) {
        this.recordId = UUID.randomUUID().toString();
        this.factorId = factorId;
        this.factorName = factorName;
        this.factorExpression = factorExpression;
        this.generationRound = generationRound;
        this.mutationType = mutationType;
        this.parentFactorId = parentFactorId;
        this.secondParentFactorId = secondParentFactorId;
        this.factorType = factorType;
        this.category = category;
        this.marketCondition = marketCondition;
        this.evaluationScore = evaluationScore;
        this.ic = ic;
        this.ir = ir;
        this.sharpeRatio = sharpeRatio;
        this.status = status;
        this.failureReason = failureReason;
        this.failurePatterns = failurePatterns;
        this.generationReasoning = generationReasoning;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // ===== Getters =====

    public String getRecordId() { return recordId; }
    public String getFactorId() { return factorId; }
    public String getFactorName() { return factorName; }
    public String getFactorExpression() { return factorExpression; }
    public int getGenerationRound() { return generationRound; }
    public MutationType getMutationType() { return mutationType; }
    public String getParentFactorId() { return parentFactorId; }
    public String getSecondParentFactorId() { return secondParentFactorId; }
    public FactorType getFactorType() { return factorType; }
    public String getCategory() { return category; }
    public String getMarketCondition() { return marketCondition; }
    public double getEvaluationScore() { return evaluationScore; }
    public double getIc() { return ic; }
    public double getIr() { return ir; }
    public double getSharpeRatio() { return sharpeRatio; }
    public FactorStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public List<String> getFailurePatterns() { return failurePatterns; }
    public String getGenerationReasoning() { return generationReasoning; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** 是否为成功因子（通过评估） */
    public boolean isSuccessful() {
        return status == FactorStatus.VALIDATED || status == FactorStatus.PROMOTED;
    }

    /** 是否为失败因子 */
    public boolean isFailed() {
        return status == FactorStatus.DEPRECATED;
    }
}
