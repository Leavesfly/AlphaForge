package io.leavesfly.alphaforge.domain.model.entity.strategy;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户自定义策略实体，对应 custom_strategies 表。
 *
 * 存储 YAML 内容 + 生命周期状态 + 版本信息，与内置 YAML 策略共存于 StrategyCatalog。
 */
public class CustomStrategy {

    private Long id;
    /** 策略唯一标识，如 my_ma_cross */
    private String strategyId;
    /** 中文展示名 */
    private String label;
    /** 策略描述 */
    private String description;
    /** 分类: technical/fundamental/sentiment/event */
    private String category;
    /** 完整策略 YAML 内容 */
    private String yamlContent;
    /** 生命周期状态: DRAFT/TESTING/PUBLISHED/DEPRECATED */
    private String lifecycleState;
    /** 版本号 */
    private int version;
    /** 能力列表（逗号分隔: backtest,scoring,screening） */
    private String capabilities;
    /** 克隆来源策略 ID */
    private String sourceStrategyId;
    /** 校验状态: pending/valid/invalid */
    private String validationStatus;
    /** 校验错误信息（JSON 数组字符串） */
    private String validationErrors;
    /** 最近校验时间 */
    private LocalDateTime lastValidatedAt;
    /** 创建来源: api/bot/clone */
    private String createdBy;
    /** 备注 */
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStrategyId() { return strategyId; }
    public void setStrategyId(String strategyId) { this.strategyId = strategyId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getYamlContent() { return yamlContent; }
    public void setYamlContent(String yamlContent) { this.yamlContent = yamlContent; }

    public String getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(String lifecycleState) { this.lifecycleState = lifecycleState; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }

    public String getSourceStrategyId() { return sourceStrategyId; }
    public void setSourceStrategyId(String sourceStrategyId) { this.sourceStrategyId = sourceStrategyId; }

    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

    public String getValidationErrors() { return validationErrors; }
    public void setValidationErrors(String validationErrors) { this.validationErrors = validationErrors; }

    public LocalDateTime getLastValidatedAt() { return lastValidatedAt; }
    public void setLastValidatedAt(LocalDateTime lastValidatedAt) { this.lastValidatedAt = lastValidatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
