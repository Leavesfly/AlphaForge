package io.leavesfly.alphaforge.application.strategy.template;

import java.util.List;
import java.util.Map;

/**
 * 策略模板：预置的策略骨架，用户可基于模板快速创建自定义策略
 */
public class StrategyTemplate {

    private String templateId;
    private String label;
    private String description;
    private String category;
    private String yamlContent;

    public StrategyTemplate(String templateId, String label, String description,
                            String category, String yamlContent) {
        this.templateId = templateId;
        this.label = label;
        this.description = description;
        this.category = category;
        this.yamlContent = yamlContent;
    }

    public String getTemplateId() { return templateId; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getYamlContent() { return yamlContent; }
}
