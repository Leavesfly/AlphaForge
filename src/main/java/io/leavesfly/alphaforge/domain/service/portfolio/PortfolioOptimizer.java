package io.leavesfly.alphaforge.domain.service.portfolio;

import java.util.List;

/**
 * 组合优化器（纯领域算法，零外部依赖）。
 *
 * <p>输入为各资产的周期收益率序列，输出长仓（权重非负、和为 1）最优权重。
 * 支持均值-方差、最小方差、最大夏普、风险平价、逆波动率、等权 6 种目标。
 * 协方差矩阵求逆采用高斯-约当消元；求解失败或矩阵病态时回退到等权，保证鲁棒性。</p>
 *
 * <p>收益率按“日频”输入时，年化因子默认 252（可配置），用于把日度均值/协方差
 * 换算成年化预期收益与年化波动率。</p>
 */
public class PortfolioOptimizer {

    /** 交易日年化因子（日频 → 年化） */
    private static final int TRADING_DAYS = 252;
    /** 风险平价迭代上限 */
    private static final int RISK_PARITY_MAX_ITER = 1000;
    private static final double RISK_PARITY_TOL = 1e-9;

    /**
     * 执行组合优化。
     *
     * @param symbols       资产代码（长度 n）
     * @param periodReturns 周期收益率矩阵，returns[i] 为第 i 个资产的收益率序列（长度 T）
     * @param objective     优化目标
     * @param riskAversion  风险厌恶系数 λ（仅 MEAN_VARIANCE 使用，需 &gt; 0）
     * @param annualRiskFreeRate 年化无风险利率（用于夏普/最大夏普，如 0.02）
     * @return 优化结果（年化口径）
     */
    public PortfolioOptimizationResult optimize(List<String> symbols, double[][] periodReturns,
                                                OptimizationObjective objective,
                                                double riskAversion, double annualRiskFreeRate) {
        int n = periodReturns.length;
        if (n == 0) {
            return new PortfolioOptimizationResult(symbols, new double[0], objective, 0, 0, 0, new double[0], 0);
        }

        double[] meanDaily = meanReturns(periodReturns);
        double[][] covDaily = covarianceMatrix(periodReturns);

        double[] weights = switch (objective) {
            case EQUAL_WEIGHT -> equalWeight(n);
            case INVERSE_VOLATILITY -> inverseVolatility(covDaily);
            case RISK_PARITY -> riskParity(covDaily);
            case MIN_VARIANCE -> minVariance(covDaily);
            case MAX_SHARPE -> maxSharpe(meanDaily, covDaily, annualRiskFreeRate / TRADING_DAYS);
            case MEAN_VARIANCE -> meanVariance(meanDaily, covDaily, Math.max(riskAversion, 1e-6));
        };

        weights = sanitizeLongOnly(weights, n);
        return buildResult(symbols, weights, objective, meanDaily, covDaily, annualRiskFreeRate);
    }

    // ==================== 统计量 ====================

    /** 逐资产均值（周期均值） */
    public double[] meanReturns(double[][] returns) {
        int n = returns.length;
        double[] mean = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (double r : returns[i]) sum += r;
            mean[i] = returns[i].length == 0 ? 0 : sum / returns[i].length;
        }
        return mean;
    }

    /** 样本协方差矩阵（无偏，除以 T-1） */
    public double[][] covarianceMatrix(double[][] returns) {
        int n = returns.length;
        int t = returns[0].length;
        double[] mean = meanReturns(returns);
        double[][] cov = new double[n][n];
        int denom = Math.max(t - 1, 1);
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double s = 0;
                for (int k = 0; k < t; k++) {
                    s += (returns[i][k] - mean[i]) * (returns[j][k] - mean[j]);
                }
                double c = s / denom;
                cov[i][j] = c;
                cov[j][i] = c;
            }
        }
        return cov;
    }

    // ==================== 各目标权重求解 ====================

    private double[] equalWeight(int n) {
        double[] w = new double[n];
        java.util.Arrays.fill(w, 1.0 / n);
        return w;
    }

    private double[] inverseVolatility(double[][] cov) {
        int n = cov.length;
        double[] w = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double vol = Math.sqrt(Math.max(cov[i][i], 1e-12));
            w[i] = 1.0 / vol;
            sum += w[i];
        }
        for (int i = 0; i < n; i++) w[i] /= sum;
        return w;
    }

    /** 最小方差：w ∝ Σ⁻¹·1 */
    private double[] minVariance(double[][] cov) {
        int n = cov.length;
        double[][] inv = invert(shrink(cov));
        if (inv == null) return equalWeight(n);
        double[] ones = new double[n];
        java.util.Arrays.fill(ones, 1.0);
        double[] w = matVec(inv, ones);
        return normalizeSum(w);
    }

    /** 最大夏普（切点组合）：w ∝ Σ⁻¹·(μ - rf) */
    private double[] maxSharpe(double[] meanDaily, double[][] cov, double rfDaily) {
        int n = cov.length;
        double[][] inv = invert(shrink(cov));
        if (inv == null) return equalWeight(n);
        double[] excess = new double[n];
        for (int i = 0; i < n; i++) excess[i] = meanDaily[i] - rfDaily;
        double[] w = matVec(inv, excess);
        return normalizeSum(w);
    }

    /**
     * 均值-方差效用最大化：max wᵀμ - (λ/2)wᵀΣw s.t. 1ᵀw=1。
     * 解析解：w = Σ⁻¹[ μ/λ + ((1 - 1ᵀΣ⁻¹μ/λ)/(1ᵀΣ⁻¹1))·1 ]。
     */
    private double[] meanVariance(double[] meanDaily, double[][] cov, double lambda) {
        int n = cov.length;
        double[][] inv = invert(shrink(cov));
        if (inv == null) return equalWeight(n);
        double[] ones = new double[n];
        java.util.Arrays.fill(ones, 1.0);
        double[] invMu = matVec(inv, meanDaily);
        double[] invOne = matVec(inv, ones);
        double oneInvOne = dot(ones, invOne);
        double oneInvMu = dot(ones, invMu);
        double c = (1.0 - oneInvMu / lambda) / oneInvOne;
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = invMu[i] / lambda + c * invOne[i];
        return normalizeSum(w);
    }

    /**
     * 风险平价（ERC）：sqrt 阻尼的乘性不动点迭代。
     *
     * <p>目标是各资产风险贡献 RC_i = w_i·(Σw)_i / (wᵀΣw) 都等于 b_i = 1/n。
     * 更新式 w_i ← w_i·sqrt(b_i / RC_i) 在 RC_i=b_i 处为不动点，且对正定 Σ 收敛稳定，
     * 避免了简单不动点在“逆方差 ↔ 等权”之间的振荡。</p>
     */
    private double[] riskParity(double[][] cov) {
        int n = cov.length;
        double[] w = inverseVolatility(cov); // 以逆波动率为热启动，加速收敛
        double target = 1.0 / n;
        for (int iter = 0; iter < RISK_PARITY_MAX_ITER; iter++) {
            double[] sigmaW = matVec(cov, w);
            double var = Math.max(dot(w, sigmaW), 1e-18);
            double[] next = new double[n];
            double sum = 0;
            for (int i = 0; i < n; i++) {
                double rc = w[i] * sigmaW[i] / var;      // 当前风险贡献占比
                double factor = Math.sqrt(target / Math.max(rc, 1e-15));
                next[i] = Math.max(w[i] * factor, 1e-15);
                sum += next[i];
            }
            double diff = 0;
            for (int i = 0; i < n; i++) {
                next[i] /= sum;
                diff += Math.abs(next[i] - w[i]);
            }
            w = next;
            if (diff < RISK_PARITY_TOL) break;
        }
        return w;
    }

    // ==================== 结果组装 ====================

    private PortfolioOptimizationResult buildResult(List<String> symbols, double[] w,
                                                    OptimizationObjective objective,
                                                    double[] meanDaily, double[][] cov,
                                                    double annualRiskFreeRate) {
        int n = w.length;
        double portDailyReturn = dot(w, meanDaily);
        double[] sigmaW = matVec(cov, w);
        double portDailyVar = Math.max(dot(w, sigmaW), 0);
        double portDailyVol = Math.sqrt(portDailyVar);

        double annualReturn = portDailyReturn * TRADING_DAYS;
        double annualVol = portDailyVol * Math.sqrt(TRADING_DAYS);
        double sharpe = annualVol > 1e-12 ? (annualReturn - annualRiskFreeRate) / annualVol : 0;

        // 风险贡献占比 RC_i = w_i*(Σw)_i / (wᵀΣw)
        double[] rc = new double[n];
        if (portDailyVar > 1e-18) {
            for (int i = 0; i < n; i++) rc[i] = w[i] * sigmaW[i] / portDailyVar;
        }

        // 分散化比率 = Σ(w_i·σ_i) / σ_p
        double weightedVol = 0;
        for (int i = 0; i < n; i++) weightedVol += w[i] * Math.sqrt(Math.max(cov[i][i], 0));
        double diversification = annualVol > 1e-12 ? weightedVol * Math.sqrt(TRADING_DAYS) / annualVol : 1.0;

        return new PortfolioOptimizationResult(symbols, w, objective,
                annualReturn, annualVol, sharpe, rc, diversification);
    }

    // ==================== 线性代数工具 ====================

    /** 长仓化：负权归零、归一，保证和为 1 */
    private double[] sanitizeLongOnly(double[] w, int n) {
        double sum = 0;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = Math.max(w[i], 0);
            sum += out[i];
        }
        if (sum <= 1e-12) return equalWeight(n);
        for (int i = 0; i < n; i++) out[i] /= sum;
        return out;
    }

    /** 协方差收缩，加对角项提升数值稳定性（Ledoit-Wolf 简化：向对角阵收缩极小量） */
    private double[][] shrink(double[][] cov) {
        int n = cov.length;
        double[][] out = new double[n][n];
        double avgVar = 0;
        for (int i = 0; i < n; i++) avgVar += cov[i][i];
        avgVar = n > 0 ? avgVar / n : 0;
        double epsilon = Math.max(avgVar, 1e-10) * 1e-6;
        for (int i = 0; i < n; i++) {
            System.arraycopy(cov[i], 0, out[i], 0, n);
            out[i][i] += epsilon;
        }
        return out;
    }

    private double[] normalizeSum(double[] v) {
        double sum = 0;
        for (double x : v) sum += x;
        if (Math.abs(sum) < 1e-12) {
            return equalWeight(v.length);
        }
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] / sum;
        return out;
    }

    private double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private double[] matVec(double[][] m, double[] v) {
        int n = m.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0;
            for (int j = 0; j < v.length; j++) s += m[i][j] * v[j];
            out[i] = s;
        }
        return out;
    }

    /** 高斯-约当求逆，奇异返回 null */
    private double[][] invert(double[][] matrix) {
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

            double pivotVal = a[col][col];
            for (int j = 0; j < 2 * n; j++) a[col][j] /= pivotVal;

            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double factor = a[r][col];
                if (factor == 0) continue;
                for (int j = 0; j < 2 * n; j++) {
                    a[r][j] -= factor * a[col][j];
                }
            }
        }
        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], n, inv[i], 0, n);
        }
        return inv;
    }
}
