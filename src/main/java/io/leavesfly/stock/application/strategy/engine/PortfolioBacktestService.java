package io.leavesfly.stock.application.strategy.engine;

import io.leavesfly.stock.application.backtest.BacktestSimulationConfig;
import io.leavesfly.stock.application.backtest.BacktestSimulationResult;
import io.leavesfly.stock.application.backtest.BacktestSimulator;
import io.leavesfly.stock.application.strategy.StrategyCatalog;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import io.leavesfly.stock.domain.service.port.MarketDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多策略组合回测服务 — 对同一标的分配资金到多个策略独立运行。
 *
 * 将初始资金按策略数量等分（或按指定比例分配），每个策略独立运行回测，
 * 最终汇总组合总收益、平均回撤、组合胜率等指标。
 *
 * 用于评估多策略协同效果，而非单一策略表现。
 */
@Component
public class PortfolioBacktestService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioBacktestService.class);

    private final StrategyCatalog catalog;
    private final BacktestSimulator simulator;
    private final MarketDataPort dataFetcher;

    public PortfolioBacktestService(StrategyCatalog catalog, BacktestSimulator simulator,
                                     MarketDataPort dataFetcher) {
        this.catalog = catalog;
        this.simulator = simulator;
        this.dataFetcher = dataFetcher;
    }

    /**
     * 多策略组合回测。
     *
     * @param stockCode       股票代码
     * @param strategyIds     策略 id 列表
     * @param startDate       开始日期
     * @param endDate         结束日期
     * @param totalCapital    总资金
     * @return 组合回测结果
     */
    public Map<String, Object> runPortfolioBacktest(String stockCode, List<String> strategyIds,
                                                     LocalDate startDate, LocalDate endDate,
                                                     double totalCapital) {
        List<StockDailyData> data = dataFetcher.getHistoryData(stockCode, startDate, endDate);
        if (data == null || data.isEmpty()) {
            return Map.of("error", "无法获取历史数据: " + stockCode);
        }

        double perStrategyCapital = totalCapital / strategyIds.size();
        BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode(stockCode);

        List<Map<String, Object>> strategyResults = new ArrayList<>();
        double portfolioFinalValue = 0;
        double portfolioTotalReturn = 0;
        double portfolioMaxDrawdown = 0;
        int portfolioTrades = 0;
        int successfulStrategies = 0;

        for (String strategyId : strategyIds) {
            var strategyOpt = catalog.find(strategyId);
            if (strategyOpt.isEmpty() || !strategyOpt.get().hasBacktest()) {
                strategyResults.add(Map.of(
                        "strategy", strategyId,
                        "status", "skipped",
                        "reason", "策略不存在或不支持回测"));
                continue;
            }

            try {
                StrategyDefinition strategy = strategyOpt.get();
                BacktestSimulationResult result = simulator.simulate(data, strategy, perStrategyCapital, config);

                Map<String, Object> sr = new LinkedHashMap<>();
                sr.put("strategy", strategyId);
                sr.put("capital_allocated", perStrategyCapital);
                sr.put("final_value", result.getFinalCapital());
                sr.put("return_pct", result.getTotalReturnPct());
                sr.put("max_drawdown_pct", result.getMaxDrawdownPct());
                sr.put("win_rate_pct", result.getWinRatePct());
                sr.put("total_trades", result.getTotalTrades());
                sr.put("sharpe_ratio", result.getSharpeRatio());
                strategyResults.add(sr);

                portfolioFinalValue += result.getFinalCapital();
                portfolioTotalReturn += result.getTotalReturnPct();
                portfolioMaxDrawdown = Math.max(portfolioMaxDrawdown, result.getMaxDrawdownPct());
                portfolioTrades += result.getTotalTrades();
                successfulStrategies++;
            } catch (Exception e) {
                strategyResults.add(Map.of(
                        "strategy", strategyId,
                        "status", "failed",
                        "reason", e.getMessage()));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("stock_code", stockCode);
        summary.put("total_capital", totalCapital);
        summary.put("strategy_count", strategyIds.size());
        summary.put("successful_strategies", successfulStrategies);
        summary.put("portfolio_final_value", portfolioFinalValue);
        summary.put("portfolio_return_pct", (portfolioFinalValue - totalCapital) / totalCapital * 100);
        summary.put("avg_strategy_return_pct", successfulStrategies > 0 ? portfolioTotalReturn / successfulStrategies : 0);
        summary.put("portfolio_max_drawdown_pct", portfolioMaxDrawdown);
        summary.put("portfolio_total_trades", portfolioTrades);
        summary.put("strategy_results", strategyResults);

        log.info("组合回测完成: {} 策略数={} 组合收益={}%",
                stockCode, successfulStrategies,
                String.format("%.2f", (portfolioFinalValue - totalCapital) / totalCapital * 100));
        return summary;
    }
}
