package io.leavesfly.stock.domain.model.entity;

import java.time.LocalDateTime;

/**
 * 异步分析任务实体
 * 对应 analysis_tasks 表
 */
public class AnalysisTask {

    private Long id;
    private String taskId;
    private String stockCode;
    private String stockCodes;
    private String taskType;
    private String status;
    private Integer progress;
    private String message;
    private String result;
    private String errorMessage;
    private String skills;
    private String analysisPhase;
    private String reportLanguage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockCodes() { return stockCodes; }
    public void setStockCodes(String stockCodes) { this.stockCodes = stockCodes; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getAnalysisPhase() { return analysisPhase; }
    public void setAnalysisPhase(String analysisPhase) { this.analysisPhase = analysisPhase; }
    public String getReportLanguage() { return reportLanguage; }
    public void setReportLanguage(String reportLanguage) { this.reportLanguage = reportLanguage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
