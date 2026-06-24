package io.leavesfly.stock.domain.model.entity;

import java.time.LocalDateTime;

/**
 * 决策信号结果评估实体
 * 对应 decision_signal_outcomes 表
 */
public class DecisionSignalOutcome {

    private Long id;
    private Long signalId;
    private String horizon;
    private String engineVersion;
    private String evalStatus;
    private String outcome;
    private Double returnPct;
    private Double entryPrice;
    private Double exitPrice;
    private String actualMovement;
    private String directionExpected;
    private String unableReason;
    private LocalDateTime evaluatedAt;
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
