package io.leavesfly.alphaforge.domain.service.performance;

import java.util.List;

/**
 * 统一绩效分析器（纯领域算法，零外部依赖）。
 *
 * <p>从“周期收益率序列”出发，统一计算夏普 / 索提诺 / 卡尔玛 / 最大回撤 / 年化收益与波动，
 * 以及相对基准的 alpha / beta / 信息比率。回测、蒙特卡洛、Walk-Forward、组合等模块
 * 统一复用本类，消除各处重复且口径不一的绩效计算。</p>
 *
 * <p>默认按日频年化（{@code periodsPerYear=252}）。收益率以小数表示（0.01 = 1%）。</p>
 */
public class PerformanceAnalytics {

    public static final int DAILY_PERIODS_PER_YEAR = 252;

    private final int periodsPerYear;
    private final double annualRiskFreeRate;

    public PerformanceAnalytics() {
        this(DAILY_PERIODS_PER_YEAR, 0.03);
    }

    public PerformanceAnalytics(int periodsPerYear, double annualRiskFreeRate) {
        this.periodsPerYear = periodsPerYear;
        this.annualRiskFreeRate = annualRiskFreeRate;
    }

    /** 无基准的绩效分析 */
    public PerformanceMetrics analyze(List<Double> returns) {
        return analyze(returns, null);
    }

    /**
     * 绩效分析。
     *
     * @param returns          组合周期收益率序列
     * @param benchmarkReturns 基准周期收益率序列（可为 null；非 null 时长度需与 returns 一致才计算 alpha/beta）
     */
    public PerformanceMetrics analyze(List<Double> returns, List<Double> benchmarkReturns) {
        if (returns == null || returns.isEmpty()) {
            return new PerformanceMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        int n = returns.size();
        double rfPerPeriod = annualRiskFreeRate / periodsPerYear;

        double mean = mean(returns);
        double std = std(returns, mean);
        double downsideStd = downsideDeviation(returns, rfPerPeriod);

        double totalReturn = cumulativeReturn(returns);
        double annualizedReturn = mean * periodsPerYear;
        double annualizedVol = std * Math.sqrt(periodsPerYear);
        double annualizedDownsideVol = downsideStd * Math.sqrt(periodsPerYear);
        double maxDrawdown = maxDrawdown(returns);

        double sharpe = annualizedVol > 1e-12 ? (annualizedReturn - annualRiskFreeRate) / annualizedVol : 0;
        double sortino = annualizedDownsideVol > 1e-12 ? (annualizedReturn - annualRiskFreeRate) / annualizedDownsideVol : 0;
        double calmar = maxDrawdown > 1e-12 ? annualizedReturn / maxDrawdown : 0;
        double winRate = winRate(returns);

        double alpha = 0, beta = 0, infoRatio = 0;
        if (benchmarkReturns != null && benchmarkReturns.size() == n && n > 1) {
            double benchMean = mean(benchmarkReturns);
            double benchVar = variance(benchmarkReturns, benchMean);
            double cov = covariance(returns, mean, benchmarkReturns, benchMean);
            beta = benchVar > 1e-18 ? cov / benchVar : 0;
            // 年化 alpha（CAPM）：α = (Rp - Rf) - β(Rb - Rf)，年化
            double annualBench = benchMean * periodsPerYear;
            alpha = (annualizedReturn - annualRiskFreeRate) - beta * (annualBench - annualRiskFreeRate);
            infoRatio = informationRatio(returns, benchmarkReturns);
        }

        return new PerformanceMetrics(totalReturn, annualizedReturn, annualizedVol,
                sharpe, sortino, calmar, maxDrawdown, winRate, alpha, beta, infoRatio, n);
    }

    // ==================== 基础统计 ====================

    /** 累计收益率（复利）：∏(1+r) - 1 */
    public double cumulativeReturn(List<Double> returns) {
        double cum = 1.0;
        for (double r : returns) cum *= (1 + r);
        return cum - 1;
    }

    /** 最大回撤（正数，基于收益序列构造净值曲线） */
    public double maxDrawdown(List<Double> returns) {
        double peak = 1.0, value = 1.0, maxDd = 0;
        for (double r : returns) {
            value *= (1 + r);
            if (value > peak) peak = value;
            double dd = peak > 0 ? (peak - value) / peak : 0;
            if (dd > maxDd) maxDd = dd;
        }
        return maxDd;
    }

    /** 日胜率 */
    public double winRate(List<Double> returns) {
        if (returns.isEmpty()) return 0;
        int wins = 0;
        for (double r : returns) if (r > 0) wins++;
        return (double) wins / returns.size();
    }

    /** 信息比率：年化超额收益 / 年化跟踪误差 */
    public double informationRatio(List<Double> returns, List<Double> benchmark) {
        int n = Math.min(returns.size(), benchmark.size());
        if (n < 2) return 0;
        double[] active = new double[n];
        for (int i = 0; i < n; i++) active[i] = returns.get(i) - benchmark.get(i);
        double m = 0;
        for (double a : active) m += a;
        m /= n;
        double var = 0;
        for (double a : active) var += (a - m) * (a - m);
        var /= (n - 1);
        double te = Math.sqrt(var) * Math.sqrt(periodsPerYear);
        return te > 1e-12 ? (m * periodsPerYear) / te : 0;
    }

    private double mean(List<Double> xs) {
        double s = 0;
        for (double x : xs) s += x;
        return xs.isEmpty() ? 0 : s / xs.size();
    }

    private double variance(List<Double> xs, double mean) {
        if (xs.size() < 2) return 0;
        double s = 0;
        for (double x : xs) s += (x - mean) * (x - mean);
        return s / (xs.size() - 1);
    }

    private double std(List<Double> xs, double mean) {
        return Math.sqrt(variance(xs, mean));
    }

    private double covariance(List<Double> a, double meanA, List<Double> b, double meanB) {
        int n = Math.min(a.size(), b.size());
        if (n < 2) return 0;
        double s = 0;
        for (int i = 0; i < n; i++) s += (a.get(i) - meanA) * (b.get(i) - meanB);
        return s / (n - 1);
    }

    /** 下行标准差（低于最低可接受收益 mar 的部分） */
    private double downsideDeviation(List<Double> xs, double mar) {
        if (xs.isEmpty()) return 0;
        double s = 0;
        int count = 0;
        for (double x : xs) {
            double d = Math.min(x - mar, 0);
            s += d * d;
            count++;
        }
        return Math.sqrt(s / count);
    }
}
