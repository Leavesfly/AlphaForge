package io.leavesfly.alphaforge.domain.service.decision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("势灯计算器测试 — 趋势分/均线结构/大盘 risk-off 封顶")
class TrendLightCalculatorTest {

    @Test
    @DisplayName("单调上升序列：趋势分达标且站上均线，势灯绿")
    void uptrendIsGreen() {
        var history = DecisionTestBars.series(300, 100, 0.01);

        TrendLightCalculator.Assessment assessment = TrendLightCalculator.calculate(history, null);

        assertEquals(LightColor.GREEN, assessment.light().getColor());
        assertTrue(assessment.trendScore() >= TrendLightCalculator.TREND_GREEN,
                "趋势分应达标，实际 " + assessment.trendScore());
        assertEquals(Boolean.FALSE, assessment.light().getDetail().get("belowMa200"));
    }

    @Test
    @DisplayName("长期下跌序列：收盘低于 MA200，势灯红")
    void downtrendBelowMa200IsRed() {
        var history = DecisionTestBars.series(300, 100, -0.01);

        TrendLightCalculator.Assessment assessment = TrendLightCalculator.calculate(history, null);

        assertEquals(LightColor.RED, assessment.light().getColor());
        assertEquals(Boolean.TRUE, assessment.light().getDetail().get("belowMa200"));
    }

    @Test
    @DisplayName("大盘 risk-off（基准收盘低于其 MA200）时势灯封顶黄")
    void benchmarkRiskOffCapsGreenToYellow() {
        var history = DecisionTestBars.series(300, 100, 0.01);
        var weakBenchmark = DecisionTestBars.series(300, 5000, -0.005);

        TrendLightCalculator.Assessment assessment = TrendLightCalculator.calculate(history, weakBenchmark);

        assertEquals(LightColor.YELLOW, assessment.light().getColor());
        assertEquals(Boolean.TRUE, assessment.light().getDetail().get("benchRiskOff"));
        assertTrue(assessment.light().getReasons().stream().anyMatch(r -> r.contains("risk-off")));
    }

    @Test
    @DisplayName("基准样本不足 200 根时跳过大盘检查（benchRiskOff=null）")
    void shortBenchmarkSkipsCheck() {
        var history = DecisionTestBars.series(300, 100, 0.01);
        var shortBenchmark = DecisionTestBars.series(100, 5000, 0.001);

        TrendLightCalculator.Assessment assessment = TrendLightCalculator.calculate(history, shortBenchmark);

        assertEquals(LightColor.GREEN, assessment.light().getColor());
        assertEquals(null, assessment.light().getDetail().get("benchRiskOff"));
    }

    @Test
    @DisplayName("无基准时相对强度权重并入动量，趋势分仍在 0~100")
    void noBenchmarkDegradesGracefully() {
        var history = DecisionTestBars.series(260, 100, 0.002);

        double score = TrendLightCalculator.trendScore(history, null);

        assertTrue(score >= 0 && score <= 100, "趋势分应在 0~100，实际 " + score);
    }
}
