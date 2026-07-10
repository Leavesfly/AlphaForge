package io.leavesfly.alphaforge.application.strategy.simulation;

import io.leavesfly.alphaforge.application.simulation.PointInTimeFundamentals;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;

/**
 * 点时基本面数据加载契约（SPI）：由策略编排层持有、回测引擎实现。
 *
 * <p>与 {@link BacktestRunner} 一样归属 {@code strategy} 顶层包，
 * 以保证策略侧仅依赖抽象，避免对 {@code backtest} 包的直接依赖。</p>
 */
public interface FundamentalSnapshotProvider {

    /**
     * 为需要基本面条件的策略加载点时财务指标；无需或失败时返回 empty。
     */
    PointInTimeFundamentals load(String stockCode, StrategyDefinition strategy);
}
