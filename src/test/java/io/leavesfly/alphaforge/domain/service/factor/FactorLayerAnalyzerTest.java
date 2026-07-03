package io.leavesfly.alphaforge.domain.service.factor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("因子分层分析器 FactorLayerAnalyzer")
class FactorLayerAnalyzerTest {

    private final FactorLayerAnalyzer analyzer = new FactorLayerAnalyzer();

    /** 构造因子值与前瞻收益强正相关的横截面 */
    private List<List<double[]>> monotonicPeriods(int periods, int stocks, double noise) {
        List<List<double[]>> result = new ArrayList<>();
        java.util.Random rnd = new java.util.Random(11);
        for (int p = 0; p < periods; p++) {
            List<double[]> cs = new ArrayList<>();
            for (int s = 0; s < stocks; s++) {
                double factor = s;                        // 因子值随 s 递增
                double ret = 0.001 * s + noise * rnd.nextGaussian(); // 收益随因子递增
                cs.add(new double[]{factor, ret});
            }
            result.add(cs);
        }
        return result;
    }

    @Test
    @DisplayName("正向因子：IC>0、单调性≈1、多空收益>0")
    void positiveFactor() {
        FactorLayerResult r = analyzer.analyze(monotonicPeriods(30, 50, 0.0005), 5);
        assertTrue(r.icMean() > 0, "IC 应为正");
        assertTrue(r.monotonicity() > 0.8, "分层应近似单调");
        assertTrue(r.longShortReturn() > 0, "多空收益应为正");
        assertEquals(5, r.quantiles());
    }

    @Test
    @DisplayName("最高层收益应高于最低层")
    void topLayerBeatsBottom() {
        FactorLayerResult r = analyzer.analyze(monotonicPeriods(30, 50, 0.0005), 5);
        double[] layers = r.layerMeanReturns();
        assertTrue(layers[4] > layers[0], "最高层收益应高于最低层");
    }

    @Test
    @DisplayName("样本不足：安全返回")
    void insufficientData() {
        FactorLayerResult r = analyzer.analyze(List.of(), 5);
        assertEquals(0, r.periods());
        assertEquals(0, r.icMean(), 1e-9);
    }
}
