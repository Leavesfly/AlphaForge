package io.leavesfly.alphaforge.application.strategy.engine;

import io.leavesfly.alphaforge.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.alphaforge.application.strategy.condition.FactorConditions;
import io.leavesfly.alphaforge.application.strategy.condition.StockBarMath;
import io.leavesfly.alphaforge.application.strategy.condition.ValueCoercion;
import io.leavesfly.alphaforge.application.strategy.model.BacktestProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 回测信号引擎 — 条件求值委托给 {@link BacktestConditionEvaluator}。
 */
@Component
public class BacktestSignalEngine {

    private final BacktestConditionEvaluator conditionEvaluator;

    public BacktestSignalEngine(BacktestConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    public int computeWarmupDays(StrategyDefinition definition) {
        BacktestProfile profile = definition.getBacktest();
        if (profile == null) {
            return 20;
        }
        int warmup = 20;
        warmup = Math.max(warmup, ValueCoercion.intParam(profile.getParameters(), "slow_period", 0));
        warmup = Math.max(warmup, ValueCoercion.intParam(profile.getParameters(), "long_ma", 0));
        warmup = Math.max(warmup, ValueCoercion.intParam(profile.getParameters(), "trend_ma", 0));
        warmup = Math.max(warmup, ValueCoercion.intParam(profile.getParameters(), "ma_period", 0));
        warmup = Math.max(warmup, ValueCoercion.intParam(profile.getParameters(), "lookback_days", 0));
        warmup = Math.max(warmup, ValueCoercion.intParam(profile.getParameters(), "wave_lookback", 0));
        warmup = Math.max(warmup, ValueCoercion.intParam(profile.getParameters(), "yin_count", 0));
        warmup = Math.max(warmup, ValueCoercion.intParam(profile.getParameters(), "consecutive_days", 0)
                + ValueCoercion.intParam(profile.getParameters(), "ma_period", 0));
        for (Map<String, Object> condition : profile.getEntryConditions()) {
            warmup = Math.max(warmup, StockBarMath.maPeriodFromCondition(condition));
            warmup = Math.max(warmup, warmupFromSharedCondition(condition));
        }
        for (Map<String, Object> condition : profile.getExitConditions()) {
            warmup = Math.max(warmup, StockBarMath.maPeriodFromCondition(condition));
            warmup = Math.max(warmup, warmupFromSharedCondition(condition));
        }
        return warmup + 1;
    }

    /** 从共享形态条件中提取所需预热天数 */
    private int warmupFromSharedCondition(Map<String, Object> condition) {
        String type = condition.get("type") != null ? String.valueOf(condition.get("type")) : "";
        return switch (type) {
            case "price_near_low" -> ValueCoercion.intVal(condition.get("lookback"), 60);
            case "consecutive_volume_days" ->
                    ValueCoercion.intVal(condition.get("days"), 2)
                            + ValueCoercion.intVal(condition.get("avg_period"), 20);
            case "yang_covers_yin" -> ValueCoercion.intVal(condition.get("yin_count"), 3);
            case "volume_amplify" -> 1;
            case "momentum_up" -> 1;
            case "macd_golden_cross", "macd_death_cross" -> 35;
            case "boll_upper_break" -> ValueCoercion.intVal(condition.get("period"), 20);
            case "amplitude_below" -> ValueCoercion.intVal(condition.get("lookback"), 20);
            case "factor" -> {
                String name = condition.get("name") != null ? String.valueOf(condition.get("name"))
                        : (condition.get("factor") != null ? String.valueOf(condition.get("factor")) : "");
                yield FactorConditions.warmupDays(name);
            }
            case "channel_breakout", "channel_breakdown" ->
                    ValueCoercion.intVal(condition.get("lookback"), 20);
            case "atr_stop" -> ValueCoercion.intVal(condition.get("period"), 20);
            case "boll_lower_touch", "boll_upper_touch", "boll_mid_reclaim" ->
                    ValueCoercion.intVal(condition.get("period"), 20);
            default -> 0;
        };
    }

    public int signal(StrategyDefinition definition, List<StockDailyData> data, int index,
                      boolean holding, double entryPrice, int entryDay) {
        return signal(definition, data, index, holding, entryPrice, entryDay, null);
    }

    /**
     * @param paramOverlay 点时覆盖参数（如 actual_roe / fundamentals_available），合并进 YAML parameters
     */
    public int signal(StrategyDefinition definition, List<StockDailyData> data, int index,
                      boolean holding, double entryPrice, int entryDay,
                      Map<String, Object> paramOverlay) {
        BacktestProfile profile = definition.getBacktest();
        if (profile == null) {
            return 0;
        }
        Map<String, Object> parameters = mergeParameters(profile.getParameters(), paramOverlay);
        if (!holding) {
            return matchesAll(profile.getEntryConditions(), data, index, parameters, false, entryPrice, entryDay)
                    ? 1 : 0;
        }
        return matchesAny(profile.getExitConditions(), data, index, parameters, true, entryPrice, entryDay)
                ? -1 : 0;
    }

    private Map<String, Object> mergeParameters(Map<String, Object> base, Map<String, Object> overlay) {
        if (overlay == null || overlay.isEmpty()) {
            return base != null ? base : Map.of();
        }
        Map<String, Object> merged = new HashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        merged.putAll(overlay);
        return merged;
    }

    private boolean matchesAll(List<Map<String, Object>> conditions, List<StockDailyData> data, int index,
                               Map<String, Object> parameters, boolean holding, double entryPrice, int entryDay) {
        if (conditions.isEmpty()) {
            return false;
        }
        for (Map<String, Object> condition : conditions) {
            if (!conditionEvaluator.evaluate(condition, data, index, parameters, holding, entryPrice, entryDay)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAny(List<Map<String, Object>> conditions, List<StockDailyData> data, int index,
                               Map<String, Object> parameters, boolean holding, double entryPrice, int entryDay) {
        for (Map<String, Object> condition : conditions) {
            if (conditionEvaluator.evaluate(condition, data, index, parameters, holding, entryPrice, entryDay)) {
                return true;
            }
        }
        return false;
    }
}
