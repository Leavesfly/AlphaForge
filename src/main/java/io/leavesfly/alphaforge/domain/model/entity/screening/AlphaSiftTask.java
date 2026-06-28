package io.leavesfly.alphaforge.domain.model.entity.screening;

import java.time.LocalDateTime;

/**
 * AlphaSift选股任务实体
 * 对应 alphasift_tasks 表
 */
public class AlphaSiftTask {

    /** 主键ID */
    private Long id;
    /** 任务唯一标识(UUID) */
    private String taskId;
    /** 策略名称 */
    private String strategy;
    /** 市场(A/US/HK) */
    private String market;
    /** 状态(pending/running/completed/failed) */
    private String status;
    /** 进度(0-100) */
    private Integer progress;
    /** 参数JSON */
    private String parameters;
    /** 结果股票列表JSON */
    private String resultStocks;
    /** 结果数量 */
    private Integer resultCount;
    /** 错误信息 */
    private String errorMessage;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 完成时间 */
    private LocalDateTime completedAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    public String getResultStocks() { return resultStocks; }
    public void setResultStocks(String resultStocks) { this.resultStocks = resultStocks; }
    public Integer getResultCount() { return resultCount; }
    public void setResultCount(Integer resultCount) { this.resultCount = resultCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
