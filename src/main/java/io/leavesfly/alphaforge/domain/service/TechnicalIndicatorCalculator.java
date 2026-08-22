package io.leavesfly.alphaforge.domain.service;

/**
 * 技术指标计算器（纯领域算法，零外部依赖）
 *
 * 提供 SMA / EMA / RSI / 标准差 / MACD金叉死叉 / 均线突破 / 量比 等基础计算与判断。
 * 输入为原始数值数组，不涉及任何 IO，供 domain.service 与 application.service 复用，
 * 消除指标算法的重复实现。
 */
public class TechnicalIndicatorCalculator {

    /**
     * 简单移动平均（取最后 period 个的均值）
     */
    public double sma(double[] data, int period) {
        int len = data.length;
        if (len < period) return data[len - 1];
        double sum = 0;
        for (int i = len - period; i < len; i++) sum += data[i];
        return sum / period;
    }

    /**
     * 指数移动平均（返回完整 EMA 序列）
     */
    public double[] ema(double[] data, int period) {
        int len = data.length;
        double[] ema = new double[len];
        double multiplier = 2.0 / (period + 1);
        ema[0] = data[0];
        for (int i = 1; i < len; i++) {
            ema[i] = (data[i] - ema[i - 1]) * multiplier + ema[i - 1];
        }
        return ema;
    }

    /**
     * RSI 指标（取最后 period 个计算）
     */
    public double rsi(double[] closes, int period) {
        return rsiFromCloses(closes, period);
    }

    /**
     * RSI 指标根据回测口径返回：数据不足时降级为中性值 50。
     *
     * <p>供实例方法 {@link #rsi(double[], int)} 与需要静态调用的场景（如
     * 回测条件求值、策略引擎）使用——这些场景需要一个可直接参与比较的
     * 数值，不区分“数据不足”与“确实中性”。</p>
     *
     * <p><b>口径差异提醒</b>：决策链（三灯评估）需要区分两者，数据不足必须
     * 不下结论，因此改用 {@link #rsiOrNaN(double[], int)}。两个方法共用同一核心
     * 算法，仅缺失值约定不同；请勿将二者合并。</p>
     *
     * @param closes 收盘价序列（按时间升序），使用最后 {@code period} 个区间
     * @param period 周期
     * @return RSI 值 [0,100]，数据不足返回中性值 50
     */
    public static double rsiFromCloses(double[] closes, int period) {
        double rsi = rsiOrNaN(closes, period);
        return Double.isNaN(rsi) ? 50 : rsi;
    }

    /**
     * RSI 指标核心算法（全局唯一实现）—— 数据不足时返回 {@code NaN}。
     *
     * <p>供需要区分“无法计算”与“计算为中性”的调用方使用（如决策三灯），
     * 调用方应以 {@code Double.isNaN} 守卫后再参与阈值判断。</p>
     *
     * <p>完全无波动的序列（涨跌幅均为 0，如停牌、一字板）返回中性值 50；
     * 仅当区间内有涨无跌时才返回 100。</p>
     *
     * @param closes 收盘价序列（按时间升序），使用最后 {@code period} 个区间
     * @param period 周期
     * @return RSI 值 [0,100]；数据不足返回 {@code Double.NaN}
     */
    public static double rsiOrNaN(double[] closes, int period) {
        int len = closes.length;
        if (len <= period) return Double.NaN;
        double gainSum = 0, lossSum = 0;
        for (int i = len - period; i < len; i++) {
            double change = closes[i] - closes[i - 1];
            if (change > 0) gainSum += change;
            else lossSum += Math.abs(change);
        }
        // 完全无波动：无涨也无跌，属中性而非超买
        if (gainSum == 0 && lossSum == 0) return 50;
        if (lossSum == 0) return 100;
        double rs = gainSum / lossSum;
        return 100 - (100 / (1 + rs));
    }

    /**
     * 标准差（取最后 period 个）
     */
    public double std(double[] data, int period) {
        int len = data.length;
        if (len < period) return 0;
        double mean = sma(data, period);
        double sumSq = 0;
        for (int i = len - period; i < len; i++) {
            sumSq += Math.pow(data[i] - mean, 2);
        }
        return Math.sqrt(sumSq / period);
    }

    /**
     * MACD 金叉判断（DIF 由下上穿 DEA）
     *
     * 采用标准实现：DIF = EMA12 - EMA26，DEA = EMA(DIF, 9)。
     * 数据不足返回 false。
     */
    public boolean isMacdGoldenCross(double[] closes) {
        int len = closes.length;
        if (len < 27) return false;
        double[] dif = macdDif(closes);
        double[] dea = ema(dif, 9);
        return dif[len - 1] > dea[len - 1] && dif[len - 2] < dea[len - 2];
    }

    /**
     * MACD 死叉判断（DIF 由上下穿 DEA）
     */
    public boolean isMacdDeathCross(double[] closes) {
        int len = closes.length;
        if (len < 27) return false;
        double[] dif = macdDif(closes);
        double[] dea = ema(dif, 9);
        return dif[len - 1] < dea[len - 1] && dif[len - 2] > dea[len - 2];
    }

    /**
     * 均线突破（今日收盘价突破 N 日均线，且昨日未突破）
     */
    public boolean isMaBreakout(double[] closes, int period) {
        int len = closes.length;
        if (len < period + 1) return false;
        double ma = 0;
        for (int i = len - period - 1; i < len - 1; i++) ma += closes[i];
        ma /= period;
        return closes[len - 1] > ma && closes[len - 2] <= ma;
    }

    /**
     * 量比（今日成交量 / 前5日平均成交量）
     */
    public double volumeRatio(long[] volumes) {
        int len = volumes.length;
        if (len < 6) return 1.0;
        long todayVol = volumes[len - 1];
        long avg5 = 0;
        for (int i = len - 6; i < len - 1; i++) avg5 += volumes[i];
        avg5 /= 5;
        return avg5 > 0 ? (double) todayVol / avg5 : 1.0;
    }

    /**
     * 计算 MACD 的 DIF 线（EMA12 - EMA26）
     */
    private double[] macdDif(double[] closes) {
        double[] ema12 = ema(closes, 12);
        double[] ema26 = ema(closes, 26);
        double[] dif = new double[closes.length];
        for (int i = 0; i < closes.length; i++) {
            dif[i] = ema12[i] - ema26[i];
        }
        return dif;
    }
}
