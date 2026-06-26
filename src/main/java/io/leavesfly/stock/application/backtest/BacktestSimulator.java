package io.leavesfly.stock.application.backtest;

import io.leavesfly.stock.application.strategy.engine.BacktestSignalEngine;
import io.leavesfly.stock.application.strategy.model.BacktestProfile;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 真实感回测仿真器：次日开盘成交、T+1、交易成本、涨跌停与停牌约束。
 */
@Component
public class BacktestSimulator {

    private final BacktestSignalEngine signalEngine;

    public BacktestSimulator(BacktestSignalEngine signalEngine) {
        this.signalEngine = signalEngine;
    }

    public BacktestSimulationResult simulate(List<StockDailyData> data,
                                           StrategyDefinition strategy,
                                           double initialCapital,
                                           BacktestSimulationConfig config) {
        BacktestProfile profile = strategy.getBacktest();
        double positionSize = profile.getPositionSize();
        int warmup = signalEngine.computeWarmupDays(strategy);

        BacktestSimulationResult result = new BacktestSimulationResult();
        SimulationState state = new SimulationState(initialCapital);
        List<Double> dailyReturns = new ArrayList<>();
        double benchmarkStartPrice = requirePrice(data.get(warmup).getClosePrice(), data.get(warmup), "close");
        double benchmarkShares = initialCapital / benchmarkStartPrice;

        for (int i = warmup; i < data.size(); i++) {
            StockDailyData bar = data.get(i);

            if (config.getExecutionMode() == ExecutionMode.NEXT_OPEN) {
                executePendingOrders(bar, i, positionSize, config, state, result);
            }

            double closePrice = requirePrice(bar.getClosePrice(), bar, "close");
            double portfolioValue = state.cash + state.shares * closePrice;
            updateDrawdown(state, portfolioValue);
            recordEquitySnapshot(result, bar, portfolioValue, benchmarkShares * closePrice, state, closePrice);
            if (i > warmup) {
                double prevClose = requirePrice(data.get(i - 1).getClosePrice(), data.get(i - 1), "close");
                double prevValue = state.cash + state.shares * prevClose;
                if (prevValue > 0) {
                    dailyReturns.add((portfolioValue - prevValue) / prevValue);
                }
            }

            int signal = signalEngine.signal(strategy, data, i, state.shares > 0, state.entryPrice, state.entryDay);
            if (config.getExecutionMode() == ExecutionMode.CLOSE) {
                applyCloseExecution(bar, i, positionSize, config, state, result, signal);
            } else {
                queueNextOpenOrders(config, state, signal, i);
            }
        }

        forceCloseAtEnd(data, config, state, result);
        finalizeMetrics(data, warmup, initialCapital, state, dailyReturns, result, config);
        return result;
    }

    private void queueNextOpenOrders(BacktestSimulationConfig config,
                                     SimulationState state,
                                     int signal,
                                     int signalIndex) {
        if (signal == 1 && state.shares == 0) {
            state.pendingBuy = true;
            state.pendingSell = false;
            return;
        }
        if (signal == -1 && state.shares > 0) {
            if (config.isT1Enabled() && signalIndex <= state.buyBarIndex) {
                state.t1BlockedSells++;
                return;
            }
            state.pendingSell = true;
            state.pendingBuy = false;
        }
    }

    private void applyCloseExecution(StockDailyData bar,
                                     int barIndex,
                                     double positionSize,
                                     BacktestSimulationConfig config,
                                     SimulationState state,
                                     BacktestSimulationResult result,
                                     int signal) {
        if (signal == -1 && state.shares > 0) {
            if (config.isT1Enabled() && barIndex <= state.buyBarIndex) {
                state.t1BlockedSells++;
                return;
            }
            if (!BarTradability.canSell(bar, config)) {
                state.skippedSells++;
                return;
            }
            trySell(bar, barIndex, config, state, result, false);
            return;
        }
        if (signal == 1 && state.shares == 0) {
            if (!BarTradability.canBuy(bar, config)) {
                state.skippedBuys++;
                return;
            }
            tryBuy(bar, barIndex, positionSize, config, state, result);
        }
    }

    private void executePendingOrders(StockDailyData bar,
                                      int barIndex,
                                      double positionSize,
                                      BacktestSimulationConfig config,
                                      SimulationState state,
                                      BacktestSimulationResult result) {
        if (state.pendingSell && state.shares > 0) {
            if (config.isT1Enabled() && barIndex <= state.buyBarIndex) {
                state.t1BlockedSells++;
            } else if (!BarTradability.canSell(bar, config)) {
                state.skippedSells++;
            } else if (trySell(bar, barIndex, config, state, result, false)) {
                state.pendingSell = false;
            } else {
                state.skippedSells++;
                state.pendingSell = false;
            }
        }

        if (state.pendingBuy && state.shares == 0) {
            if (!BarTradability.canBuy(bar, config)) {
                state.skippedBuys++;
                state.pendingBuy = false;
            } else if (tryBuy(bar, barIndex, positionSize, config, state, result)) {
                state.pendingBuy = false;
            } else {
                state.skippedBuys++;
                state.pendingBuy = false;
            }
        }
    }

    private boolean tryBuy(StockDailyData bar,
                           int barIndex,
                           double positionSize,
                           BacktestSimulationConfig config,
                           SimulationState state,
                           BacktestSimulationResult result) {
        double referencePrice = executionReferencePrice(bar, config);
        double executionPrice = TradeCostCalculator.buyExecutionPrice(referencePrice, config);
        int rawShares = (int) (state.cash * positionSize / executionPrice);
        int shares = TradeCostCalculator.normalizeShares(rawShares, config);
        if (shares <= 0) {
            return false;
        }

        double commission = TradeCostCalculator.buyCommission(shares, executionPrice, config);
        double totalCost = shares * executionPrice + commission;
        if (totalCost > state.cash) {
            shares = shrinkSharesToAfford(shares, executionPrice, config, state.cash);
            if (shares <= 0) {
                return false;
            }
            commission = TradeCostCalculator.buyCommission(shares, executionPrice, config);
            totalCost = shares * executionPrice + commission;
        }

        double slippageCost = shares * Math.abs(executionPrice - referencePrice);
        state.cash -= totalCost;
        state.shares = shares;
        state.buyBarIndex = barIndex;
        state.entryPrice = executionPrice;
        state.entryDay = barIndex;

        BacktestTrade trade = new BacktestTrade();
        trade.setTradeDate(bar.getTradeDate());
        trade.setSide("buy");
        trade.setPrice(executionPrice);
        trade.setShares(shares);
        trade.setCommission(commission);
        trade.setSlippageCost(slippageCost);
        trade.setAmount(shares * executionPrice);
        trade.setReason("entry_signal");
        trade.setBarIndex(barIndex);
        result.getTrades().add(trade);
        return true;
    }

    private boolean trySell(StockDailyData bar,
                            int barIndex,
                            BacktestSimulationConfig config,
                            SimulationState state,
                            BacktestSimulationResult result,
                            boolean forced) {
        double referencePrice = executionReferencePrice(bar, config);
        double executionPrice = TradeCostCalculator.sellExecutionPrice(referencePrice, config);
        int shares = state.shares;
        double commission = TradeCostCalculator.sellCommission(shares, executionPrice, config);
        double stampTax = TradeCostCalculator.sellStampTax(shares, executionPrice, config);
        double proceeds = shares * executionPrice - commission - stampTax;
        double costBasis = shares * state.entryPrice;
        double profit = proceeds - costBasis;

        if (profit > 0) {
            state.wins++;
            state.grossProfit += profit;
        } else {
            state.losses++;
            state.grossLoss += Math.abs(profit);
        }
        state.completedTrades++;
        state.totalHoldDays += Math.max(0, barIndex - state.buyBarIndex);
        state.cash += proceeds;

        BacktestTrade trade = new BacktestTrade();
        trade.setTradeDate(bar.getTradeDate());
        trade.setSide("sell");
        trade.setPrice(executionPrice);
        trade.setShares(shares);
        trade.setCommission(commission);
        trade.setStampTax(stampTax);
        trade.setSlippageCost(shares * Math.abs(referencePrice - executionPrice));
        trade.setAmount(shares * executionPrice);
        trade.setReason(forced ? "forced_liquidation" : "exit_signal");
        trade.setBarIndex(barIndex);
        result.getTrades().add(trade);

        state.shares = 0;
        state.buyBarIndex = -1;
        state.entryPrice = 0;
        state.entryDay = -1;
        return true;
    }

    private void forceCloseAtEnd(List<StockDailyData> data,
                                 BacktestSimulationConfig config,
                                 SimulationState state,
                                 BacktestSimulationResult result) {
        if (state.shares <= 0) {
            return;
        }
        StockDailyData lastBar = data.get(data.size() - 1);
        if (!BarTradability.canSell(lastBar, config)) {
            result.getDiagnostics().put("forced_liquidation_skipped", true);
            return;
        }
        if (config.getExecutionMode() == ExecutionMode.NEXT_OPEN) {
            state.pendingSell = true;
            executePendingOrders(lastBar, data.size() - 1, 0, config, state, result);
            if (state.shares > 0 && BarTradability.canSell(lastBar, config)) {
                trySell(lastBar, data.size() - 1, config, state, result, true);
                state.pendingSell = false;
            }
            return;
        }
        trySell(lastBar, data.size() - 1, config, state, result, true);
    }

    private void finalizeMetrics(List<StockDailyData> data,
                                 int warmup,
                                 double initialCapital,
                                 SimulationState state,
                                 List<Double> dailyReturns,
                                 BacktestSimulationResult result,
                                 BacktestSimulationConfig config) {
        double lastClose = requirePrice(data.get(data.size() - 1).getClosePrice(), data.get(data.size() - 1), "close");
        double finalValue = state.cash + state.shares * lastClose;
        double benchmarkReturn = (lastClose - data.get(warmup).getClosePrice())
                / data.get(warmup).getClosePrice() * 100;

        result.setFinalCapital(finalValue);
        result.setTotalReturnPct((finalValue - initialCapital) / initialCapital * 100);
        result.setMaxDrawdownPct(state.maxDrawdown);
        result.setTotalTrades(state.completedTrades);
        result.setWinningTrades(state.wins);
        result.setLosingTrades(state.losses);
        result.setWinRatePct(state.completedTrades > 0 ? (double) state.wins / state.completedTrades * 100 : 0);
        result.setAvgHoldingDays(state.completedTrades > 0 ? state.totalHoldDays / state.completedTrades : 0);
        result.setBenchmarkReturnPct(benchmarkReturn);
        result.setProfitLossRatio(state.grossLoss > 0 ? state.grossProfit / state.grossLoss
                : (state.grossProfit > 0 ? state.grossProfit : 0));

        int days = data.size() - warmup;
        result.setAnnualReturnPct(days > 0 ? result.getTotalReturnPct() * 252.0 / days : 0);

        if (!dailyReturns.isEmpty()) {
            double avgReturn = dailyReturns.stream().mapToDouble(r -> r).average().orElse(0);
            double stdReturn = Math.sqrt(dailyReturns.stream().mapToDouble(r -> Math.pow(r - avgReturn, 2)).average().orElse(0));
            result.setSharpeRatio(stdReturn > 0 ? (avgReturn * 252 - 0.03) / (stdReturn * Math.sqrt(252)) : 0);
        }

        result.getDiagnostics().put("execution_mode", config.getExecutionMode().name());
        result.getDiagnostics().put("t1_enabled", config.isT1Enabled());
        result.getDiagnostics().put("total_commission",
                result.getTrades().stream().mapToDouble(BacktestTrade::getCommission).sum());
        result.getDiagnostics().put("total_stamp_tax",
                result.getTrades().stream().mapToDouble(BacktestTrade::getStampTax).sum());
        result.getDiagnostics().put("total_slippage_cost",
                result.getTrades().stream().mapToDouble(BacktestTrade::getSlippageCost).sum());
        result.getDiagnostics().put("skipped_buys", state.skippedBuys);
        result.getDiagnostics().put("skipped_sells", state.skippedSells);
        result.getDiagnostics().put("t1_blocked_sells", state.t1BlockedSells);
        result.getDiagnostics().put("open_position_at_end", state.shares > 0);
        result.getDiagnostics().put("equity_curve", result.getEquityCurve());
    }

    private void recordEquitySnapshot(BacktestSimulationResult result,
                                      StockDailyData bar,
                                      double portfolioValue,
                                      double benchmarkValue,
                                      SimulationState state,
                                      double closePrice) {
        double drawdownPct = state.peakValue > 0
                ? (state.peakValue - portfolioValue) / state.peakValue * 100 : 0;
        BacktestDailySnapshot snapshot = new BacktestDailySnapshot();
        snapshot.setDate(bar.getTradeDate());
        snapshot.setPortfolioValue(portfolioValue);
        snapshot.setBenchmarkValue(benchmarkValue);
        snapshot.setDrawdownPct(drawdownPct);
        snapshot.setClosePrice(closePrice);
        result.getEquityCurve().add(snapshot);
    }

    private double executionReferencePrice(StockDailyData bar, BacktestSimulationConfig config) {
        if (config.getExecutionMode() == ExecutionMode.CLOSE) {
            return requirePrice(bar.getClosePrice(), bar, "close");
        }
        Double open = bar.getOpenPrice();
        if (open != null && open > 0) {
            return open;
        }
        return requirePrice(bar.getClosePrice(), bar, "close");
    }

    private int shrinkSharesToAfford(int shares, double price, BacktestSimulationConfig config, double cash) {
        int lotSize = Math.max(1, config.getLotSize());
        while (shares > 0) {
            double commission = TradeCostCalculator.buyCommission(shares, price, config);
            if (shares * price + commission <= cash) {
                return shares;
            }
            shares -= lotSize;
        }
        return 0;
    }

    private void updateDrawdown(SimulationState state, double portfolioValue) {
        if (portfolioValue > state.peakValue) {
            state.peakValue = portfolioValue;
        }
        double drawdown = (state.peakValue - portfolioValue) / state.peakValue * 100;
        if (drawdown > state.maxDrawdown) {
            state.maxDrawdown = drawdown;
        }
    }

    private double requirePrice(Double price, StockDailyData bar, String field) {
        if (price == null || price <= 0) {
            throw new IllegalStateException("无效" + field + "价格: " + bar.getStockCode() + " @ " + bar.getTradeDate());
        }
        return price;
    }

    private static final class SimulationState {
        double cash;
        int shares;
        int buyBarIndex = -1;
        double entryPrice;
        int entryDay = -1;
        boolean pendingBuy;
        boolean pendingSell;
        double peakValue;
        double maxDrawdown;
        int completedTrades;
        int wins;
        int losses;
        int skippedBuys;
        int skippedSells;
        int t1BlockedSells;
        double totalHoldDays;
        double grossProfit;
        double grossLoss;

        SimulationState(double initialCapital) {
            this.cash = initialCapital;
            this.peakValue = initialCapital;
        }
    }
}
