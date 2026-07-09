package io.leavesfly.alphaforge.application.backtest.pairs;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配对交易统计工具（纯静态、无状态，仿照 {@code StockBarMath} 风格）。
 *
 * <p>提供两标的对齐、皮尔逊相关、OLS 对冲比率、价差与滚动 z-score，
 * 以及基于 AR(1) 的均值回复代理检验（协整检验的工程化简化）。</p>
 */
public final class PairStatistics {

    private PairStatistics() {
    }

    /**
     * 按交易日取交集对齐两只标的的日线序列，保持 A 的时间顺序。
     *
     * <p>配对交易要求两腿在同一交易日比较，交易日历不一致时（停牌/上市时间差异）
     * 必须先对齐，否则价差序列无意义。</p>
     */
    public static Aligned alignByDate(List<StockDailyData> a, List<StockDailyData> b) {
        Map<LocalDate, StockDailyData> indexB = new LinkedHashMap<>();
        if (b != null) {
            for (StockDailyData bar : b) {
                if (bar != null && bar.getTradeDate() != null && bar.getClosePrice() != null) {
                    indexB.put(bar.getTradeDate(), bar);
                }
            }
        }
        List<StockDailyData> barsA = new ArrayList<>();
        List<StockDailyData> barsB = new ArrayList<>();
        if (a != null) {
            for (StockDailyData barA : a) {
                if (barA == null || barA.getTradeDate() == null || barA.getClosePrice() == null) {
                    continue;
                }
                StockDailyData barB = indexB.get(barA.getTradeDate());
                if (barB != null) {
                    barsA.add(barA);
                    barsB.add(barB);
                }
            }
        }
        return new Aligned(barsA, barsB);
    }

    /** 算术平均。 */
    public static double mean(double[] xs) {
        if (xs == null || xs.length == 0) {
            return 0;
        }
        double s = 0;
        for (double x : xs) {
            s += x;
        }
        return s / xs.length;
    }

    /** 样本标准差（除以 n-1）。 */
    public static double stdDev(double[] xs) {
        if (xs == null || xs.length < 2) {
            return 0;
        }
        double m = mean(xs);
        double s = 0;
        for (double x : xs) {
            s += (x - m) * (x - m);
        }
        return Math.sqrt(s / (xs.length - 1));
    }

    /** 皮尔逊相关系数，范围 [-1, 1]；数据不足或零方差返回 0。 */
    public static double correlation(double[] a, double[] b) {
        if (a == null || b == null) {
            return 0;
        }
        int n = Math.min(a.length, b.length);
        if (n < 2) {
            return 0;
        }
        double ma = mean(a);
        double mb = mean(b);
        double cov = 0;
        double varA = 0;
        double varB = 0;
        for (int i = 0; i < n; i++) {
            double da = a[i] - ma;
            double db = b[i] - mb;
            cov += da * db;
            varA += da * da;
            varB += db * db;
        }
        double denom = Math.sqrt(varA * varB);
        return denom > 1e-12 ? cov / denom : 0;
    }

    /**
     * OLS 对冲比率 beta —— 回归 {@code A = alpha + beta * B} 的斜率。
     *
     * <p>用于定义价差 {@code spread = A - beta * B}，使价差在两标的同向波动时更平稳。
     * B 方差退化时回退为 1.0。</p>
     */
    public static double hedgeRatio(double[] a, double[] b) {
        if (a == null || b == null) {
            return 1.0;
        }
        int n = Math.min(a.length, b.length);
        if (n < 2) {
            return 1.0;
        }
        double ma = mean(a);
        double mb = mean(b);
        double cov = 0;
        double varB = 0;
        for (int i = 0; i < n; i++) {
            double db = b[i] - mb;
            cov += (a[i] - ma) * db;
            varB += db * db;
        }
        return varB > 1e-12 ? cov / varB : 1.0;
    }

    /** 价差序列 {@code spread[i] = A[i] - beta * B[i]}。 */
    public static double[] spreadSeries(double[] a, double[] b, double beta) {
        int n = Math.min(a != null ? a.length : 0, b != null ? b.length : 0);
        double[] spread = new double[n];
        for (int i = 0; i < n; i++) {
            spread[i] = a[i] - beta * b[i];
        }
        return spread;
    }

    /**
     * 滚动窗口 z-score —— 用 {@code [index-window+1, index]} 窗口的均值与样本标准差标准化。
     *
     * @return 标准分数；窗口数据不足或零方差返回 0
     */
    public static double zscore(double[] spread, int index, int window) {
        if (spread == null || window < 2 || index < window - 1 || index >= spread.length) {
            return 0;
        }
        double[] win = new double[window];
        System.arraycopy(spread, index - window + 1, win, 0, window);
        double m = mean(win);
        double sd = stdDev(win);
        return sd > 1e-12 ? (spread[index] - m) / sd : 0;
    }

    /**
     * 均值回复代理检验 —— 对价差做 AR(1) 回归 {@code Δspread_t = alpha + rho * spread_{t-1} + ε}。
     *
     * <p>这是 ADF 单位根检验的核心思想：{@code rho} 显著为负说明价差有向均值回复的拉力，
     * 该配对适合做统计套利。工程化简化避免引入 ADF 临界值表。</p>
     *
     * <ul>
     *   <li>{@code rho >= 0}：无回复（随机游走或发散），{@code meanReverting=false}，半衰期为正无穷</li>
     *   <li>{@code -1 < rho < 0}：均值回复，半衰期 {@code = -ln(2) / ln(1 + rho)}</li>
     * </ul>
     */
    public static MeanReversion meanReversionScore(double[] spread) {
        if (spread == null || spread.length < 3) {
            return new MeanReversion(0, Double.POSITIVE_INFINITY, false);
        }
        int n = spread.length - 1;
        // x = spread_{t-1}, y = Δspread_t = spread_t - spread_{t-1}
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = spread[i];
            y[i] = spread[i + 1] - spread[i];
        }
        double mx = mean(x);
        double my = mean(y);
        double cov = 0;
        double varX = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - mx;
            cov += dx * (y[i] - my);
            varX += dx * dx;
        }
        double rho = varX > 1e-12 ? cov / varX : 0;
        boolean meanReverting = rho < 0 && rho > -2;
        double halfLife;
        if (rho < 0 && (1 + rho) > 0) {
            halfLife = -Math.log(2) / Math.log(1 + rho);
        } else {
            halfLife = Double.POSITIVE_INFINITY;
        }
        return new MeanReversion(rho, halfLife, meanReverting);
    }

    /**
     * 对齐后的两腿日线序列（交易日已取交集）。
     */
    public static final class Aligned {
        private final List<StockDailyData> barsA;
        private final List<StockDailyData> barsB;
        private final double[] closesA;
        private final double[] closesB;

        Aligned(List<StockDailyData> barsA, List<StockDailyData> barsB) {
            this.barsA = barsA;
            this.barsB = barsB;
            this.closesA = toCloses(barsA);
            this.closesB = toCloses(barsB);
        }

        private static double[] toCloses(List<StockDailyData> bars) {
            double[] closes = new double[bars.size()];
            for (int i = 0; i < bars.size(); i++) {
                closes[i] = bars.get(i).getClosePrice();
            }
            return closes;
        }

        public List<StockDailyData> getBarsA() {
            return barsA;
        }

        public List<StockDailyData> getBarsB() {
            return barsB;
        }

        public double[] getClosesA() {
            return closesA;
        }

        public double[] getClosesB() {
            return closesB;
        }

        public int size() {
            return barsA.size();
        }
    }

    /**
     * 均值回复检验结果。
     *
     * @param rho           AR(1) 回归斜率（负值表示回复）
     * @param halfLife      回复半衰期（交易日）
     * @param meanReverting 是否判定为均值回复
     */
    public record MeanReversion(double rho, double halfLife, boolean meanReverting) {
    }
}
