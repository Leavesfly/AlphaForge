package io.leavesfly.alphaforge.domain.service.factor;

/**
 * 因子横截面预处理算子（纯静态工具，零依赖）。
 *
 * <p>标准因子研究流程：去极值 → 标准化 → 中性化。本类提供这些算子的单一实现，
 * 供因子评估、分层回测、选股打分等模块统一复用，避免各处各写一套。</p>
 *
 * <p>所有方法均为“对当期横截面向量”操作（长度为股票数），不跨期。</p>
 */
public final class CrossSectionalOps {

    private CrossSectionalOps() {
    }

    /**
     * MAD 去极值（中位数绝对偏差法）。
     *
     * <p>以中位数 med 与 MAD 为基准，将超出 [med - k·1.4826·MAD, med + k·1.4826·MAD]
     * 的值截断到边界。1.4826 使 MAD 在正态分布下等价于标准差。</p>
     *
     * @param values 原始因子值（原地不修改，返回新数组）
     * @param k      截断倍数（常用 3.0）
     */
    public static double[] winsorizeMad(double[] values, double k) {
        int n = values.length;
        if (n == 0) return new double[0];
        double med = median(values);
        double[] absDev = new double[n];
        for (int i = 0; i < n; i++) absDev[i] = Math.abs(values[i] - med);
        double mad = median(absDev) * 1.4826;
        double lo = med - k * mad;
        double hi = med + k * mad;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = mad <= 1e-12 ? values[i] : Math.max(lo, Math.min(hi, values[i]));
        }
        return out;
    }

    /**
     * 分位数去极值：将低于下分位/高于上分位的值截断到分位边界。
     *
     * @param lowerQuantile 如 0.01
     * @param upperQuantile 如 0.99
     */
    public static double[] winsorizeQuantile(double[] values, double lowerQuantile, double upperQuantile) {
        int n = values.length;
        if (n == 0) return new double[0];
        double lo = quantile(values, lowerQuantile);
        double hi = quantile(values, upperQuantile);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = Math.max(lo, Math.min(hi, values[i]));
        return out;
    }

    /** Z-Score 标准化（减均值除标准差） */
    public static double[] zscore(double[] values) {
        int n = values.length;
        if (n == 0) return new double[0];
        double mean = 0;
        for (double v : values) mean += v;
        mean /= n;
        double var = 0;
        for (double v : values) var += (v - mean) * (v - mean);
        double std = Math.sqrt(var / Math.max(n - 1, 1));
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = std <= 1e-12 ? 0 : (values[i] - mean) / std;
        return out;
    }

    /** 分位数排名标准化（映射到 [0,1]，对异常值稳健） */
    public static double[] rankNormalize(double[] values) {
        int n = values.length;
        if (n == 0) return new double[0];
        if (n == 1) return new double[]{0.5};
        double[] ranks = averageRanks(values); // 1..n
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = (ranks[i] - 1) / (n - 1);
        return out;
    }

    /**
     * 因子中性化：对暴露矩阵做 OLS 回归，返回残差作为中性化后的因子。
     *
     * <p>暴露矩阵 exposures[i] 为第 i 只股票的暴露向量（如 [log市值, 行业哑变量...]），
     * 内部自动追加截距项。求解 β=(XᵀX)⁻¹Xᵀy，残差 = y - Xβ。矩阵奇异时原样返回。</p>
     *
     * @param factor    因子值向量（长度 n）
     * @param exposures 暴露矩阵（n × k），可为 null（则原样返回）
     */
    public static double[] neutralize(double[] factor, double[][] exposures) {
        int n = factor.length;
        if (exposures == null || exposures.length != n || n == 0) return factor.clone();
        int k = exposures[0].length + 1; // +1 截距
        double[][] x = new double[n][k];
        for (int i = 0; i < n; i++) {
            x[i][0] = 1.0;
            System.arraycopy(exposures[i], 0, x[i], 1, k - 1);
        }
        // XᵀX (k×k) 与 Xᵀy (k)
        double[][] xtx = new double[k][k];
        double[] xty = new double[k];
        for (int a = 0; a < k; a++) {
            for (int b = 0; b < k; b++) {
                double s = 0;
                for (int i = 0; i < n; i++) s += x[i][a] * x[i][b];
                xtx[a][b] = s;
            }
            double sy = 0;
            for (int i = 0; i < n; i++) sy += x[i][a] * factor[i];
            xty[a] = sy;
        }
        double[][] inv = invert(xtx);
        if (inv == null) return factor.clone();
        double[] beta = new double[k];
        for (int a = 0; a < k; a++) {
            double s = 0;
            for (int b = 0; b < k; b++) s += inv[a][b] * xty[b];
            beta[a] = s;
        }
        double[] residual = new double[n];
        for (int i = 0; i < n; i++) {
            double pred = 0;
            for (int a = 0; a < k; a++) pred += x[i][a] * beta[a];
            residual[i] = factor[i] - pred;
        }
        return residual;
    }

    // ==================== 统计工具 ====================

    public static double median(double[] values) {
        return quantile(values, 0.5);
    }

    /** 线性插值分位数（p∈[0,1]） */
    public static double quantile(double[] values, double p) {
        int n = values.length;
        if (n == 0) return 0;
        if (n == 1) return values[0];
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        double pos = p * (n - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return sorted[lo];
        double frac = pos - lo;
        return sorted[lo] * (1 - frac) + sorted[hi] * frac;
    }

    /** 平均秩（处理并列，秩从 1 开始） */
    public static double[] averageRanks(double[] values) {
        int n = values.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(values[a], values[b]));
        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n - 1 && values[idx[j + 1]] == values[idx[i]]) j++;
            double avg = (i + j + 2.0) / 2.0;
            for (int t = i; t <= j; t++) ranks[idx[t]] = avg;
            i = j + 1;
        }
        return ranks;
    }

    /** Spearman 秩相关 */
    public static double spearman(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        if (n < 3) return Double.NaN;
        double[] ra = averageRanks(a);
        double[] rb = averageRanks(b);
        double sa = 0, sb = 0, sab = 0, sa2 = 0, sb2 = 0;
        for (int i = 0; i < n; i++) {
            sa += ra[i];
            sb += rb[i];
            sab += ra[i] * rb[i];
            sa2 += ra[i] * ra[i];
            sb2 += rb[i] * rb[i];
        }
        double denom = Math.sqrt((n * sa2 - sa * sa) * (n * sb2 - sb * sb));
        return denom == 0 ? 0 : (n * sab - sa * sb) / denom;
    }

    /** 高斯-约当求逆，奇异返回 null */
    private static double[][] invert(double[][] matrix) {
        int n = matrix.length;
        double[][] a = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, n);
            a[i][n + i] = 1.0;
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            double max = Math.abs(a[col][col]);
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > max) {
                    max = Math.abs(a[r][col]);
                    pivot = r;
                }
            }
            if (max < 1e-12) return null;
            double[] tmp = a[col];
            a[col] = a[pivot];
            a[pivot] = tmp;
            double pv = a[col][col];
            for (int j = 0; j < 2 * n; j++) a[col][j] /= pv;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = a[r][col];
                if (f == 0) continue;
                for (int j = 0; j < 2 * n; j++) a[r][j] -= f * a[col][j];
            }
        }
        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(a[i], n, inv[i], 0, n);
        return inv;
    }
}
