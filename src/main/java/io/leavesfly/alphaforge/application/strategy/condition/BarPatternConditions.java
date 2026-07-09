package io.leavesfly.alphaforge.application.strategy.condition;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.TechnicalIndicatorCalculator;

import java.util.List;

/**
 * 共享 K 线形态 / 量价条件求值。
 *
 * <p>回测（{@link BacktestConditionEvaluator}）与综合评分（CompositeScoringEngine）
 * 共用同一套实现，避免「同一策略两套口径」。选股引擎产出连续分数，暂不走本类。</p>
 */
public final class BarPatternConditions {

    private static final TechnicalIndicatorCalculator INDICATORS = new TechnicalIndicatorCalculator();

    private BarPatternConditions() {
    }

    /**
     * 收盘价是否处于近 {@code lookback} 日区间的偏低位置（相对位置 ≤ {@code maxPosition}）。
     * {@code maxPosition=0.25} 表示落在区间下 25%。
     */
    public static boolean priceNearLow(List<StockDailyData> data, int index, int lookback, double maxPosition) {
        if (data == null || index < 0 || index >= data.size()) {
            return false;
        }
        int window = Math.min(lookback, index + 1);
        if (window < 5) {
            return false;
        }
        double low = Double.MAX_VALUE;
        double high = Double.MIN_VALUE;
        for (int i = index - window + 1; i <= index; i++) {
            double close = data.get(i).getClosePrice();
            low = Math.min(low, close);
            high = Math.max(high, close);
        }
        if (high <= low) {
            return false;
        }
        double pos = (data.get(index).getClosePrice() - low) / (high - low);
        return pos <= maxPosition;
    }

    /**
     * 连续 {@code days} 日成交量均 ≥ 前 {@code avgPeriod} 日均量 × {@code multiple}。
     */
    public static boolean consecutiveVolumeDays(List<StockDailyData> data, int index,
                                                int days, double multiple, int avgPeriod) {
        if (data == null || index < avgPeriod + days - 1) {
            return false;
        }
        for (int i = index - days + 1; i <= index; i++) {
            long avg = StockBarMath.avgVolume(data, i - avgPeriod, i - 1);
            if (avg <= 0 || data.get(i).getVolume() < avg * multiple) {
                return false;
            }
        }
        return true;
    }

    /**
     * 一阳穿 N 阴：当日阳线实体覆盖前 {@code yinCount} 根阴线高低点。
     */
    public static boolean oneYangCoversYin(List<StockDailyData> data, int index, int yinCount) {
        if (data == null || index < yinCount) {
            return false;
        }
        StockDailyData yang = data.get(index);
        if (yang.getOpenPrice() == null || yang.getClosePrice() == null) {
            Double chg = yang.getChangePct();
            if (chg == null || chg <= 0) {
                return false;
            }
        } else if (yang.getClosePrice() <= yang.getOpenPrice()) {
            return false;
        }
        double yangLow = yang.getLowPrice() != null ? yang.getLowPrice()
                : (yang.getOpenPrice() != null ? yang.getOpenPrice() : yang.getClosePrice());
        double yangHigh = yang.getHighPrice() != null ? yang.getHighPrice() : yang.getClosePrice();
        for (int i = index - yinCount; i < index; i++) {
            StockDailyData bar = data.get(i);
            double open = bar.getOpenPrice() != null ? bar.getOpenPrice() : bar.getClosePrice();
            double close = bar.getClosePrice();
            if (close >= open) {
                return false;
            }
            double yinHigh = bar.getHighPrice() != null ? bar.getHighPrice() : open;
            double yinLow = bar.getLowPrice() != null ? bar.getLowPrice() : close;
            if (yangLow > yinLow || yangHigh < yinHigh) {
                return false;
            }
        }
        return true;
    }

    /** 当日成交量 / 前一日成交量。 */
    public static double volumeAmplify(List<StockDailyData> data, int index) {
        if (data == null || index < 1) {
            return 1;
        }
        Long prev = data.get(index - 1).getVolume();
        Long today = data.get(index).getVolume();
        if (prev == null || prev <= 0 || today == null) {
            return 1;
        }
        return (double) today / prev;
    }

    /** 当日成交量 / 前 {@code period} 日均量。 */
    public static double volumeRatio(List<StockDailyData> data, int index, int period) {
        if (data == null || index < period) {
            return 1;
        }
        long avg = StockBarMath.avgVolume(data, index - period, index - 1);
        Long today = data.get(index).getVolume();
        if (avg <= 0 || today == null) {
            return 1;
        }
        return (double) today / avg;
    }

    /** 当日涨跌幅是否严格大于阈值（%）。 */
    public static boolean momentumUp(List<StockDailyData> data, int index, double minChangePct) {
        if (data == null || index < 0 || index >= data.size()) {
            return false;
        }
        Double changePct = data.get(index).getChangePct();
        return changePct != null && changePct > minChangePct;
    }

    /**
     * MACD 金叉（缠论「底背驰」的可计算近似：DIF 上穿 DEA）。
     */
    public static boolean macdGoldenCross(List<StockDailyData> data, int index) {
        double[] closes = closesTo(data, index);
        if (closes == null || closes.length < 35) {
            return false;
        }
        return INDICATORS.isMacdGoldenCross(closes);
    }

    /**
     * MACD 死叉（出场 / 顶背驰近似）。
     */
    public static boolean macdDeathCross(List<StockDailyData> data, int index) {
        double[] closes = closesTo(data, index);
        if (closes == null || closes.length < 35) {
            return false;
        }
        return INDICATORS.isMacdDeathCross(closes);
    }

    /**
     * 布林带上轨突破（缠论「中枢突破」的可计算近似）。
     */
    public static boolean bollUpperBreak(List<StockDailyData> data, int index, int period, double stdMult) {
        double[] closes = closesTo(data, index);
        if (closes == null || closes.length < period) {
            return false;
        }
        double mid = INDICATORS.sma(closes, period);
        double std = INDICATORS.std(closes, period);
        double upper = mid + stdMult * std;
        return closes[closes.length - 1] > upper;
    }

    /**
     * 近 {@code lookback} 日振幅（%）是否不超过上限 —— 稳健/高股息类代理条件。
     */
    public static boolean amplitudeBelow(List<StockDailyData> data, int index, int lookback, double maxPct) {
        if (data == null || index < lookback - 1) {
            return false;
        }
        double high = Double.MIN_VALUE;
        double low = Double.MAX_VALUE;
        for (int i = index - lookback + 1; i <= index; i++) {
            double close = data.get(i).getClosePrice();
            high = Math.max(high, close);
            low = Math.min(low, close);
        }
        double mid = (high + low) / 2;
        if (mid <= 0) {
            return false;
        }
        return (high - low) / mid * 100 <= maxPct;
    }

    /**
     * 通道上破（海龟入场近似）：收盘价突破近 {@code lookback} 日最高价（不含当日）。
     */
    public static boolean channelBreakout(List<StockDailyData> data, int index, int lookback) {
        if (data == null || index < lookback || lookback < 1) {
            return false;
        }
        double channelHigh = Double.MIN_VALUE;
        for (int i = index - lookback; i < index; i++) {
            double h = data.get(i).getHighPrice() != null
                    ? data.get(i).getHighPrice() : data.get(i).getClosePrice();
            channelHigh = Math.max(channelHigh, h);
        }
        return data.get(index).getClosePrice() > channelHigh;
    }

    /**
     * 通道下破（海龟出场近似）：收盘价跌破近 {@code lookback} 日最低价（不含当日）。
     */
    public static boolean channelBreakdown(List<StockDailyData> data, int index, int lookback) {
        if (data == null || index < lookback || lookback < 1) {
            return false;
        }
        double channelLow = Double.MAX_VALUE;
        for (int i = index - lookback; i < index; i++) {
            double l = data.get(i).getLowPrice() != null
                    ? data.get(i).getLowPrice() : data.get(i).getClosePrice();
            channelLow = Math.min(channelLow, l);
        }
        return data.get(index).getClosePrice() < channelLow;
    }

    /**
     * 平均真实波幅 ATR（Wilder 简化：近 {@code period} 日 TR 均值）。
     */
    public static double atr(List<StockDailyData> data, int index, int period) {
        if (data == null || index < period || period < 1) {
            return Double.NaN;
        }
        double sum = 0;
        for (int i = index - period + 1; i <= index; i++) {
            sum += trueRange(data, i);
        }
        return sum / period;
    }

    /**
     * ATR 止损：收盘价相对入场价回撤 ≥ {@code multiplier} × ATR。
     */
    public static boolean atrStop(List<StockDailyData> data, int index, int period,
                                  double multiplier, double entryPrice) {
        if (entryPrice <= 0) {
            return false;
        }
        double atr = atr(data, index, period);
        if (Double.isNaN(atr) || atr <= 0) {
            return false;
        }
        double close = data.get(index).getClosePrice();
        return close <= entryPrice - multiplier * atr;
    }

    /**
     * 触及布林带下轨（均值回归做多入场）。
     */
    public static boolean bollLowerTouch(List<StockDailyData> data, int index, int period, double stdMult) {
        double[] closes = closesTo(data, index);
        if (closes == null || closes.length < period) {
            return false;
        }
        double mid = INDICATORS.sma(closes, period);
        double std = INDICATORS.std(closes, period);
        double lower = mid - stdMult * std;
        return closes[closes.length - 1] <= lower;
    }

    /**
     * 触及布林带上轨（均值回归出场 / 做空代理）。
     */
    public static boolean bollUpperTouch(List<StockDailyData> data, int index, int period, double stdMult) {
        double[] closes = closesTo(data, index);
        if (closes == null || closes.length < period) {
            return false;
        }
        double mid = INDICATORS.sma(closes, period);
        double std = INDICATORS.std(closes, period);
        double upper = mid + stdMult * std;
        return closes[closes.length - 1] >= upper;
    }

    /**
     * 收盘价回到布林中轨之上（均值回归止盈近似）。
     */
    public static boolean bollMidReclaim(List<StockDailyData> data, int index, int period) {
        double[] closes = closesTo(data, index);
        if (closes == null || closes.length < period) {
            return false;
        }
        double mid = INDICATORS.sma(closes, period);
        return closes[closes.length - 1] >= mid;
    }

    private static double trueRange(List<StockDailyData> data, int index) {
        StockDailyData bar = data.get(index);
        double high = bar.getHighPrice() != null ? bar.getHighPrice() : bar.getClosePrice();
        double low = bar.getLowPrice() != null ? bar.getLowPrice() : bar.getClosePrice();
        double close = bar.getClosePrice();
        double tr = high - low;
        if (index > 0) {
            double prevClose = data.get(index - 1).getClosePrice();
            tr = Math.max(tr, Math.abs(high - prevClose));
            tr = Math.max(tr, Math.abs(low - prevClose));
        }
        return tr;
    }

    private static double[] closesTo(List<StockDailyData> data, int index) {
        if (data == null || index < 0 || index >= data.size()) {
            return null;
        }
        double[] closes = new double[index + 1];
        for (int i = 0; i <= index; i++) {
            closes[i] = data.get(i).getClosePrice();
        }
        return closes;
    }
}
