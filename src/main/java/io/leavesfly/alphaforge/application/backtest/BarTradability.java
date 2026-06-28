package io.leavesfly.alphaforge.application.backtest;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

/**
 * 判断 K 线是否可交易（停牌、涨跌停）。
 */
public final class BarTradability {

    private static final double LIMIT_EPSILON = 0.05;

    private BarTradability() {}

    public static boolean isSuspended(StockDailyData bar, BacktestSimulationConfig config) {
        if (!config.isCheckSuspension()) {
            return false;
        }
        Long volume = bar.getVolume();
        return volume == null || volume <= 0;
    }

    public static boolean isLimitUp(StockDailyData bar, BacktestSimulationConfig config) {
        if (!config.isCheckLimitPrice()) {
            return false;
        }
        Double changePct = bar.getChangePct();
        if (changePct == null) {
            return false;
        }
        return changePct >= config.getLimitPct() - LIMIT_EPSILON;
    }

    public static boolean isLimitDown(StockDailyData bar, BacktestSimulationConfig config) {
        if (!config.isCheckLimitPrice()) {
            return false;
        }
        Double changePct = bar.getChangePct();
        if (changePct == null) {
            return false;
        }
        return changePct <= -(config.getLimitPct() - LIMIT_EPSILON);
    }

    public static boolean canBuy(StockDailyData bar, BacktestSimulationConfig config) {
        return !isSuspended(bar, config) && !isLimitUp(bar, config);
    }

    public static boolean canSell(StockDailyData bar, BacktestSimulationConfig config) {
        return !isSuspended(bar, config) && !isLimitDown(bar, config);
    }
}
