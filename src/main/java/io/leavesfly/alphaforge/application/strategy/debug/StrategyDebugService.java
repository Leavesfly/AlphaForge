package io.leavesfly.alphaforge.application.strategy.debug;

import io.leavesfly.alphaforge.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.alphaforge.application.strategy.engine.BacktestSignalEngine;
import io.leavesfly.alphaforge.application.strategy.model.BacktestProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 策略调试服务：逐 K 线跟踪条件评估结果，帮助定位策略逻辑问题。
 *
 * 对每根 K 线，分别评估每个 entry/exit 条件的命中情况，
 * 记录信号生成过程，方便开发者定位「为何该天没有触发信号」等问题。
 */
@Component
public class StrategyDebugService {

    private final BacktestSignalEngine signalEngine;
    private final BacktestConditionEvaluator conditionEvaluator;

    public StrategyDebugService(BacktestSignalEngine signalEngine,
                                BacktestConditionEvaluator conditionEvaluator) {
        this.signalEngine = signalEngine;
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * 逐 K 线调试策略
     *
     * @param definition 策略定义
     * @param data       K 线数据
     * @return 调试追踪结果
     */
    public DebugTraceResult debug(StrategyDefinition definition, List<StockDailyData> data) {
        DebugTraceResult result = new DebugTraceResult();
        result.setStrategyId(definition.getId());
        result.setStockCode(data.isEmpty() ? "" : data.get(0).getStockCode());
        result.setTotalDays(data.size());

        BacktestProfile profile = definition.getBacktest();
        if (profile == null || data.isEmpty()) {
            result.setSteps(List.of());
            result.setWarmupDays(0);
            return result;
        }

        int warmup = signalEngine.computeWarmupDays(definition);
        result.setWarmupDays(warmup);

        List<DebugStep> steps = new ArrayList<>();
        boolean holding = false;
        double entryPrice = 0;
        int entryDay = -1;
        int buyCount = 0;
        int sellCount = 0;

        for (int i = 0; i < data.size(); i++) {
            StockDailyData bar = data.get(i);
            DebugStep step = new DebugStep();
            step.setDate(bar.getTradeDate());
            step.setIndex(i);
            step.setOpenPrice(bar.getOpenPrice() != null ? bar.getOpenPrice() : 0);
            step.setClosePrice(bar.getClosePrice() != null ? bar.getClosePrice() : 0);
            step.setVolume(bar.getVolume() != null ? bar.getVolume() : 0);
            step.setHolding(holding);
            step.setEntryPrice(entryPrice);

            // 评估入场条件（仅非持仓时）
            if (!holding) {
                step.setEntryConditions(evaluateConditions(
                        profile.getEntryConditions(), data, i, profile.getParameters(), false, entryPrice, entryDay));
                boolean allMatched = allMatched(step.getEntryConditions());
                step.setSignal(allMatched ? 1 : 0);
                if (allMatched && i >= warmup) {
                    holding = true;
                    entryPrice = bar.getClosePrice();
                    entryDay = i;
                    buyCount++;
                }
            } else {
                step.setEntryConditions(List.of());
                step.setExitConditions(evaluateConditions(
                        profile.getExitConditions(), data, i, profile.getParameters(), true, entryPrice, entryDay));
                boolean anyMatched = anyMatched(step.getExitConditions());
                step.setSignal(anyMatched ? -1 : 0);
                if (anyMatched) {
                    holding = false;
                    entryPrice = 0;
                    entryDay = -1;
                    sellCount++;
                }
            }

            steps.add(step);
        }

        result.setSteps(steps);
        result.setBuySignals(buyCount);
        result.setSellSignals(sellCount);
        return result;
    }

    /**
     * 评估一组条件的每个条件命中情况
     */
    private List<DebugStep.ConditionResult> evaluateConditions(
            List<Map<String, Object>> conditions, List<StockDailyData> data,
            int index, Map<String, Object> parameters, boolean holding,
            double entryPrice, int entryDay) {

        List<DebugStep.ConditionResult> results = new ArrayList<>();
        for (Map<String, Object> condition : conditions) {
            String type = String.valueOf(condition.getOrDefault("type", "unknown"));
            boolean matched = conditionEvaluator.evaluate(condition, data, index, parameters, holding, entryPrice, entryDay);
            String detail = describeCondition(condition, matched);
            results.add(new DebugStep.ConditionResult(type, matched, detail));
        }
        return results;
    }

    private boolean allMatched(List<DebugStep.ConditionResult> results) {
        if (results == null || results.isEmpty()) return false;
        return results.stream().allMatch(DebugStep.ConditionResult::isMatched);
    }

    private boolean anyMatched(List<DebugStep.ConditionResult> results) {
        if (results == null || results.isEmpty()) return false;
        return results.stream().anyMatch(DebugStep.ConditionResult::isMatched);
    }

    private String describeCondition(Map<String, Object> condition, boolean matched) {
        String type = String.valueOf(condition.getOrDefault("type", "unknown"));
        StringBuilder sb = new StringBuilder();
        sb.append(matched ? "[命中] " : "[未命中] ");
        sb.append(type);

        if (condition.containsKey("direction")) {
            sb.append(" direction=").append(condition.get("direction"));
        }
        if (condition.containsKey("fast")) {
            sb.append(" fast=").append(condition.get("fast"));
        }
        if (condition.containsKey("slow")) {
            sb.append(" slow=").append(condition.get("slow"));
        }
        if (condition.containsKey("pct")) {
            sb.append(" pct=").append(condition.get("pct"));
        }
        if (condition.containsKey("multiple")) {
            sb.append(" multiple=").append(condition.get("multiple"));
        }
        if (condition.containsKey("ma")) {
            sb.append(" ma=").append(condition.get("ma"));
        }
        if (condition.containsKey("days")) {
            sb.append(" days=").append(condition.get("days"));
        }
        return sb.toString();
    }
}
