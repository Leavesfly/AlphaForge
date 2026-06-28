package io.leavesfly.stock.application.strategy.engine;

import io.leavesfly.stock.application.backtest.BacktestSimulationConfig;
import io.leavesfly.stock.application.backtest.BacktestSimulationResult;
import io.leavesfly.stock.application.backtest.BacktestSimulator;
import io.leavesfly.stock.application.strategy.model.BacktestProfile;
import io.leavesfly.stock.application.strategy.model.OptimizationResult;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import io.leavesfly.stock.application.strategy.model.WalkForwardResult;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Walk-Forward 滚动窗口验证器 — 防止参数过拟合。
 *
 * 将历史数据切分为多个滚动窗口，每个窗口分为训练段和测试段：
 * 1. 训练段：用 ParameterOptimizer 网格搜索找最优参数
 * 2. 测试段：用训练段找到的最优参数执行样本外回测
 *
 * 汇总样本外表现，通过 overfitRatio（样本外收益/样本内收益）
 * 评估策略参数在不同时间窗口的稳健性。
 */
@Component
public class WalkForwardValidator {

    private static final Logger log = LoggerFactory.getLogger(WalkForwardValidator.class);
    private static final int MIN_WINDOW_SIZE = 60;
    private static final double DEFAULT_TRAIN_RATIO = 0.7;

    private final ParameterOptimizer optimizer;
    private final BacktestSimulator simulator;

    public WalkForwardValidator(ParameterOptimizer optimizer, BacktestSimulator simulator) {
        this.optimizer = optimizer;
        this.simulator = simulator;
    }

    /**
     * 执行 Walk-Forward 验证。
     *
     * @param strategy       含 param_space 的策略定义
     * @param data           完整历史K线数据
     * @param windowSize     每个滚动窗口大小（交易日），0 表示自动
     * @param initialCapital 初始资金
     * @return Walk-Forward 验证结果
     */
    public WalkForwardResult validate(StrategyDefinition strategy, List<StockDailyData> data,
                                       int windowSize, double initialCapital) {
        if (data == null || data.size() < MIN_WINDOW_SIZE * 2) {
            WalkForwardResult empty = new WalkForwardResult();
            empty.setWindowCount(0);
            return empty;
        }

        BacktestProfile profile = strategy.getBacktest();
        if (profile == null || !profile.hasParamSpace()) {
            log.warn("策略 {} 未声明 param_space，无法执行 Walk-Forward", strategy.getId());
            WalkForwardResult empty = new WalkForwardResult();
            empty.setWindowCount(0);
            return empty;
        }

        // 自动计算窗口大小：至少 2 个窗口
        if (windowSize <= 0) {
            int windowCount = Math.min(5, data.size() / MIN_WINDOW_SIZE);
            windowSize = data.size() / Math.max(windowCount, 2);
        }
        windowSize = Math.max(windowSize, MIN_WINDOW_SIZE);

        int trainSize = (int) (windowSize * DEFAULT_TRAIN_RATIO);
        int testSize = windowSize - trainSize;
        int step = testSize; // 滚动步长 = 测试段长度

        List<WalkForwardResult.WindowResult> windows = new ArrayList<>();
        double totalOosReturn = 0;
        double totalOosDrawdown = 0;
        double totalOosWinRate = 0;
        double totalIsReturn = 0;
        int validWindows = 0;

        for (int start = 0; start + windowSize <= data.size(); start += step) {
            int trainEnd = start + trainSize;
            int testEnd = start + windowSize;

            List<StockDailyData> trainData = data.subList(start, trainEnd);
            List<StockDailyData> testData = data.subList(trainEnd, testEnd);

            // 训练段：网格搜索最优参数
            OptimizationResult optResult = optimizer.optimize(strategy, trainData, initialCapital);
            if (optResult.getTotalCandidates() == 0 || optResult.getBestParams().isEmpty()) {
                log.debug("窗口 {} 训练段无有效参数", windows.size());
                continue;
            }

            // 测试段：用训练段最优参数执行样本外回测
            StrategyDefinition testVariant = applyParams(strategy, optResult.getBestParams());
            BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode(
                    data.get(0).getStockCode());
            BacktestSimulationResult testResult = simulator.simulate(testData, testVariant, initialCapital, config);

            double isReturn = optResult.getBestReturnPct();
            double oosReturn = testResult.getTotalReturnPct();
            double oosDrawdown = testResult.getMaxDrawdownPct();
            double oosWinRate = testResult.getWinRatePct();

            windows.add(new WalkForwardResult.WindowResult(
                    windows.size(), optResult.getBestParams(),
                    isReturn, oosReturn, oosDrawdown, oosWinRate));

            totalOosReturn += oosReturn;
            totalOosDrawdown += oosDrawdown;
            totalOosWinRate += oosWinRate;
            totalIsReturn += isReturn;
            validWindows++;

            log.debug("窗口 {}: 样本内={}%, 样本外={}%", windows.size() - 1,
                    String.format("%.2f", isReturn), String.format("%.2f", oosReturn));
        }

        WalkForwardResult result = new WalkForwardResult();
        result.setWindowCount(validWindows);
        result.setWindows(windows);
        if (validWindows > 0) {
            result.setAvgOutOfSampleReturnPct(totalOosReturn / validWindows);
            result.setAvgOutOfSampleDrawdownPct(totalOosDrawdown / validWindows);
            result.setAvgOutOfSampleWinRatePct(totalOosWinRate / validWindows);
            result.setAvgInSampleReturnPct(totalIsReturn / validWindows);
            double avgIs = totalIsReturn / validWindows;
            result.setOverfitRatio(avgIs != 0 ? (totalOosReturn / validWindows) / avgIs : 0);
        }

        log.info("Walk-Forward 完成: {} 窗口={} 样本外均值={}% 过拟合比率={}",
                strategy.getId(), validWindows,
                String.format("%.2f", result.getAvgOutOfSampleReturnPct()),
                String.format("%.2f", result.getOverfitRatio()));
        return result;
    }

    /** 创建策略的参数变体（复用 ParameterOptimizer 的逻辑） */
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
        java.util.Map<String, Object> mergedParams = new java.util.HashMap<>(originalProfile.getParameters());
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
