package io.leavesfly.alphaforge.application.strategy.generator;

/**
 * LLM 策略生成结果
 */
public class StrategyGenerationResult {

    /** 生成的策略 YAML 内容 */
    private String yamlContent;
    /** LLM 生成的策略 ID */
    private String strategyId;
    /** LLM 生成的策略名称 */
    private String label;
    /** LLM 建议的分类 */
    private String category;
    /** LLM 的推理说明 */
    private String reasoning;
    /** 是否通过校验 */
    private boolean valid;
    /** 校验错误信息 */
    private String validationErrors;

    public static StrategyGenerationResult of(String yaml, String strategyId, String label,
                                                String category, String reasoning) {
        StrategyGenerationResult result = new StrategyGenerationResult();
        result.yamlContent = yaml;
        result.strategyId = strategyId;
        result.label = label;
        result.category = category;
        result.reasoning = reasoning;
        result.valid = false;
        return result;
    }

    public String getYamlContent() { return yamlContent; }
    public void setYamlContent(String yamlContent) { this.yamlContent = yamlContent; }

    public String getStrategyId() { return strategyId; }
    public void setStrategyId(String strategyId) { this.strategyId = strategyId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getValidationErrors() { return validationErrors; }
    public void setValidationErrors(String validationErrors) { this.validationErrors = validationErrors; }
}
