package io.leavesfly.alphaforge.domain.service.portfolio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("组合优化器 PortfolioOptimizer")
class PortfolioOptimizerTest {

    private final PortfolioOptimizer optimizer = new PortfolioOptimizer();
    private final List<String> symbols = List.of("A", "B", "C");

    /** 构造 3 资产、不同波动/相关性的收益率序列 */
    private double[][] sampleReturns() {
        // A: 低波动稳定；B: 中波动；C: 高波动
        double[][] r = new double[3][60];
        java.util.Random rnd = new java.util.Random(42);
        for (int t = 0; t < 60; t++) {
            r[0][t] = 0.0005 + 0.005 * rnd.nextGaussian();
            r[1][t] = 0.0008 + 0.015 * rnd.nextGaussian();
            r[2][t] = 0.0010 + 0.030 * rnd.nextGaussian();
        }
        return r;
    }

    private double sum(double[] w) {
        double s = 0;
        for (double x : w) s += x;
        return s;
    }

    @Test
    @DisplayName("所有目标：权重非负且和为1")
    void weightsAreValidSimplex() {
        double[][] r = sampleReturns();
        for (OptimizationObjective obj : OptimizationObjective.values()) {
            PortfolioOptimizationResult res = optimizer.optimize(symbols, r, obj, 3.0, 0.02);
            assertEquals(1.0, sum(res.weights()), 1e-6, "权重和应为1: " + obj);
            for (double w : res.weights()) {
                assertTrue(w >= -1e-9, "权重应非负: " + obj);
            }
        }
    }

    @Test
    @DisplayName("等权：每个资产权重相等")
    void equalWeight() {
        PortfolioOptimizationResult res = optimizer.optimize(symbols, sampleReturns(),
                OptimizationObjective.EQUAL_WEIGHT, 3.0, 0.02);
        for (double w : res.weights()) {
            assertEquals(1.0 / 3, w, 1e-9);
        }
    }

    @Test
    @DisplayName("逆波动率：低波动资产权重更高")
    void inverseVolatilityFavorsLowVol() {
        PortfolioOptimizationResult res = optimizer.optimize(symbols, sampleReturns(),
                OptimizationObjective.INVERSE_VOLATILITY, 3.0, 0.02);
        double[] w = res.weights();
        assertTrue(w[0] > w[1], "A(低波动) 应重于 B");
        assertTrue(w[1] > w[2], "B 应重于 C(高波动)");
    }

    @Test
    @DisplayName("最小方差：波动应不高于等权组合")
    void minVarianceReducesVolatility() {
        double[][] r = sampleReturns();
        double minVarVol = optimizer.optimize(symbols, r,
                OptimizationObjective.MIN_VARIANCE, 3.0, 0.02).expectedVolatility();
        double equalVol = optimizer.optimize(symbols, r,
                OptimizationObjective.EQUAL_WEIGHT, 3.0, 0.02).expectedVolatility();
        assertTrue(minVarVol <= equalVol + 1e-9, "最小方差组合波动应不高于等权");
    }

    @Test
    @DisplayName("风险平价：各资产风险贡献近似相等")
    void riskParityEqualizesRiskContribution() {
        PortfolioOptimizationResult res = optimizer.optimize(symbols, sampleReturns(),
                OptimizationObjective.RISK_PARITY, 3.0, 0.02);
        double[] rc = res.riskContributions();
        for (double c : rc) {
            assertEquals(1.0 / 3, c, 0.03, "风险贡献应接近 1/3");
        }
    }

    @Test
    @DisplayName("空输入：安全返回")
    void emptyInput() {
        PortfolioOptimizationResult res = optimizer.optimize(List.of(), new double[0][0],
                OptimizationObjective.MAX_SHARPE, 3.0, 0.02);
        assertEquals(0, res.weights().length);
    }
}
