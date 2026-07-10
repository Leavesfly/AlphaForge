package io.leavesfly.alphaforge.application.backtest.pairs;

import io.leavesfly.alphaforge.application.simulation.BacktestSimulationConfig;

import io.leavesfly.alphaforge.application.simulation.BacktestSimulationConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配对交易 / 统计套利编排服务。
 *
 * <p>拉取两只标的历史日线，做数据量与交易日重叠校验，委托 {@link PairBacktestSimulator}
 * 执行只做多头腿的价差回归回测，并基于相关性与均值回复代理检验判定该配对是否适合统计套利。</p>
 */
@Component
public class PairTradingService {

    private static final Logger log = LoggerFactory.getLogger(PairTradingService.class);

    private final MarketDataPort dataFetcher;
    private final PairBacktestSimulator simulator;

    public PairTradingService(MarketDataPort dataFetcher, PairBacktestSimulator simulator) {
        this.dataFetcher = dataFetcher;
        this.simulator = simulator;
    }

    /**
     * 运行配对交易回测。
     *
     * @param codeA   标的 A 代码
     * @param codeB   标的 B 代码
     * @param start   开始日期
     * @param end     结束日期
     * @param capital 初始资金
     * @param cfg     配对参数（null 时使用默认）
     * @return 回测结果 Map，含是否适合配对、绩效指标与诊断信息；数据不足时返回 {@code error}
     */
    public Map<String, Object> runPairBacktest(String codeA, String codeB,
                                               LocalDate start, LocalDate end,
                                               double capital, PairTradingConfig cfg) {
        if (codeA == null || codeA.isBlank() || codeB == null || codeB.isBlank()) {
            return Map.of("error", "两只标的代码均不能为空");
        }
        if (codeA.equalsIgnoreCase(codeB)) {
            return Map.of("error", "配对交易需要两只不同的标的");
        }
        PairTradingConfig config = cfg != null ? cfg : PairTradingConfig.defaults();

        List<StockDailyData> dataA = dataFetcher.getHistoryData(codeA, start, end);
        if (dataA == null || dataA.isEmpty()) {
            return Map.of("error", "无法获取历史数据: " + codeA);
        }
        List<StockDailyData> dataB = dataFetcher.getHistoryData(codeB, start, end);
        if (dataB == null || dataB.isEmpty()) {
            return Map.of("error", "无法获取历史数据: " + codeB);
        }

        PairStatistics.Aligned aligned = PairStatistics.alignByDate(dataA, dataB);
        if (aligned.size() <= config.getLookbackWindow() + 1) {
            return Map.of("error", String.format(
                    "两只标的重叠交易日不足（%d 日），需 > 窗口 %d 日",
                    aligned.size(), config.getLookbackWindow()));
        }

        BacktestSimulationConfig aCost = BacktestSimulationConfig.forStockCode(codeA);
        BacktestSimulationConfig bCost = BacktestSimulationConfig.forStockCode(codeB);

        PairBacktestResult result = simulator.simulate(dataA, dataB, config, capital, aCost, bCost);

        boolean suitable = Boolean.TRUE.equals(result.getDiagnostics().get("suitable_for_pairs"));
        log.info("配对回测完成: {}~{} 适合配对={} 相关={} 收益={}%",
                codeA, codeB, suitable,
                result.getDiagnostics().get("correlation"),
                String.format("%.2f", result.getTotalReturnPct()));

        return toResultMap(codeA, codeB, config, result);
    }

    private Map<String, Object> toResultMap(String codeA, String codeB,
                                            PairTradingConfig config, PairBacktestResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code_a", codeA);
        map.put("code_b", codeB);
        map.put("suitable_for_pairs", result.getDiagnostics().get("suitable_for_pairs"));
        map.put("correlation", result.getDiagnostics().get("correlation"));
        map.put("beta", result.getDiagnostics().get("beta"));
        map.put("mean_reversion_half_life", result.getDiagnostics().get("mean_reversion_half_life"));
        map.put("min_correlation", config.getMinCorrelation());
        map.put("final_capital", result.getFinalCapital());
        map.put("total_return_pct", result.getTotalReturnPct());
        map.put("annual_return_pct", result.getAnnualReturnPct());
        map.put("max_drawdown_pct", result.getMaxDrawdownPct());
        map.put("sharpe_ratio", result.getSharpeRatio());
        map.put("win_rate_pct", result.getWinRatePct());
        map.put("total_trades", result.getTotalTrades());
        map.put("avg_holding_days", result.getAvgHoldingDays());
        map.put("trades", result.getTrades());
        map.put("diagnostics", result.getDiagnostics());
        if (!Boolean.TRUE.equals(result.getDiagnostics().get("suitable_for_pairs"))) {
            map.put("advice", "该配对相关性或均值回复性不足，统计套利结果仅供参考，不建议实盘");
        }
        return map;
    }
}
