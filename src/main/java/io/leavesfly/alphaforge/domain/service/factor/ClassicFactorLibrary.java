package io.leavesfly.alphaforge.domain.service.factor;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.TechnicalIndicatorCalculator;

import java.util.List;

/**
 * 经典因子库（纯领域算法）。
 *
 * <p>提供一组开箱即用、点位可计算（point-in-time）的横截面 alpha 因子。
 * 输入为单只股票截至当前时点的历史 K 线（升序），输出该时点的因子值。
 * 覆盖动量、反转、波动、流动性、量能、彩票效应等常见风格。</p>
 */
public class ClassicFactorLibrary {

    private static final TechnicalIndicatorCalculator TA = new TechnicalIndicatorCalculator();

    /** 支持的因子名称 */
    public static final List<String> FACTOR_NAMES = List.of(
            "momentum_20", "momentum_60", "reversal_5", "volatility_20",
            "turnover_mean_20", "volume_ratio", "rsi_14", "ma_gap_20",
            "amihud_illiquidity_20", "max_return_20"
    );

    public List<String> names() {
        return FACTOR_NAMES;
    }

    public boolean supports(String factorName) {
        return FACTOR_NAMES.contains(factorName);
    }

    /**
     * 计算指定因子在历史序列末端的值。
     *
     * @param factorName 因子名（见 {@link #FACTOR_NAMES}）
     * @param history    截至当前时点的历史 K 线（时间升序）
     * @return 因子值；数据不足或不支持返回 {@link Double#NaN}
     */
    public double compute(String factorName, List<StockDailyData> history) {
        if (history == null || history.isEmpty()) return Double.NaN;
        return switch (factorName) {
            case "momentum_20" -> momentum(history, 20);
            case "momentum_60" -> momentum(history, 60);
            case "reversal_5" -> -momentum(history, 5);
            case "volatility_20" -> volatility(history, 20);
            case "turnover_mean_20" -> turnoverMean(history, 20);
            case "volume_ratio" -> volumeRatio(history);
            case "rsi_14" -> rsi(history, 14);
            case "ma_gap_20" -> maGap(history, 20);
            case "amihud_illiquidity_20" -> amihud(history, 20);
            case "max_return_20" -> maxReturn(history, 20);
            default -> Double.NaN;
        };
    }

    // ==================== 因子实现 ====================

    /** N 日动量：close[t]/close[t-N] - 1 */
    private double momentum(List<StockDailyData> h, int n) {
        int size = h.size();
        if (size <= n) return Double.NaN;
        Double cur = h.get(size - 1).getClosePrice();
        Double past = h.get(size - 1 - n).getClosePrice();
        if (cur == null || past == null || past <= 0) return Double.NaN;
        return cur / past - 1;
    }

    /** N 日日收益率标准差 */
    private double volatility(List<StockDailyData> h, int n) {
        int size = h.size();
        if (size <= n) return Double.NaN;
        double[] rets = new double[n];
        for (int i = 0; i < n; i++) {
            Double c1 = h.get(size - n + i).getClosePrice();
            Double c0 = h.get(size - n + i - 1).getClosePrice();
            rets[i] = (c0 != null && c0 > 0 && c1 != null) ? (c1 - c0) / c0 : 0;
        }
        double mean = 0;
        for (double r : rets) mean += r;
        mean /= n;
        double var = 0;
        for (double r : rets) var += (r - mean) * (r - mean);
        return Math.sqrt(var / Math.max(n - 1, 1));
    }

    /** N 日平均换手率 */
    private double turnoverMean(List<StockDailyData> h, int n) {
        int size = h.size();
        if (size < n) return Double.NaN;
        double sum = 0;
        int cnt = 0;
        for (int i = size - n; i < size; i++) {
            Double t = h.get(i).getTurnoverRate();
            if (t != null) {
                sum += t;
                cnt++;
            }
        }
        return cnt > 0 ? sum / cnt : Double.NaN;
    }

    /** 量比：今日量 / 前5日均量 */
    private double volumeRatio(List<StockDailyData> h) {
        int size = h.size();
        if (size < 6) return Double.NaN;
        long[] vols = new long[6];
        for (int i = 0; i < 6; i++) {
            Long v = h.get(size - 6 + i).getVolume();
            vols[i] = v != null ? v : 0;
        }
        return TA.volumeRatio(vols);
    }

    /** RSI(14) —— 复用统一指标核心 */
    private double rsi(List<StockDailyData> h, int period) {
        int size = h.size();
        if (size <= period) return Double.NaN;
        double[] closes = new double[size];
        for (int i = 0; i < size; i++) {
            Double c = h.get(i).getClosePrice();
            closes[i] = c != null ? c : 0;
        }
        return TechnicalIndicatorCalculator.rsiFromCloses(closes, period);
    }

    /** 均线乖离：(close - MA_N) / MA_N */
    private double maGap(List<StockDailyData> h, int n) {
        int size = h.size();
        if (size < n) return Double.NaN;
        double[] closes = new double[size];
        for (int i = 0; i < size; i++) {
            Double c = h.get(i).getClosePrice();
            closes[i] = c != null ? c : 0;
        }
        double ma = TA.sma(closes, n);
        double last = closes[size - 1];
        return ma > 0 ? (last - ma) / ma : Double.NaN;
    }

    /** Amihud 非流动性：N 日 平均(|日收益| / 成交额) ×1e8（放大便于阅读） */
    private double amihud(List<StockDailyData> h, int n) {
        int size = h.size();
        if (size <= n) return Double.NaN;
        double sum = 0;
        int cnt = 0;
        for (int i = size - n; i < size; i++) {
            Double c1 = h.get(i).getClosePrice();
            Double c0 = h.get(i - 1).getClosePrice();
            Double amount = h.get(i).getAmount();
            if (c0 != null && c0 > 0 && c1 != null && amount != null && amount > 0) {
                sum += Math.abs((c1 - c0) / c0) / amount;
                cnt++;
            }
        }
        return cnt > 0 ? sum / cnt * 1e8 : Double.NaN;
    }

    /** 彩票因子：N 日内单日最大收益 */
    private double maxReturn(List<StockDailyData> h, int n) {
        int size = h.size();
        if (size <= n) return Double.NaN;
        double max = -Double.MAX_VALUE;
        for (int i = size - n; i < size; i++) {
            Double c1 = h.get(i).getClosePrice();
            Double c0 = h.get(i - 1).getClosePrice();
            if (c0 != null && c0 > 0 && c1 != null) {
                max = Math.max(max, (c1 - c0) / c0);
            }
        }
        return max == -Double.MAX_VALUE ? Double.NaN : max;
    }
}
