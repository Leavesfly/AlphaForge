package io.leavesfly.alphaforge.domain.service.factor;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.FactorLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 默认因子库 — 基于技术分析指标的简单实现
 *
 * 提供常用的动量/均值回归/波动率/量价因子计算。
 * 生产环境可替换为专业因子库（含 IC/IR 分析、因子衰减监控等）。
 */
@Component
@ConditionalOnMissingBean(FactorLibrary.class)
public class DefaultFactorLibrary implements FactorLibrary {

    private static final Logger log = LoggerFactory.getLogger(DefaultFactorLibrary.class);

    private static final Map<String, String> FACTOR_CATEGORIES = Map.of(
            "momentum", "动量类",
            "mean_reversion", "均值回归类",
            "volatility", "波动率类",
            "volume", "量价类",
            "trend", "趋势类"
    );

    public DefaultFactorLibrary() {
        log.info("使用默认因子库（基于技术分析指标）");
    }

    @Override
    public double calculate(String factorName, List<StockDailyData> history) {
        if (history == null || history.isEmpty()) return 0;
        int size = history.size();

        return switch (factorName) {
            case "momentum_5d" -> calcReturn(history, Math.min(5, size));
            case "momentum_10d" -> calcReturn(history, Math.min(10, size));
            case "momentum_20d" -> calcReturn(history, Math.min(20, size));
            case "rsi_14" -> calcRSI(history, Math.min(14, size));
            case "volume_ratio_20d" -> calcVolumeRatio(history, Math.min(20, size));
            case "volatility_20d" -> calcVolatility(history, Math.min(20, size));
            case "ma_gap_5_20" -> calcMaGap(history, 5, 20);
            case "boll_position" -> calcBollPosition(history, 20);
            default -> 0;
        };
    }

    @Override
    public Map<String, Double> calculateBatch(List<String> factorNames, List<StockDailyData> history) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (String name : factorNames) {
            result.put(name, calculate(name, history));
        }
        return result;
    }

    @Override
    public List<Double> getFactorIC(String factorName, int lookbackDays) {
        // 默认实现不维护 IC 历史，返回空列表
        return Collections.emptyList();
    }

    @Override
    public double getFactorIR(String factorName, int lookbackDays) {
        return 0; // 默认实现不计算 IR
    }

    @Override
    public List<String> listAvailableFactors() {
        return List.of(
                "momentum_5d", "momentum_10d", "momentum_20d",
                "rsi_14", "volume_ratio_20d", "volatility_20d",
                "ma_gap_5_20", "boll_position"
        );
    }

    @Override
    public Map<String, List<String>> getFactorCategories() {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        categories.put("momentum", List.of("momentum_5d", "momentum_10d", "momentum_20d"));
        categories.put("mean_reversion", List.of("rsi_14", "boll_position"));
        categories.put("volatility", List.of("volatility_20d"));
        categories.put("volume", List.of("volume_ratio_20d"));
        categories.put("trend", List.of("ma_gap_5_20"));
        return categories;
    }

    // ===== 因子计算方法 =====

    private double calcReturn(List<StockDailyData> data, int period) {
        if (data.size() < period + 1) return 0;
        double current = getClose(data, data.size() - 1);
        double past = getClose(data, data.size() - 1 - period);
        return past > 0 ? (current - past) / past * 100 : 0;
    }

    private double calcRSI(List<StockDailyData> data, int period) {
        if (data.size() < period + 1) return 50;
        double gains = 0, losses = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double change = getClose(data, i) - getClose(data, i - 1);
            if (change > 0) gains += change;
            else losses -= change;
        }
        double avgGain = gains / period;
        double avgLoss = losses / period;
        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private double calcVolumeRatio(List<StockDailyData> data, int period) {
        if (data.size() < period + 1) return 1;
        long current = getVolume(data, data.size() - 1);
        long avg = 0;
        for (int i = data.size() - 1 - period; i < data.size() - 1; i++) {
            avg += getVolume(data, i);
        }
        avg /= period;
        return avg > 0 ? (double) current / avg : 1;
    }

    private double calcVolatility(List<StockDailyData> data, int period) {
        if (data.size() < period) return 0;
        double[] returns = new double[period - 1];
        double sum = 0;
        for (int i = 0; i < period - 1; i++) {
            double ret = getClose(data, data.size() - period + i + 1) / getClose(data, data.size() - period + i) - 1;
            returns[i] = ret;
            sum += ret;
        }
        double avg = sum / (period - 1);
        double variance = 0;
        for (double r : returns) variance += (r - avg) * (r - avg);
        return Math.sqrt(variance / (period - 1)) * Math.sqrt(252) * 100; // 年化波动率
    }

    private double calcMaGap(List<StockDailyData> data, int shortPeriod, int longPeriod) {
        if (data.size() < longPeriod) return 0;
        double maShort = 0, maLong = 0;
        for (int i = data.size() - shortPeriod; i < data.size(); i++) maShort += getClose(data, i);
        maShort /= shortPeriod;
        for (int i = data.size() - longPeriod; i < data.size(); i++) maLong += getClose(data, i);
        maLong /= longPeriod;
        return maLong > 0 ? (maShort - maLong) / maLong * 100 : 0;
    }

    private double calcBollPosition(List<StockDailyData> data, int period) {
        if (data.size() < period) return 50;
        double sum = 0;
        for (int i = data.size() - period; i < data.size(); i++) sum += getClose(data, i);
        double mean = sum / period;
        double variance = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            variance += Math.pow(getClose(data, i) - mean, 2);
        }
        double std = Math.sqrt(variance / period);
        double current = getClose(data, data.size() - 1);
        if (std == 0) return 50;
        double upper = mean + 2 * std;
        double lower = mean - 2 * std;
        return (current - lower) / (upper - lower) * 100;
    }

    private double getClose(List<StockDailyData> data, int i) {
        Double c = data.get(i).getClosePrice();
        return c != null ? c : 0;
    }

    private long getVolume(List<StockDailyData> data, int i) {
        Long v = data.get(i).getVolume();
        return v != null ? v : 0;
    }
}
