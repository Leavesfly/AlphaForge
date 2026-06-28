package io.leavesfly.alphaforge.application.strategy.engine;

import io.leavesfly.alphaforge.application.backtest.BacktestSimulationConfig;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulator;
import io.leavesfly.alphaforge.application.strategy.model.BacktestProfile;
import io.leavesfly.alphaforge.application.strategy.model.OptimizationResult;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数优化器 — 网格搜索策略最优参数。
 *
 * 读取策略 YAML 中 backtest.param_space 声明的参数搜索空间，
 * 遍历所有参数组合（笛卡尔积），对每组参数运行回测仿真，
 * 按总收益率排序后返回最优参数及 Top N 候选结果。
 *
 * 用于：
 * - LLM 通过 optimize_strategy 工具触发自动调参
 * - WalkForwardValidator 的训练窗口内寻找最优参数
 */
@Component
public class ParameterOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ParameterOptimizer.class);
    private static final int MAX_TOP_CANDIDATES = 10;
    private static final int MAX_COMBINATIONS = 500;

    private final BacktestSimulator simulator;

    public ParameterOptimizer(BacktestSimulator simulator) {
        this.simulator = simulator;
    }

    /**
     * 对指定策略执行网格搜索参数优化。
     *
     * @param strategy       含 param_space 的策略定义
     * @param data           历史K线数据
     * @param initialCapital 初始资金
     * @return 优化结果（含最优参数及 Top 候选）
     */
    public OptimizationResult optimize(StrategyDefinition strategy, List<StockDailyData> data,
                                        double initialCapital) {
        BacktestProfile profile = strategy.getBacktest();
        if (profile == null || !profile.hasParamSpace()) {
            OptimizationResult empty = new OptimizationResult();
            empty.setTotalCandidates(0);
            return empty;
        }

        Map<String, List<Object>> paramSpace = profile.getParamSpace();
        List<Map<String, Object>> combinations = generateCombinations(paramSpace);

        if (combinations.size() > MAX_COMBINATIONS) {
            log.warn("参数组合数 {} 超过上限 {}，仅搜索前 {} 个", combinations.size(), MAX_COMBINATIONS, MAX_COMBINATIONS);
            combinations = combinations.subList(0, MAX_COMBINATIONS);
        }

        BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode(
                data.isEmpty() ? "" : data.get(0).getStockCode());
        if (profile.getSimulation() != null && !profile.getSimulation().isEmpty()) {
            config = BacktestSimulationConfig.merge(config, profile.getSimulation());
        }

        log.info("开始参数优化: {} 共 {} 个候选组合", strategy.getId(), combinations.size());

        List<OptimizationResult.CandidateResult> candidates = new ArrayList<>();
        Map<String, Object> bestParams = null;
        double bestReturn = Double.NEGATIVE_INFINITY;
        double bestDrawdown = 0;
        double bestWinRate = 0;
        double bestSharpe = 0;

        for (int i = 0; i < combinations.size(); i++) {
            Map<String, Object> params = combinations.get(i);
            try {
                StrategyDefinition variant = applyParams(strategy, params);
                BacktestSimulationResult result = simulator.simulate(data, variant, initialCapital, config);

                candidates.add(new OptimizationResult.CandidateResult(
                        params, result.getTotalReturnPct(), result.getMaxDrawdownPct(),
                        result.getWinRatePct(), result.getSharpeRatio()));

                if (result.getTotalReturnPct() > bestReturn) {
                    bestReturn = result.getTotalReturnPct();
                    bestParams = params;
                    bestDrawdown = result.getMaxDrawdownPct();
                    bestWinRate = result.getWinRatePct();
                    bestSharpe = result.getSharpeRatio();
                }
            } catch (Exception e) {
                log.debug("参数组合 {} 回测失败: {}", params, e.getMessage());
            }
        }

        candidates.sort(Comparator.comparingDouble(OptimizationResult.CandidateResult::returnPct).reversed());
        if (candidates.size() > MAX_TOP_CANDIDATES) {
            candidates = candidates.subList(0, MAX_TOP_CANDIDATES);
        }

        OptimizationResult optimizationResult = new OptimizationResult();
        optimizationResult.setBestParams(bestParams != null ? bestParams : Collections.emptyMap());
        optimizationResult.setBestReturnPct(bestReturn);
        optimizationResult.setBestMaxDrawdownPct(bestDrawdown);
        optimizationResult.setBestWinRatePct(bestWinRate);
        optimizationResult.setBestSharpeRatio(bestSharpe);
        optimizationResult.setTotalCandidates(combinations.size());
        optimizationResult.setTopCandidates(candidates);

        log.info("参数优化完成: {} 最优参数={} 收益={}%", strategy.getId(), bestParams,
                String.format("%.2f", bestReturn));
        return optimizationResult;
    }

    /** 生成参数空间的笛卡尔积 */
    private List<Map<String, Object>> generateCombinations(Map<String, List<Object>> paramSpace) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(new LinkedHashMap<>());
        for (Map.Entry<String, List<Object>> entry : paramSpace.entrySet()) {
            String key = entry.getKey();
            List<Object> values = entry.getValue();
            List<Map<String, Object>> expanded = new ArrayList<>();
            for (Map<String, Object> existing : result) {
                for (Object value : values) {
                    Map<String, Object> copy = new LinkedHashMap<>(existing);
                    copy.put(key, value);
                    expanded.add(copy);
                }
            }
            result = expanded;
        }
        return result;
    }

    /** 创建策略的参数变体（不修改原始策略定义） */
    private StrategyDefinition applyParams(StrategyDefinition original, Map<String, Object> params) {
        StrategyDefinition variant = new StrategyDefinition();
        variant.setId(original.getId());
        variant.setLabel(original.getLabel());
        variant.setDescription(original.getDescription());
        variant.setCategory(original.getCategory());
        variant.setRiskLevel(original.getRiskLevel());
        variant.setCapabilities(original.getCapabilities());
        variant.setRuntime(original.getRuntime());
        variant.setAvailable(true);

        BacktestProfile originalProfile = original.getBacktest();
        BacktestProfile variantProfile = new BacktestProfile();
        // 合并原始参数与覆盖参数
        Map<String, Object> mergedParams = new HashMap<>(originalProfile.getParameters());
        mergedParams.putAll(params);
        variantProfile.setParameters(mergedParams);
        variantProfile.setEntryConditions(originalProfile.getEntryConditions());
        variantProfile.setExitConditions(originalProfile.getExitConditions());
        variantProfile.setPositionSize(originalProfile.getPositionSize());
        variantProfile.setSimulation(originalProfile.getSimulation());
        variant.setBacktest(variantProfile);
        return variant;
    }
}
