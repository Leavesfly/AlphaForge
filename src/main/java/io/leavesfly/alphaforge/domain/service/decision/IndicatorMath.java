package io.leavesfly.alphaforge.domain.service.decision;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.TechnicalIndicatorCalculator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 三灯引擎自含指标计算 — 纯静态函数，无 IO 与框架依赖。
 *
 * <p>domain 层不依赖 application 层（如 BarPatternConditions），因此引擎自带
 * 与 skill 口径一致的指标实现：ATR(14) 含当日 TR 均值、Wilder RSI(14)、
 * Kaufman 效率比 ER(20)、滚动年化波动率（60 日，√252）。</p>
 *
 * <p>其中 RSI 委派给同层的 {@link io.leavesfly.alphaforge.domain.service.TechnicalIndicatorCalculator}
 * 核心算法，避免口径分叉；本类对外保持“数据不足返回 NaN”的约定，供
 * 决策链以 {@code isNaN} 守卫后再下结论。</p>
 */
public final class IndicatorMath {

    /** 有效评分所需最少 K 线数（MA200 + 动量窗口 + 周线结构需要足够历史） */
    public static final int MIN_BARS = 250;

    private IndicatorMath() {
    }

    /** 简单移动平均（取末值；样本不足返回 NaN） */
    public static double sma(List<StockDailyData> data, int period) {
        if (data.size() < period) {
            return Double.NaN;
        }
        double sum = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            sum += data.get(i).getClosePrice();
        }
        return sum / period;
    }

    /** 滚动窗口年化波动率末值（日收益标准差 × √252，ddof=0） */
    public static double annualizedVol(List<StockDailyData> data, int period) {
        if (data.size() < period + 1) {
            return Double.NaN;
        }
        double[] rets = new double[period];
        for (int i = 0; i < period; i++) {
            double prev = data.get(data.size() - period - 1 + i).getClosePrice();
            double cur = data.get(data.size() - period + i).getClosePrice();
            rets[i] = cur / prev - 1.0;
        }
        double mean = 0;
        for (double r : rets) {
            mean += r;
        }
        mean /= period;
        double variance = 0;
        for (double r : rets) {
            variance += (r - mean) * (r - mean);
        }
        variance /= period;
        return Math.sqrt(variance) * Math.sqrt(252.0);
    }

    /** Kaufman 效率比 ER = |N 日净变动| / N 日逐日变动绝对值之和（0~1） */
    public static double efficiencyRatio(List<StockDailyData> data, int period) {
        if (data.size() < period + 1) {
            return Double.NaN;
        }
        double netChange = Math.abs(data.get(data.size() - 1).getClosePrice()
                - data.get(data.size() - 1 - period).getClosePrice());
        double pathVol = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            pathVol += Math.abs(data.get(i).getClosePrice() - data.get(i - 1).getClosePrice());
        }
        if (pathVol <= 0) {
            return 0.0;
        }
        return Math.min(1.0, netChange / pathVol);
    }

    /** Wilder RSI(14) 末值 */
    public static double rsi(List<StockDailyData> data, int period) {
        if (data.size() < period + 1) {
            return Double.NaN;
        }
        double[] closes = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            closes[i] = data.get(i).getClosePrice();
        }
        return TechnicalIndicatorCalculator.rsiOrNaN(closes, period);
    }

    /** ATR（近 window 日 TR 简单均值，含当日；无高低价时退化为收盘价差） */
    public static double atr(List<StockDailyData> data, int window) {
        if (data.size() < window + 1) {
            return Double.NaN;
        }
        double sum = 0;
        for (int i = data.size() - window; i < data.size(); i++) {
            StockDailyData bar = data.get(i);
            double high = bar.getHighPrice() != null ? bar.getHighPrice() : bar.getClosePrice();
            double low = bar.getLowPrice() != null ? bar.getLowPrice() : bar.getClosePrice();
            double prevClose = data.get(i - 1).getClosePrice();
            double tr = Math.max(high - low,
                    Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            sum += tr;
        }
        return sum / window;
    }

    /** 窗口内最高收盘价（用于距 60 日高点回撤） */
    public static double highestClose(List<StockDailyData> data, int period) {
        if (data.isEmpty()) {
            return Double.NaN;
        }
        double high = Double.NEGATIVE_INFINITY;
        for (int i = Math.max(0, data.size() - period); i < data.size(); i++) {
            high = Math.max(high, data.get(i).getClosePrice());
        }
        return high;
    }

    /** 区间收益：末值相对 period 根前收盘的涨跌幅 */
    public static double periodReturn(List<StockDailyData> data, int period) {
        if (data.size() < period + 1) {
            return Double.NaN;
        }
        double base = data.get(data.size() - 1 - period).getClosePrice();
        if (base <= 0) {
            return Double.NaN;
        }
        return data.get(data.size() - 1).getClosePrice() / base - 1.0;
    }

    /** 周线收盘序列：按 ISO 周聚合（每周最后交易日收盘） */
    public static List<Double> weeklyCloses(List<StockDailyData> data) {
        List<Double> weeks = new ArrayList<>();
        LocalDate currentWeek = null;
        Double lastOfWeek = null;
        for (StockDailyData bar : data) {
            if (bar.getTradeDate() == null) {
                continue;
            }
            LocalDate date = bar.getTradeDate();
            LocalDate weekStart = date.minusDays((date.getDayOfWeek().getValue() - 1L));
            if (currentWeek == null) {
                currentWeek = weekStart;
            } else if (!weekStart.equals(currentWeek)) {
                if (lastOfWeek != null) {
                    weeks.add(lastOfWeek);
                }
                currentWeek = weekStart;
            }
            lastOfWeek = bar.getClosePrice();
        }
        if (lastOfWeek != null) {
            weeks.add(lastOfWeek);
        }
        return weeks;
    }

    /** tanh 压缩映射到 0~100 分位标尺：50 × (1 + tanh(x)) */
    public static double tanhScore(double x) {
        return 50.0 * (1.0 + Math.tanh(x));
    }

    /** 安全取末根收盘价 */
    public static double lastClose(List<StockDailyData> data) {
        return data.isEmpty() ? Double.NaN : data.get(data.size() - 1).getClosePrice();
    }
}
