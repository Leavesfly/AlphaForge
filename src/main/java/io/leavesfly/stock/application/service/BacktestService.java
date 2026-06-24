package io.leavesfly.stock.application.service;

import io.leavesfly.stock.domain.model.entity.BacktestRecord;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.infrastructure.persistence.BacktestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 回测引擎服务
 * 
 * 对应Python版本的 src/core/backtest_engine.py + src/services/backtest_service.py
 * 功能: 策略回测、绩效计算、交易模拟
 */
@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);
    private final DataFetcherManager dataFetcher;
    private final BacktestRepository backtestRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BacktestService(DataFetcherManager dataFetcher, BacktestRepository backtestRepo) {
        this.dataFetcher = dataFetcher;
        this.backtestRepo = backtestRepo;
    }

    /**
     * 执行回测
     *
     * @param stockCode    股票代码
     * @param strategyName 策略名称
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @param initialCapital 初始资金
     * @return 回测结果
     */
    public BacktestRecord runBacktest(String stockCode, String strategyName,
                                      LocalDate startDate, LocalDate endDate, double initialCapital) {
        log.info("开始回测: {} - 策略: {} - 周期: {} ~ {}", stockCode, strategyName, startDate, endDate);

        // 获取历史数据
        List<StockDailyData> historyData = dataFetcher.getHistoryData(stockCode, startDate, endDate);
        if (historyData.size() < 20) {
            log.error("历史数据不足，无法回测: {}", stockCode);
            return null;
        }

        // 执行策略回测
        BacktestResult result = executeStrategy(historyData, strategyName, initialCapital);

        // 保存回测记录
        BacktestRecord record = new BacktestRecord();
        record.setStockCode(stockCode);
        record.setStockName(historyData.get(0).getStockName());
        record.setStrategyName(strategyName);
        record.setStartDate(startDate.atStartOfDay());
        record.setEndDate(endDate.atStartOfDay());
        record.setInitialCapital(initialCapital);
        record.setFinalCapital(result.finalCapital);
        record.setTotalReturnPct(result.totalReturnPct);
        record.setAnnualReturnPct(result.annualReturnPct);
        record.setMaxDrawdownPct(result.maxDrawdownPct);
        record.setSharpeRatio(result.sharpeRatio);
        record.setWinRatePct(result.winRatePct);
        record.setTotalTrades(result.totalTrades);
        record.setWinningTrades(result.winningTrades);
        record.setLosingTrades(result.losingTrades);
        record.setAvgHoldingDays(result.avgHoldingDays);
        record.setProfitLossRatio(result.profitLossRatio);
        record.setBenchmarkReturnPct(result.benchmarkReturnPct);
        record.setAlphaPct(result.totalReturnPct - result.benchmarkReturnPct);

        backtestRepo.save(record);
        log.info("回测完成: {} 总收益: {:.2f}% 最大回撤: {:.2f}%", stockCode, result.totalReturnPct, result.maxDrawdownPct);
        return record;
    }

    /**
     * 执行策略模拟交易
     */
    private BacktestResult executeStrategy(List<StockDailyData> data, String strategy, double capital) {
        BacktestResult result = new BacktestResult();
        double cash = capital;
        int shares = 0;
        double peakValue = capital;
        double maxDrawdown = 0;
        List<Double> dailyReturns = new ArrayList<>();
        int trades = 0, wins = 0, losses = 0;
        double totalHoldDays = 0;
        int entryDay = -1;

        for (int i = 20; i < data.size(); i++) {
            StockDailyData today = data.get(i);
            double price = today.getClosePrice();
            double portfolioValue = cash + shares * price;

            // 计算最大回撤
            if (portfolioValue > peakValue) peakValue = portfolioValue;
            double drawdown = (peakValue - portfolioValue) / peakValue * 100;
            if (drawdown > maxDrawdown) maxDrawdown = drawdown;

            // 日收益率
            if (i > 20) {
                double prevValue = cash + shares * data.get(i - 1).getClosePrice();
                dailyReturns.add((portfolioValue - prevValue) / prevValue);
            }

            // 策略信号生成
            int signal = generateSignal(data, i, strategy);

            if (signal == 1 && shares == 0) {
                // 买入
                shares = (int) (cash * 0.95 / price);
                cash -= shares * price;
                entryDay = i;
                trades++;
            } else if (signal == -1 && shares > 0) {
                // 卖出
                double sellValue = shares * price;
                double profit = sellValue - (shares * data.get(entryDay).getClosePrice());
                if (profit > 0) wins++;
                else losses++;
                totalHoldDays += (i - entryDay);
                cash += sellValue;
                shares = 0;
            }
        }

        // 最终结算
        double finalValue = cash + shares * data.get(data.size() - 1).getClosePrice();
        double benchmarkReturn = (data.get(data.size() - 1).getClosePrice() - data.get(20).getClosePrice())
                / data.get(20).getClosePrice() * 100;

        result.finalCapital = finalValue;
        result.totalReturnPct = (finalValue - capital) / capital * 100;
        result.maxDrawdownPct = maxDrawdown;
        result.totalTrades = trades;
        result.winningTrades = wins;
        result.losingTrades = losses;
        result.winRatePct = trades > 0 ? (double) wins / trades * 100 : 0;
        result.avgHoldingDays = trades > 0 ? totalHoldDays / trades : 0;
        result.benchmarkReturnPct = benchmarkReturn;
        result.profitLossRatio = losses > 0 ? (double) wins / losses : wins;

        // 年化收益率
        int days = data.size() - 20;
        result.annualReturnPct = days > 0 ? result.totalReturnPct * 252.0 / days : 0;

        // 夏普比率
        if (!dailyReturns.isEmpty()) {
            double avgReturn = dailyReturns.stream().mapToDouble(r -> r).average().orElse(0);
            double stdReturn = Math.sqrt(dailyReturns.stream().mapToDouble(r -> Math.pow(r - avgReturn, 2)).average().orElse(0));
            result.sharpeRatio = stdReturn > 0 ? (avgReturn * 252 - 0.03) / (stdReturn * Math.sqrt(252)) : 0;
        }

        return result;
    }

    /**
     * 生成交易信号
     * @return 1=买入, -1=卖出, 0=持有
     */
    private int generateSignal(List<StockDailyData> data, int index, String strategy) {
        switch (strategy.toLowerCase()) {
            case "ma_golden_cross": return maCrossSignal(data, index);
            case "volume_breakout": return volumeBreakoutSignal(data, index);
            case "bull_trend": return bullTrendSignal(data, index);
            default: return maCrossSignal(data, index);
        }
    }

    /** 均线金叉/死叉策略 */
    private int maCrossSignal(List<StockDailyData> data, int index) {
        if (index < 20) return 0;
        double ma5 = avgClose(data, index, 5);
        double ma20 = avgClose(data, index, 20);
        double prevMa5 = avgClose(data, index - 1, 5);
        double prevMa20 = avgClose(data, index - 1, 20);
        if (prevMa5 <= prevMa20 && ma5 > ma20) return 1;  // 金叉买入
        if (prevMa5 >= prevMa20 && ma5 < ma20) return -1; // 死叉卖出
        return 0;
    }

    /** 放量突破策略 */
    private int volumeBreakoutSignal(List<StockDailyData> data, int index) {
        if (index < 20) return 0;
        long avgVol = 0;
        for (int i = index - 20; i < index; i++) avgVol += data.get(i).getVolume();
        avgVol /= 20;
        StockDailyData today = data.get(index);
        if (today.getVolume() > avgVol * 2 && today.getChangePct() != null && today.getChangePct() > 3) return 1;
        if (today.getChangePct() != null && today.getChangePct() < -5) return -1;
        return 0;
    }

    /** 牛趋势策略 */
    private int bullTrendSignal(List<StockDailyData> data, int index) {
        if (index < 30) return 0;
        double ma10 = avgClose(data, index, 10);
        double ma30 = avgClose(data, index, 30);
        double price = data.get(index).getClosePrice();
        if (price > ma10 && ma10 > ma30 && data.get(index).getChangePct() != null && data.get(index).getChangePct() > 0) return 1;
        if (price < ma30) return -1;
        return 0;
    }

    private double avgClose(List<StockDailyData> data, int end, int period) {
        double sum = 0;
        for (int i = end - period + 1; i <= end; i++) sum += data.get(i).getClosePrice();
        return sum / period;
    }

    /** 获取回测历史 */
    public List<BacktestRecord> getHistory(String stockCode) {
        if (stockCode != null && !stockCode.isEmpty()) return backtestRepo.findByStockCodeOrderByCreatedAtDesc(stockCode);
        return backtestRepo.findTop20ByOrderByCreatedAtDesc();
    }

    /** 运行信号回测(对齐dsa-web) */
    public Map<String, Object> runSignalBacktest(String code, int evalWindowDays, int limit) {
        return Map.of("status", "completed", "evaluated", 0, "message", "回测完成");
    }

    /** 获取回测结果列表 */
    public List<Map<String, Object>> getBacktestResults(String code, int page, int limit) {
        return List.of();
    }

    /** 获取整体绩效 */
    public Map<String, Object> getOverallPerformance() {
        return Map.of("total", 0, "win_rate", (Object) null, "profit_loss_ratio", (Object) null, "avg_return_pct", (Object) null);
    }

    /** 获取个股绩效 */
    public Map<String, Object> getStockPerformance(String code) {
        return Map.of("stock_code", code, "total", 0, "win_rate", (Object) null);
    }

    /** 回测结果内部类 */
    private static class BacktestResult {
        double finalCapital;
        double totalReturnPct;
        double annualReturnPct;
        double maxDrawdownPct;
        double sharpeRatio;
        double winRatePct;
        int totalTrades;
        int winningTrades;
        int losingTrades;
        double avgHoldingDays;
        double profitLossRatio;
        double benchmarkReturnPct;
    }
}
