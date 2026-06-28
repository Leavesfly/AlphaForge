package io.leavesfly.alphaforge.application.strategy.engine;

import io.leavesfly.alphaforge.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.alphaforge.application.strategy.condition.StockBarMath;
import io.leavesfly.alphaforge.application.strategy.condition.ValueCoercion;
import io.leavesfly.alphaforge.application.strategy.model.BacktestProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.springframework.stereotype.Component;

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
        for (Map<String, Object> condition : profile.getEntryConditions()) {
            warmup = Math.max(warmup, StockBarMath.maPeriodFromCondition(condition));
        }
        for (Map<String, Object> condition : profile.getExitConditions()) {
            warmup = Math.max(warmup, StockBarMath.maPeriodFromCondition(condition));
        }
        return warmup + 1;
    }

    public int signal(StrategyDefinition definition, List<StockDailyData> data, int index,
                      boolean holding, double entryPrice, int entryDay) {
        BacktestProfile profile = definition.getBacktest();
        if (profile == null) {
            return 0;
        }
        if (!holding) {
            return matchesAll(profile.getEntryConditions(), data, index, profile.getParameters(), false, entryPrice, entryDay)
                    ? 1 : 0;
        }
        return matchesAny(profile.getExitConditions(), data, index, profile.getParameters(), true, entryPrice, entryDay)
                ? -1 : 0;
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
