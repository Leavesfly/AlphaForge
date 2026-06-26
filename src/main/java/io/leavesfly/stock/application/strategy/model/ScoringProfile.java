package io.leavesfly.stock.application.strategy.model;

import java.util.Collections;
import java.util.Map;

/**
 * 综合分析评分配置段，对应 YAML 中 scoring: 节点。
 */
public class ScoringProfile {

    /** 命中该策略时累加的权重分 */
    private int scoreWeight;
    /** 触发条件，全部满足才算命中（AND 逻辑） */
    private Map<String, Object> conditions = Collections.emptyMap();
    /** 可选的展示名称，如龙头战法在评分场景下显示为「龙头首板」 */
    private String label;

    public int getScoreWeight() { return scoreWeight; }
    public void setScoreWeight(int scoreWeight) { this.scoreWeight = scoreWeight; }

    public Map<String, Object> getConditions() { return conditions; }
    public void setConditions(Map<String, Object> conditions) {
        this.conditions = conditions != null ? conditions : Collections.emptyMap();
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
