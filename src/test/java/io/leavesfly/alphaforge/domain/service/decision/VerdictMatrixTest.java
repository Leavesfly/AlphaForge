package io.leavesfly.alphaforge.domain.service.decision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("决策矩阵测试 — 七态全分支与触发条件")
class VerdictMatrixTest {

    private static LightResult light(LightColor color) {
        return new LightResult(color);
    }

    private static LightResult valueLight(LightColor color, boolean hardFlaw) {
        LightResult light = light(color);
        light.putDetail("hardFlaw", hardFlaw);
        return light;
    }

    @Test
    @DisplayName("价灯硬伤一票否决 → 回避")
    void hardFlawVetoAvoid() {
        VerdictMatrix.Decision decision = VerdictMatrix.decide(
                valueLight(LightColor.RED, true), light(LightColor.GREEN), light(LightColor.GREEN),
                80.0, Map.of());
        assertEquals(Verdict.AVOID, decision.verdict());
        assertTrue(decision.rule().contains("硬伤"));
    }

    @Test
    @DisplayName("势绿+时绿+价红 → 纯趋势仓")
    void greenTrendRedValueIsTrendOnly() {
        VerdictMatrix.Decision decision = VerdictMatrix.decide(
                valueLight(LightColor.RED, false), light(LightColor.GREEN), light(LightColor.GREEN),
                80.0, Map.of());
        assertEquals(Verdict.TREND_ONLY, decision.verdict());
    }

    @Test
    @DisplayName("势绿+时绿+价非红 → 趋势买点（价灰时标注仅代表势/时）")
    void greenTrendGoodTimingIsTrendEntry() {
        VerdictMatrix.Decision withGrayValue = VerdictMatrix.decide(
                valueLight(LightColor.GRAY, false), light(LightColor.GREEN), light(LightColor.GREEN),
                80.0, Map.of());
        assertEquals(Verdict.TREND_ENTRY, withGrayValue.verdict());
        assertTrue(withGrayValue.rule().contains("仅代表势/时"));

        VerdictMatrix.Decision withGreenValue = VerdictMatrix.decide(
                valueLight(LightColor.GREEN, false), light(LightColor.GREEN), light(LightColor.GREEN),
                80.0, Map.of());
        assertEquals(Verdict.TREND_ENTRY, withGreenValue.verdict());
    }

    @Test
    @DisplayName("势绿+时非绿 → 等回踩（触发条件含收复 MA20）")
    void greenTrendBadTimingWaitsPullback() {
        LightResult timing = light(LightColor.YELLOW);
        timing.putDetail("belowMa20", true);
        VerdictMatrix.Decision decision = VerdictMatrix.decide(
                valueLight(LightColor.GRAY, false), light(LightColor.GREEN), timing,
                80.0, Map.of("ma20", 98.5));
        assertEquals(Verdict.WAIT_PULLBACK, decision.verdict());
        assertTrue(decision.triggers().stream().anyMatch(t -> t.contains("收复 MA20（98.50）")));
    }

    @Test
    @DisplayName("价绿+势弱 → 左侧观察（触发条件含趋势修复信号）")
    void greenValueWeakTrendIsLeftWatch() {
        LightResult trend = light(LightColor.YELLOW);
        trend.putDetail("belowMa60", true);
        trend.putDetail("weeklyBroken", true);
        VerdictMatrix.Decision decision = VerdictMatrix.decide(
                valueLight(LightColor.GREEN, false), trend, light(LightColor.YELLOW),
                50.0, Map.of());
        assertEquals(Verdict.LEFT_WATCH, decision.verdict());
        assertTrue(decision.triggers().stream().anyMatch(t -> t.contains("站回 MA60")));
        assertTrue(decision.triggers().stream().anyMatch(t -> t.contains("周线 MA30")));
        assertTrue(decision.triggers().stream().anyMatch(t -> t.contains("趋势分回升至 60")));
    }

    @Test
    @DisplayName("势弱+价无吸引力 → 回避")
    void weakTrendNoValueIsAvoid() {
        VerdictMatrix.Decision decision = VerdictMatrix.decide(
                valueLight(LightColor.YELLOW, false), light(LightColor.RED), light(LightColor.GREEN),
                30.0, Map.of());
        assertEquals(Verdict.AVOID, decision.verdict());
    }

    @Test
    @DisplayName("左侧分批计划：价深绿无硬伤才给，否则 null")
    void leftPlanOnlyForDeepGreen() {
        LightResult deepGreen = valueLight(LightColor.GREEN, false);
        deepGreen.putDetail("valuationPercentile", 0.2);
        deepGreen.putDetail("profitToLoss", false);
        VerdictMatrix.Decision watchDecision =
                new VerdictMatrix.Decision(Verdict.LEFT_WATCH, "rule", java.util.List.of("站回 MA60"));
        assertNotNull(VerdictMatrix.buildLeftPlan(deepGreen, watchDecision));

        LightResult notDeep = valueLight(LightColor.GREEN, false);
        notDeep.putDetail("valuationPercentile", 0.3);
        notDeep.putDetail("profitToLoss", false);
        assertNull(VerdictMatrix.buildLeftPlan(notDeep, watchDecision));

        LightResult flawed = valueLight(LightColor.RED, true);
        flawed.putDetail("valuationPercentile", 0.1);
        flawed.putDetail("profitToLoss", false);
        assertNull(VerdictMatrix.buildLeftPlan(flawed, watchDecision));
    }
}
