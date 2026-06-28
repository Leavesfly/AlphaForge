package io.leavesfly.stock.domain.model.entity.signal;

import java.time.LocalDateTime;

/**
 * 决策信号结果评估实体
 * 对应 decision_signal_outcomes 表
 */
public class DecisionSignalOutcome {

    /** 主键ID */
    private Long id;
    /** 关联信号ID */
    private Long signalId;
    /** 评估周期(short/mid/long) */
    private String horizon;
    /** 评估引擎版本 */
    private String engineVersion;
    /** 评估状态(pending/evaluated/unable) */
    private String evalStatus;
    /** 结果(hit/miss/partial) */
    private String outcome;
    /** 回报率(%) */
    private Double returnPct;
    /** 入场价 */
    private Double entryPrice;
    /** 出场价 */
    private Double exitPrice;
    /** 实际变动方向 */
    private String actualMovement;
    /** 预期变动方向 */
    private String directionExpected;
    /** 无法评估原因 */
    private String unableReason;
    /** 评估时间 */
    private LocalDateTime evaluatedAt;
    /** 创建时间 */
    private LocalDateTime createdAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSignalId() { return signalId; }
    public void setSignalId(Long signalId) { this.signalId = signalId; }
    public String getHorizon() { return horizon; }
    public void setHorizon(String horizon) { this.horizon = horizon; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public String getEvalStatus() { return evalStatus; }
    public void setEvalStatus(String evalStatus) { this.evalStatus = evalStatus; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public Double getReturnPct() { return returnPct; }
    public void setReturnPct(Double returnPct) { this.returnPct = returnPct; }
    public Double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(Double entryPrice) { this.entryPrice = entryPrice; }
    public Double getExitPrice() { return exitPrice; }
    public void setExitPrice(Double exitPrice) { this.exitPrice = exitPrice; }
    public String getActualMovement() { return actualMovement; }
    public void setActualMovement(String actualMovement) { this.actualMovement = actualMovement; }
    public String getDirectionExpected() { return directionExpected; }
    public void setDirectionExpected(String directionExpected) { this.directionExpected = directionExpected; }
    public String getUnableReason() { return unableReason; }
    public void setUnableReason(String unableReason) { this.unableReason = unableReason; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
