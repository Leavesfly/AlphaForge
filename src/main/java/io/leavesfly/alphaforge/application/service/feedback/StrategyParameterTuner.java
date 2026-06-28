package io.leavesfly.alphaforge.application.service.feedback;

import io.leavesfly.alphaforge.application.strategy.engine.StrategyPerformanceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 策略参数自动调优器 — 信号效果→策略参数自动调优闭环
 *
 * 基于历史信号效果，自动建议策略参数调整方向：
 * - 准确率高的策略：保持参数，可适当放宽阈值
 * - 准确率低的策略：收紧阈值，降低权重
 * - 长期无信号的策略：建议优化或下线
 *
 * 与 StrategyPerformanceTracker 的区别：
 * - PerformanceTracker 做权重衰减（运行时）
 * - ParameterTuner 做参数建议（分析时），供人工审核或自动应用
 */
@Service
public class StrategyParameterTuner {

    private static final Logger log = LoggerFactory.getLogger(StrategyParameterTuner.class);

    /** 准确率阈值 */
    private static final double HIGH_ACCURACY = 70.0;
    private static final double LOW_ACCURACY = 40.0;

    private final StrategyPerformanceTracker performanceTracker;

    /** 调优建议历史（策略ID -> 最近建议列表） */
    private final Map<String, List<TuningSuggestion>> suggestionHistory = new LinkedHashMap<>();

    public StrategyParameterTuner(StrategyPerformanceTracker performanceTracker) {
        this.performanceTracker = performanceTracker;
    }

    /**
     * 为指定策略生成参数调优建议
     *
     * @param strategyId      策略 ID
     * @param currentParams   当前参数
     * @return 调优建议列表
     */
    public List<TuningSuggestion> suggestTuning(String strategyId, Map<String, Object> currentParams) {
        List<TuningSuggestion> suggestions = new ArrayList<>();

        double accuracy = performanceTracker.getOutcomeAccuracy(strategyId);
        double matchRate = performanceTracker.getMatchRate(strategyId);

        // 准确率数据不足
        if (accuracy < 0 && matchRate < 0) {
            return suggestions;
        }

        // 1. 基于信号准确率的调优
        if (accuracy >= 0) {
            if (accuracy < LOW_ACCURACY) {
                suggestions.add(new TuningSuggestion(
                        strategyId, "accuracy", accuracy,
                        "tighten_threshold",
                        String.format("策略准确率仅%.1f%%，建议收紧买入阈值（如提高 score 阈值 5-10 分），减少错误信号", accuracy),
                        TuningAction.TIGHTEN
                ));
            } else if (accuracy > HIGH_ACCURACY) {
                suggestions.add(new TuningSuggestion(
                        strategyId, "accuracy", accuracy,
                        "relax_threshold",
                        String.format("策略准确率%.1f%%表现优秀，可适当放宽阈值以捕获更多机会", accuracy),
                        TuningAction.RELAX
                ));
            }
        }

        // 2. 基于命中率的调优
        if (matchRate >= 0) {
            if (matchRate < 0.1) {
                suggestions.add(new TuningSuggestion(
                        strategyId, "match_rate", matchRate * 100,
                        "review_applicability",
                        String.format("策略命中率仅%.1f%%，几乎不触发，建议检查条件是否过于严格或已过时", matchRate * 100),
                        TuningAction.REVIEW
                ));
            } else if (matchRate > 0.9) {
                suggestions.add(new TuningSuggestion(
                        strategyId, "match_rate", matchRate * 100,
                        "increase_selectivity",
                        String.format("策略命中率%.1f%%过高，区分度低，建议增加额外条件提高选择性", matchRate * 100),
                        TuningAction.TIGHTEN
                ));
            }
        }

        // 记录建议历史
        if (!suggestions.isEmpty()) {
            suggestionHistory.computeIfAbsent(strategyId, k -> new ArrayList<>()).addAll(suggestions);
            log.info("策略[{}] 生成 {} 条调优建议 (准确率:{}% 命中率:{}%)",
                    strategyId, suggestions.size(),
                    String.format("%.1f", accuracy), String.format("%.1f", matchRate * 100));
        }

        return suggestions;
    }

    /**
     * 批量为所有策略生成调优建议
     *
     * @param strategyIds 策略 ID 列表
     * @return 策略 ID -> 建议列表
     */
    public Map<String, List<TuningSuggestion>> batchSuggestTuning(List<String> strategyIds) {
        Map<String, List<TuningSuggestion>> result = new LinkedHashMap<>();
        for (String id : strategyIds) {
            List<TuningSuggestion> suggestions = suggestTuning(id, Collections.emptyMap());
            if (!suggestions.isEmpty()) {
                result.put(id, suggestions);
            }
        }
        return result;
    }

    /**
     * 获取策略的调优历史
     */
    public List<TuningSuggestion> getTuningHistory(String strategyId) {
        return suggestionHistory.getOrDefault(strategyId, Collections.emptyList());
    }

    /**
     * 获取所有策略的调优建议摘要
     */
    public Map<String, Object> getTuningSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_strategies_tuned", suggestionHistory.size());
        summary.put("total_suggestions",
                suggestionHistory.values().stream().mapToInt(List::size).sum());

        // 按动作类型统计
        Map<TuningAction, Integer> actionCounts = new EnumMap<>(TuningAction.class);
        for (List<TuningSuggestion> suggestions : suggestionHistory.values()) {
            for (TuningSuggestion s : suggestions) {
                actionCounts.merge(s.action, 1, Integer::sum);
            }
        }
        summary.put("action_breakdown", actionCounts);
        return summary;
    }

    // ===== 数据类 =====

    /** 调优动作 */
    public enum TuningAction {
        TIGHTEN,   // 收紧阈值
        RELAX,     // 放宽阈值
        REVIEW,    // 需要审查
        DISABLE    // 建议禁用
    }

    /** 调优建议 */
    public record TuningSuggestion(
            String strategyId,
            String metric,         // 评估指标 (accuracy/match_rate)
            double metricValue,     // 指标值
            String suggestionKey,   // 建议标识
            String description,     // 建议描述
            TuningAction action     // 调优动作
    ) {}
}
