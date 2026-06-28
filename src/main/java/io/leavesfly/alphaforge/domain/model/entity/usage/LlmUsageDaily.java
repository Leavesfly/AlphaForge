package io.leavesfly.alphaforge.domain.model.entity.usage;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * LLM每日用量统计实体
 * 对应 llm_usage_daily 表
 */
public class LlmUsageDaily {

    /** 主键ID */
    private Long id;
    /** 统计日期 */
    private LocalDate usageDate;
    /** LLM模型名称 */
    private String model;
    /** 服务商(openai/anthropic/qwen等) */
    private String provider;
    /** 请求次数 */
    private Integer requestCount;
    /** 输入Token数 */
    private Integer promptTokens;
    /** 输出Token数 */
    private Integer completionTokens;
    /** 总Token数 */
    private Integer totalTokens;
    /** 总费用(美元) */
    private Double totalCost;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getUsageDate() { return usageDate; }
    public void setUsageDate(LocalDate usageDate) { this.usageDate = usageDate; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
