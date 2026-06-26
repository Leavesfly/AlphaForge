package io.leavesfly.stock.application.backtest;

/**
 * 交易成本计算：佣金、印花税、滑点。
 */
public final class TradeCostCalculator {

    private TradeCostCalculator() {}

    public static double buyExecutionPrice(double referencePrice, BacktestSimulationConfig config) {
        return referencePrice * (1 + config.getSlippageRate());
    }

    public static double sellExecutionPrice(double referencePrice, BacktestSimulationConfig config) {
        return referencePrice * (1 - config.getSlippageRate());
    }

    public static double buyCommission(int shares, double price, BacktestSimulationConfig config) {
        double commission = shares * price * config.getCommissionRate();
        if (config.getMinCommission() > 0) {
            commission = Math.max(commission, config.getMinCommission());
        }
        return commission;
    }

    public static double sellCommission(int shares, double price, BacktestSimulationConfig config) {
        double commission = shares * price * config.getCommissionRate();
        if (config.getMinCommission() > 0) {
            commission = Math.max(commission, config.getMinCommission());
        }
        return commission;
    }

    public static double sellStampTax(int shares, double price, BacktestSimulationConfig config) {
        return shares * price * config.getStampTaxRate();
    }

    public static int normalizeShares(int rawShares, BacktestSimulationConfig config) {
        int lotSize = Math.max(1, config.getLotSize());
        if (lotSize == 1) {
            return rawShares;
        }
        return (rawShares / lotSize) * lotSize;
    }
}
