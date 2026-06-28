package io.leavesfly.alphaforge.application.strategy.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多策略加权评分的输出结果。
 *
 * 包含归一化后的总分、命中/未命中权重统计，以及各策略的命中明细。
 */
public class CompositeScoringResult {

    /** 归一化综合分，0~100 */
    private final int totalScore;
    /** 实际命中的权重之和 */
    private final int earnedWeight;
    /** 全部 scoring 策略的权重之和 */
    private final int maxWeight;
    /** 各策略命中情况 */
    private final List<StrategyHit> hits;

    public CompositeScoringResult(int totalScore, int earnedWeight, int maxWeight, List<StrategyHit> hits) {
        this.totalScore = totalScore;
        this.earnedWeight = earnedWeight;
        this.maxWeight = maxWeight;
        this.hits = hits;
    }

    public int getTotalScore() { return totalScore; }
    public int getEarnedWeight() { return earnedWeight; }
    public int getMaxWeight() { return maxWeight; }
    public List<StrategyHit> getHits() { return hits; }

    /** 转为 Map，写入 technicalResult.composite_scoring 供前端/API 展示 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total_score", totalScore);
        map.put("earned_weight", earnedWeight);
        map.put("max_weight", maxWeight);
        List<Map<String, Object>> items = new ArrayList<>();
        for (StrategyHit hit : hits) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", hit.id());
            item.put("label", hit.label());
            item.put("weight", hit.weight());
            item.put("matched", hit.matched());
            items.add(item);
        }
        map.put("strategies", items);
        return map;
    }

    /** 单条策略的评分命中记录 */
    public record StrategyHit(String id, String label, int weight, boolean matched) {}
}
