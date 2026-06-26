package io.leavesfly.stock.application.strategy.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 智能选股配置段，对应 YAML 中 screening: 节点。
 */
public class ScreeningProfile {

    /** 策略参数，供规则或文档引用 */
    private Map<String, Object> parameters = Collections.emptyMap();
    /** 打分规则列表，由 ScreeningScoreEngine 逐条求值 */
    private List<Map<String, Object>> scoringRules = Collections.emptyList();
    /** 推荐理由模板：high / low 两档文案 */
    private Map<String, String> reasonTemplates = Collections.emptyMap();
    /** 行情缺字段时的兜底评分配置 */
    private Map<String, Object> fallback = Collections.emptyMap();

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters != null ? parameters : Collections.emptyMap();
    }

    public List<Map<String, Object>> getScoringRules() { return scoringRules; }
    public void setScoringRules(List<Map<String, Object>> scoringRules) {
        this.scoringRules = scoringRules != null ? scoringRules : Collections.emptyList();
    }

    public Map<String, String> getReasonTemplates() { return reasonTemplates; }
    public void setReasonTemplates(Map<String, String> reasonTemplates) {
        this.reasonTemplates = reasonTemplates != null ? reasonTemplates : Collections.emptyMap();
    }

    public Map<String, Object> getFallback() { return fallback; }
    public void setFallback(Map<String, Object> fallback) {
        this.fallback = fallback != null ? fallback : Collections.emptyMap();
    }
}
