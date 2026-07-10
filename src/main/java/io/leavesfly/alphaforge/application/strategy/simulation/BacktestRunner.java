package io.leavesfly.alphaforge.application.strategy.simulation;

import io.leavesfly.alphaforge.application.simulation.BacktestSimulationConfig;
import io.leavesfly.alphaforge.application.simulation.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.simulation.PointInTimeFundamentals;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.util.List;

/**
 * 回测执行契约（SPI）：由策略编排层持有、回测引擎实现。
 *
 * <p>该接口归属 {@code strategy} 顶层包，使策略侧只依赖抽象，
 * 而由 {@code backtest} 包提供实现，从而消除 strategy 与 backtest 之间的包级循环依赖。</p>
 */
public interface BacktestRunner {

    /**
     * 执行回测仿真。
     *
     * @param data           行情序列
     * @param strategy       策略定义
     * @param initialCapital 初始资金
     * @param config         仿真参数
     * @return 回测结果
     */
    BacktestSimulationResult simulate(List<StockDailyData> data,
                                      StrategyDefinition strategy,
                                      double initialCapital,
                                      BacktestSimulationConfig config);

    /**
     * 执行回测仿真（带点时基本面数据）。
     */
    BacktestSimulationResult simulate(List<StockDailyData> data,
                                      StrategyDefinition strategy,
                                      double initialCapital,
                                      BacktestSimulationConfig config,
                                      PointInTimeFundamentals fundamentals);
}
