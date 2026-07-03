package io.leavesfly.alphaforge.domain.service.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("统一绩效分析器 PerformanceAnalytics")
class PerformanceAnalyticsTest {

    private final PerformanceAnalytics perf = new PerformanceAnalytics();

    @Test
    @DisplayName("累计收益率：复利计算正确")
    void cumulativeReturn() {
        // (1+0.1)*(1+0.1)-1 = 0.21
        assertEquals(0.21, perf.cumulativeReturn(List.of(0.1, 0.1)), 1e-9);
    }

    @Test
    @DisplayName("最大回撤：识别峰谷跌幅")
    void maxDrawdown() {
        // 净值: 1 -> 1.2 -> 0.96 => 回撤 (1.2-0.96)/1.2 = 0.2
        double md = perf.maxDrawdown(List.of(0.2, -0.2));
        assertEquals(0.2, md, 1e-9);
    }

    @Test
    @DisplayName("胜率：正收益日占比")
    void winRate() {
        assertEquals(0.6, perf.winRate(List.of(0.01, -0.01, 0.02, 0.03, -0.02)), 1e-9);
    }

    @Test
    @DisplayName("强正漂移序列：夏普为正")
    void positiveSeries() {
        List<Double> r = new ArrayList<>();
        java.util.Random rnd = new java.util.Random(1);
        for (int i = 0; i < 200; i++) r.add(0.002 + 0.001 * rnd.nextGaussian());
        PerformanceMetrics m = perf.analyze(r);
        assertTrue(m.sharpeRatio() > 0, "强正漂移应给出正夏普");
        assertEquals(200, m.periods());
    }

    @Test
    @DisplayName("相对基准：完全相同则 beta≈1、alpha≈0")
    void betaAgainstIdenticalBenchmark() {
        List<Double> r = new ArrayList<>();
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < 120; i++) r.add(0.0005 + 0.01 * rnd.nextGaussian());
        PerformanceMetrics m = perf.analyze(r, r);
        assertEquals(1.0, m.beta(), 1e-6);
        assertEquals(0.0, m.alpha(), 1e-6);
    }

    @Test
    @DisplayName("空输入：安全返回全零")
    void emptyInput() {
        PerformanceMetrics m = perf.analyze(List.of());
        assertEquals(0, m.periods());
        assertEquals(0, m.sharpeRatio());
    }
}
