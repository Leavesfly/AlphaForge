package io.leavesfly.stock.application.service;

import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 告警指标计算器
 * 对应Python: alert_indicators.py
 * 提供MACD金叉/死叉、RSI超买超卖、均线突破、量比等指标判断
 */
@Service
public class AlertIndicators {

    private static final Logger log = LoggerFactory.getLogger(AlertIndicators.class);
    private final DataFetcherManager dataFetcher;

    public AlertIndicators(DataFetcherManager dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    /** 量比(今日成交量 / 5日均量) */
    public double volumeRatio(String stockCode) {
        List<StockDailyData> data = getRecentData(stockCode, 10);
        if (data.size() < 6) return 1.0;
        long todayVol = data.get(data.size() - 1).getVolume() != null ? data.get(data.size() - 1).getVolume() : 0;
        long avg5 = 0;
        for (int i = data.size() - 6; i < data.size() - 1; i++) {
            avg5 += data.get(i).getVolume() != null ? data.get(i).getVolume() : 0;
        }
        avg5 /= 5;
        return avg5 > 0 ? (double) todayVol / avg5 : 1.0;
    }

    /** RSI指标 */
    public double rsi(String stockCode, int period) {
        List<StockDailyData> data = getRecentData(stockCode, period + 5);
        if (data.size() < period + 1) return 50.0;
        double gainSum = 0, lossSum = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double change = data.get(i).getClosePrice() - data.get(i - 1).getClosePrice();
            if (change > 0) gainSum += change;
            else lossSum -= change;
        }
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /** MACD金叉判断(DIF上穿DEA) */
    public boolean isMacdGoldenCross(String stockCode) {
        double[] macd = computeMacd(stockCode);
        if (macd == null) return false;
        // macd[0]=当日DIF-DEA, macd[1]=前日DIF-DEA
        return macd[0] > 0 && macd[1] <= 0;
    }

    /** MACD死叉判断(DIF下穿DEA) */
    public boolean isMacdDeathCross(String stockCode) {
        double[] macd = computeMacd(stockCode);
        if (macd == null) return false;
        return macd[0] < 0 && macd[1] >= 0;
    }

    /** 均线突破(收盘价突破N日均线) */
    public boolean isMaBreakout(String stockCode, int period) {
        List<StockDailyData> data = getRecentData(stockCode, period + 2);
        if (data.size() < period + 1) return false;
        double ma = 0;
        for (int i = data.size() - period - 1; i < data.size() - 1; i++) ma += data.get(i).getClosePrice();
        ma /= period;
        double todayClose = data.get(data.size() - 1).getClosePrice();
        double yesterdayClose = data.get(data.size() - 2).getClosePrice();
        return todayClose > ma && yesterdayClose <= ma;
    }

    /** 计算MACD(返回[当日柱, 前日柱]) */
    private double[] computeMacd(String stockCode) {
        List<StockDailyData> data = getRecentData(stockCode, 40);
        if (data.size() < 35) return null;
        double[] closes = data.stream().mapToDouble(StockDailyData::getClosePrice).toArray();
        double[] ema12 = ema(closes, 12);
        double[] ema26 = ema(closes, 26);
        int len = closes.length;
        double dif1 = ema12[len - 1] - ema26[len - 1];
        double dif2 = ema12[len - 2] - ema26[len - 2];
        // 简化DEA为DIF的9日EMA近似
        double dea1 = dif2 * 0.8 + dif1 * 0.2; // 近似
        return new double[]{dif1 - dea1, dif2 - dea1 * 0.9};
    }

    private double[] ema(double[] data, int period) {
        double[] result = new double[data.length];
        double k = 2.0 / (period + 1);
        result[0] = data[0];
        for (int i = 1; i < data.length; i++) {
            result[i] = data[i] * k + result[i - 1] * (1 - k);
        }
        return result;
    }

    private List<StockDailyData> getRecentData(String stockCode, int days) {
        try {
            return dataFetcher.getHistoryData(stockCode, LocalDate.now().minusDays(days + 5), LocalDate.now());
        } catch (Exception e) {
            return List.of();
        }
    }
}
