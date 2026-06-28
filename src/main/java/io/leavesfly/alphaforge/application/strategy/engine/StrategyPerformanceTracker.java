package io.leavesfly.alphaforge.application.strategy.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略效果衰减追踪器。
 *
 * 在内存中追踪每个 scoring 策略最近 N 次评估的命中情况，
 * 根据命中频率计算衰减因子，调整策略的有效权重。
 *
 * 衰减逻辑：
 * - 命中率 < 10%：策略几乎不触发，可能已过时，权重降至 minWeight
 * - 命中率 > 90%：策略几乎总触发，区分度低，权重减半
 * - 命中率 30%~70%：策略处于最佳区分区间，不衰减
 * - 其他区间：按线性插值衰减
 *
 * 由 CompositeScoringEngine 在每次评分后调用 recordMatch()，
 * 并在评分时通过 getEffectiveWeight() 获取调整后的权重。
 */
@Component
public class StrategyPerformanceTracker {

    private static final Logger log = LoggerFactory.getLogger(StrategyPerformanceTracker.class);

    /** 策略 id → 命中历史（true=命中, false=未命中） */
    private final Map<String, Deque<Boolean>> matchHistory = new ConcurrentHashMap<>();
    /** 策略 id → 配置的窗口大小 */
    private final Map<String, Integer> windowSizes = new ConcurrentHashMap<>();
    /** 策略 id → 信号结果反馈历史（true=正确, false=错误，由SignalOutcomeEvaluator写入）*/
    private final Map<String, Deque<Boolean>> outcomeHistory = new ConcurrentHashMap<>();
    /** 策略全局结果准确率（由LoopStateManager定期更新，-1表示无数据）*/
    private volatile double globalSignalAccuracyPct = -1.0;

    /**
     * 记录信号结果反馈（由 SignalOutcomeEvaluator 在评估完成后调用）
     * 实现论文中的自我改进闭环：结果好的策略权重上升，结果差的权重下降
     *
     * @param strategyId 策略 id（若为null则更新全局准确率）
     * @param wasCorrect 信号是否正确
     */
    public void recordOutcome(String strategyId, boolean wasCorrect) {
        if (strategyId == null) return;
        Deque<Boolean> history = outcomeHistory.computeIfAbsent(strategyId, k -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(wasCorrect);
            while (history.size() > 50) { // 保留最近50次结果
                history.removeFirst();
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("策略[{}] 记录结果反馈: {}", strategyId, wasCorrect ? "正确" : "错误");
        }
    }

    /**
     * 更新全局信号准确率（由 LoopStateManager 在每次评估后调用）
     */
    public void updateGlobalAccuracy(double accuracyPct) {
        this.globalSignalAccuracyPct = accuracyPct;
        log.info("[StrategyPerformanceTracker] 全局信号准确率更新: {}%", String.format("%.1f", accuracyPct));
    }

    /**
     * 获取策略的结果准确率
     */
    public double getOutcomeAccuracy(String strategyId) {
        Deque<Boolean> history = outcomeHistory.get(strategyId);
        if (history == null || history.isEmpty()) return globalSignalAccuracyPct;
        synchronized (history) {
            long correct = history.stream().filter(b -> b).count();
            return (double) correct / history.size() * 100.0;
        }
    }

    /**
     * 记录策略命中情况。
     *
     * @param strategyId 策略 id
     * @param matched    是否命中
     * @param windowSize 追踪窗口大小
     */
    public void recordMatch(String strategyId, boolean matched, int windowSize) {
        Deque<Boolean> history = matchHistory.computeIfAbsent(strategyId, k -> new ArrayDeque<>());
        windowSizes.put(strategyId, windowSize);
        synchronized (history) {
            history.addLast(matched);
            while (history.size() > windowSize) {
                history.removeFirst();
            }
        }
    }

    /**
     * 计算策略的有效权重（考虑衰减）。
     *
     * @param strategyId    策略 id
     * @param originalWeight 原始权重
     * @param autoDecay     是否启用衰减
     * @param minWeight     最小权重
     * @return 调整后的有效权重
     */
    public int getEffectiveWeight(String strategyId, int originalWeight, boolean autoDecay, int minWeight) {
        if (!autoDecay) {
            return originalWeight;
        }

        Deque<Boolean> history = matchHistory.get(strategyId);
        if (history == null || history.isEmpty()) {
            return originalWeight; // 无历史数据，不衰减
        }

        double matchRate;
        synchronized (history) {
            long matches = history.stream().filter(b -> b).count();
            matchRate = (double) matches / history.size();
        }

        double decayFactor = calculateDecayFactor(matchRate);
        // 叠加结果反馈调整：结果准确率低于60%时额外衰减
        double outcomeAccuracy = getOutcomeAccuracy(strategyId);
        if (outcomeAccuracy >= 0 && outcomeAccuracy < 60.0) {
            double outcomePenalty = 1.0 - (60.0 - outcomeAccuracy) / 60.0 * 0.3; // 最多额外衰减30%
            decayFactor = decayFactor * Math.max(0.5, outcomePenalty);
            log.debug("策略 {} 结果惩罚: 准确率={}% 惩罚因子={}",
                    strategyId, String.format("%.1f", outcomeAccuracy), String.format("%.2f", outcomePenalty));
        }
        int effectiveWeight = (int) Math.round(originalWeight * decayFactor);
        effectiveWeight = Math.max(minWeight, effectiveWeight);

        if (effectiveWeight < originalWeight) {
            log.debug("策略 {} 衰减: 原权重={} 命中率={} 衰减因子={} 有效权重={}",
                    strategyId, originalWeight, String.format("%.2f", matchRate),
                    String.format("%.2f", decayFactor), effectiveWeight);
        }

        return effectiveWeight;
    }

    /**
     * 根据命中率计算衰减因子。
     * - < 10% 或 > 90%：强衰减
     * - 30%~70%：不衰减
     * - 其他：线性插值
     */
    private double calculateDecayFactor(double matchRate) {
        if (matchRate < 0.1) {
            // 几乎不触发 → 降至 30% 权重
            return 0.3;
        }
        if (matchRate > 0.9) {
            // 几乎总触发 → 降至 50% 权重（区分度低）
            return 0.5;
        }
        if (matchRate >= 0.3 && matchRate <= 0.7) {
            // 最佳区分区间，不衰减
            return 1.0;
        }
        if (matchRate < 0.3) {
            // 10%~30%：从 0.3 线性回升到 1.0
            return 0.3 + (matchRate - 0.1) / 0.2 * 0.7;
        }
        // 70%~90%：从 1.0 线性下降到 0.5
        return 1.0 - (matchRate - 0.7) / 0.2 * 0.5;
    }

    /** 获取策略当前的命中率（用于调试/展示） */
    public double getMatchRate(String strategyId) {
        Deque<Boolean> history = matchHistory.get(strategyId);
        if (history == null || history.isEmpty()) {
            return -1; // 无数据
        }
        synchronized (history) {
            long matches = history.stream().filter(b -> b).count();
            return (double) matches / history.size();
        }
    }

    /** 清除指定策略的历史记录 */
    public void clear(String strategyId) {
        matchHistory.remove(strategyId);
        windowSizes.remove(strategyId);
    }

    /** 清除所有策略的历史记录 */
    public void clearAll() {
        matchHistory.clear();
        windowSizes.clear();
    }
}
