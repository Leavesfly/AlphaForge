package io.leavesfly.alphaforge.application.backtest.pairs;

import io.leavesfly.alphaforge.application.simulation.BacktestSimulationConfig;
import io.leavesfly.alphaforge.application.simulation.BacktestDailySnapshot;
import io.leavesfly.alphaforge.application.simulation.BacktestTrade;

import io.leavesfly.alphaforge.application.simulation.BacktestDailySnapshot;
import io.leavesfly.alphaforge.application.simulation.BacktestSimulationConfig;
import io.leavesfly.alphaforge.application.simulation.BacktestTrade;
import io.leavesfly.alphaforge.application.backtest.BarTradability;
import io.leavesfly.alphaforge.application.backtest.TradeCostCalculator;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.performance.PerformanceAnalytics;
import io.leavesfly.alphaforge.domain.service.performance.PerformanceMetrics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 配对交易回测引擎（只做多头腿）。
 *
 * <p>基于两标的价差的滚动 z-score 生成信号：价差偏离时买入相对便宜的一腿，
 * 价差回归（|z| 缩小）或进一步发散（止损）时平仓。任一时刻最多持有一条腿。</p>
 *
 * <p>成交采用当日收盘价语义，复用 {@link TradeCostCalculator}（佣金/印花税/滑点/手数）、
 * {@link BarTradability}（停牌/涨跌停）与 T+1 约束；绩效统一由 {@link PerformanceAnalytics} 计算。</p>
 */
@Component
public class PairBacktestSimulator {

    private static final PerformanceAnalytics PERF = new PerformanceAnalytics();

    private enum Leg {
        NONE, A, B
    }

    public PairBacktestResult simulate(List<StockDailyData> a,
                                       List<StockDailyData> b,
                                       PairTradingConfig cfg,
                                       double initialCapital,
                                       BacktestSimulationConfig aCost,
                                       BacktestSimulationConfig bCost) {
        PairBacktestResult result = new PairBacktestResult();
        PairStatistics.Aligned aligned = PairStatistics.alignByDate(a, b);
        int window = cfg.getLookbackWindow();

        if (aligned.size() <= window + 1) {
            result.setFinalCapital(initialCapital);
            result.getDiagnostics().put("suitable_for_pairs", false);
            result.getDiagnostics().put("reason",
                    "对齐后交易日不足: " + aligned.size() + " <= 窗口 " + window);
            result.getDiagnostics().put("aligned_days", aligned.size());
            return result;
        }

        double[] closesA = aligned.getClosesA();
        double[] closesB = aligned.getClosesB();
        double beta = "ratio".equalsIgnoreCase(cfg.getHedgeRatioMode())
                ? 1.0 : PairStatistics.hedgeRatio(closesA, closesB);
        double correlation = PairStatistics.correlation(closesA, closesB);
        double[] spread = PairStatistics.spreadSeries(closesA, closesB, beta);
        PairStatistics.MeanReversion mr = PairStatistics.meanReversionScore(spread);

        State state = new State(initialCapital);
        List<Double> dailyReturns = new ArrayList<>();

        for (int i = window; i < aligned.size(); i++) {
            StockDailyData barA = aligned.getBarsA().get(i);
            StockDailyData barB = aligned.getBarsB().get(i);
            double closeLegA = closesA[i];
            double closeLegB = closesB[i];

            double heldClose = state.leg == Leg.A ? closeLegA : (state.leg == Leg.B ? closeLegB : 0);
            double portfolioValue = state.cash + state.shares * heldClose;
            updateDrawdown(state, portfolioValue);
            recordSnapshot(result, barA.getTradeDate(), portfolioValue, state, spread[i]);

            if (i > window && state.prevValue > 0) {
                dailyReturns.add((portfolioValue - state.prevValue) / state.prevValue);
            }
            state.prevValue = portfolioValue;

            double z = PairStatistics.zscore(spread, i, window);

            if (state.leg == Leg.NONE) {
                if (z <= -cfg.getEntryZ()) {
                    tryBuy(Leg.A, barA, closeLegA, i, cfg, aCost, state, result);
                } else if (z >= cfg.getEntryZ()) {
                    tryBuy(Leg.B, barB, closeLegB, i, cfg, bCost, state, result);
                }
            } else {
                boolean regress = Math.abs(z) <= cfg.getExitZ();
                boolean diverge = Math.abs(z) >= cfg.getStopZ();
                if (regress || diverge) {
                    StockDailyData heldBar = state.leg == Leg.A ? barA : barB;
                    double heldPrice = state.leg == Leg.A ? closeLegA : closeLegB;
                    BacktestSimulationConfig heldCost = state.leg == Leg.A ? aCost : bCost;
                    trySell(heldBar, heldPrice, i, heldCost, state, result,
                            diverge ? "stop_loss" : "exit_signal");
                }
            }
        }

        forceCloseAtEnd(aligned, closesA, closesB, aCost, bCost, state, result);
        finalizeMetrics(aligned, closesA, closesB, window, initialCapital, state, dailyReturns, result);

        result.getDiagnostics().put("beta", round(beta));
        result.getDiagnostics().put("correlation", round(correlation));
        result.getDiagnostics().put("mean_reversion_rho", round(mr.rho()));
        result.getDiagnostics().put("mean_reversion_half_life",
                Double.isFinite(mr.halfLife()) ? round(mr.halfLife()) : -1);
        result.getDiagnostics().put("suitable_for_pairs",
                correlation >= cfg.getMinCorrelation() && mr.meanReverting());
        result.getDiagnostics().put("aligned_days", aligned.size());
        result.getDiagnostics().put("entry_z", cfg.getEntryZ());
        result.getDiagnostics().put("exit_z", cfg.getExitZ());
        result.getDiagnostics().put("stop_z", cfg.getStopZ());
        result.getDiagnostics().put("equity_curve", result.getEquityCurve());
        return result;
    }

    private boolean tryBuy(Leg leg, StockDailyData bar, double closePrice, int barIndex,
                           PairTradingConfig cfg, BacktestSimulationConfig cost,
                           State state, PairBacktestResult result) {
        if (!BarTradability.canBuy(bar, cost)) {
            state.skippedBuys++;
            return false;
        }
        double executionPrice = TradeCostCalculator.buyExecutionPrice(closePrice, cost);
        int rawShares = (int) (state.cash * cfg.getPositionSize() / executionPrice);
        int shares = TradeCostCalculator.normalizeShares(rawShares, cost);
        if (shares <= 0) {
            state.skippedBuys++;
            return false;
        }
        double commission = TradeCostCalculator.buyCommission(shares, executionPrice, cost);
        double totalCost = shares * executionPrice + commission;
        while (totalCost > state.cash && shares > 0) {
            shares -= Math.max(1, cost.getLotSize());
            shares = TradeCostCalculator.normalizeShares(shares, cost);
            commission = TradeCostCalculator.buyCommission(shares, executionPrice, cost);
            totalCost = shares * executionPrice + commission;
        }
        if (shares <= 0) {
            state.skippedBuys++;
            return false;
        }

        double slippageCost = shares * Math.abs(executionPrice - closePrice);
        state.cash -= totalCost;
        state.shares = shares;
        state.leg = leg;
        state.entryPrice = executionPrice;
        state.buyBarIndex = barIndex;

        BacktestTrade trade = new BacktestTrade();
        trade.setTradeDate(bar.getTradeDate());
        trade.setSide("buy");
        trade.setPrice(executionPrice);
        trade.setShares(shares);
        trade.setCommission(commission);
        trade.setSlippageCost(slippageCost);
        trade.setAmount(shares * executionPrice);
        trade.setReason("entry_" + leg.name());
        trade.setBarIndex(barIndex);
        result.getTrades().add(trade);
        return true;
    }

    private boolean trySell(StockDailyData bar, double closePrice, int barIndex,
                            BacktestSimulationConfig cost, State state, PairBacktestResult result,
                            String reason) {
        if (cost.isT1Enabled() && barIndex <= state.buyBarIndex) {
            state.t1BlockedSells++;
            return false;
        }
        if (!BarTradability.canSell(bar, cost)) {
            state.skippedSells++;
            return false;
        }
        double executionPrice = TradeCostCalculator.sellExecutionPrice(closePrice, cost);
        int shares = state.shares;
        double commission = TradeCostCalculator.sellCommission(shares, executionPrice, cost);
        double stampTax = TradeCostCalculator.sellStampTax(shares, executionPrice, cost);
        double proceeds = shares * executionPrice - commission - stampTax;
        double profit = proceeds - shares * state.entryPrice;

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
        trade.setSlippageCost(shares * Math.abs(closePrice - executionPrice));
        trade.setAmount(shares * executionPrice);
        trade.setReason(reason);
        trade.setBarIndex(barIndex);
        result.getTrades().add(trade);

        state.shares = 0;
        state.leg = Leg.NONE;
        state.entryPrice = 0;
        state.buyBarIndex = -1;
        return true;
    }

    private void forceCloseAtEnd(PairStatistics.Aligned aligned, double[] closesA, double[] closesB,
                                 BacktestSimulationConfig aCost, BacktestSimulationConfig bCost,
                                 State state, PairBacktestResult result) {
        if (state.leg == Leg.NONE || state.shares <= 0) {
            return;
        }
        int last = aligned.size() - 1;
        StockDailyData bar = state.leg == Leg.A ? aligned.getBarsA().get(last) : aligned.getBarsB().get(last);
        double close = state.leg == Leg.A ? closesA[last] : closesB[last];
        BacktestSimulationConfig cost = state.leg == Leg.A ? aCost : bCost;
        if (!BarTradability.canSell(bar, cost)) {
            result.getDiagnostics().put("forced_liquidation_skipped", true);
            return;
        }
        // 强平不受 T+1 限制约束（最后一日结算）
        state.buyBarIndex = -1;
        trySell(bar, close, last, cost, state, result, "forced_liquidation");
    }

    private void finalizeMetrics(PairStatistics.Aligned aligned, double[] closesA, double[] closesB,
                                 int window, double initialCapital, State state,
                                 List<Double> dailyReturns, PairBacktestResult result) {
        int last = aligned.size() - 1;
        double heldClose = state.leg == Leg.A ? closesA[last] : (state.leg == Leg.B ? closesB[last] : 0);
        double finalValue = state.cash + state.shares * heldClose;

        result.setFinalCapital(finalValue);
        result.setTotalReturnPct((finalValue - initialCapital) / initialCapital * 100);
        result.setMaxDrawdownPct(state.maxDrawdown);
        result.setTotalTrades(state.completedTrades);
        result.setWinningTrades(state.wins);
        result.setLosingTrades(state.losses);
        result.setWinRatePct(state.completedTrades > 0
                ? (double) state.wins / state.completedTrades * 100 : 0);
        result.setAvgHoldingDays(state.completedTrades > 0
                ? state.totalHoldDays / state.completedTrades : 0);
        result.setProfitLossRatio(state.grossLoss > 0 ? state.grossProfit / state.grossLoss
                : (state.grossProfit > 0 ? state.grossProfit : 0));

        int days = aligned.size() - window;
        result.setAnnualReturnPct(days > 0 ? result.getTotalReturnPct() * 252.0 / days : 0);

        if (!dailyReturns.isEmpty()) {
            PerformanceMetrics metrics = PERF.analyze(dailyReturns);
            result.setSharpeRatio(metrics.sharpeRatio());
            result.getDiagnostics().put("sortino_ratio", metrics.sortinoRatio());
            result.getDiagnostics().put("annualized_volatility_pct", metrics.annualizedVolatility() * 100);
        }

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
    }

    private void recordSnapshot(PairBacktestResult result, java.time.LocalDate date,
                                double portfolioValue, State state, double spread) {
        double drawdownPct = state.peakValue > 0
                ? (state.peakValue - portfolioValue) / state.peakValue * 100 : 0;
        BacktestDailySnapshot snapshot = new BacktestDailySnapshot();
        snapshot.setDate(date);
        snapshot.setPortfolioValue(portfolioValue);
        snapshot.setDrawdownPct(drawdownPct);
        snapshot.setClosePrice(spread);
        result.getEquityCurve().add(snapshot);
    }

    private void updateDrawdown(State state, double portfolioValue) {
        if (portfolioValue > state.peakValue) {
            state.peakValue = portfolioValue;
        }
        double drawdown = state.peakValue > 0 ? (state.peakValue - portfolioValue) / state.peakValue * 100 : 0;
        if (drawdown > state.maxDrawdown) {
            state.maxDrawdown = drawdown;
        }
    }

    private double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0;
        }
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static final class State {
        double cash;
        int shares;
        Leg leg = Leg.NONE;
        double entryPrice;
        int buyBarIndex = -1;
        double peakValue;
        double maxDrawdown;
        double prevValue;
        int completedTrades;
        int wins;
        int losses;
        int skippedBuys;
        int skippedSells;
        int t1BlockedSells;
        double totalHoldDays;
        double grossProfit;
        double grossLoss;

        State(double initialCapital) {
            this.cash = initialCapital;
            this.peakValue = initialCapital;
            this.prevValue = initialCapital;
        }
    }
}
