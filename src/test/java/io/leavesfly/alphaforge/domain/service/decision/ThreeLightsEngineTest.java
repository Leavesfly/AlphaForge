package io.leavesfly.alphaforge.domain.service.decision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("三灯引擎测试 — MIN_BARS 拦截/完整编排/持仓联动/左侧计划")
class ThreeLightsEngineTest {

    @Test
    @DisplayName("K 线不足 MIN_BARS：unrated + 三灰灯 + 补数据触发条件")
    void insufficientBarsIsUnrated() {
        var history = DecisionTestBars.series(200, 100, 0.01);

        LightsResult result = ThreeLightsEngine.evaluate(ThreeLightsInput.builder("600519")
                .history(history).build());

        assertEquals(Verdict.UNRATED, result.getVerdict());
        assertEquals(200, result.getNBars());
        assertNull(result.getTrendScore());
        assertEquals(LightColor.GRAY, result.getLights().get("value").getColor());
        assertEquals(LightColor.GRAY, result.getLights().get("trend").getColor());
        assertEquals(LightColor.GRAY, result.getLights().get("timing").getColor());
        assertTrue(result.getDecision().rule().contains("数据不足"));
        assertTrue(result.getDecision().triggers().get(0).contains("补足历史"));
    }

    @Test
    @DisplayName("完整编排：温和上升趋势 → 趋势买点 + ATR 交易计划 + 建议仓位")
    void happyPathProducesTrendEntryWithPlan() {
        var history = DecisionTestBars.alternating(300, 100, 0.008, -0.004);

        LightsResult result = ThreeLightsEngine.evaluate(ThreeLightsInput.builder("600519")
                .stockName("测试股")
                .history(history)
                .capitalYuan(100_000.0)
                .riskMultiplier(1.0)
                .lotSize(100)
                .build());

        assertEquals(Verdict.TREND_ENTRY, result.getVerdict(),
                "实际结论 " + result.getVerdict() + " / " + result.lightsSummary());
        assertEquals("价灰+势绿+时绿", result.lightsSummary());
        assertNotNull(result.getPlan());
        assertTrue(result.getPlan().getSizing().getSuggestedShares() > 0);
        assertEquals(300, result.getNBars());
        assertNotNull(result.getAsof());
        // 证据链含势灯趋势分与 MA 结构条目
        assertTrue(result.getEvidence().stream().anyMatch(e -> "trend_score".equals(e.indicator())));
    }

    @Test
    @DisplayName("持仓联动：势红 + 持仓成本 → 结论切换为持仓需减风险")
    void positionOverlayWithRedTrendBecomesReduceRisk() {
        var history = DecisionTestBars.series(300, 100, -0.01);

        LightsResult result = ThreeLightsEngine.evaluate(ThreeLightsInput.builder("600519")
                .history(history)
                .positionCost(150.0)
                .positionShares(1000.0)
                .build());

        assertEquals(Verdict.REDUCE_RISK, result.getVerdict());
        assertNotNull(result.getPosition());
        assertEquals(150.0, (Double) result.getPosition().get("cost"), 1e-9);
        assertNotNull(result.getPosition().get("pnlPct"));
        assertTrue(String.valueOf(result.getPosition().get("advice")).contains("减仓"));
    }

    @Test
    @DisplayName("持仓联动：势绿持仓态给出持有建议（不改灯色）")
    void positionOverlayWithGreenTrendKeepsVerdict() {
        var history = DecisionTestBars.alternating(300, 100, 0.008, -0.004);

        LightsResult result = ThreeLightsEngine.evaluate(ThreeLightsInput.builder("600519")
                .history(history)
                .positionCost(80.0)
                .build());

        assertEquals(Verdict.TREND_ENTRY, result.getVerdict());
        assertNotNull(result.getPosition());
        assertTrue(String.valueOf(result.getPosition().get("advice")).contains("继续持有"));
    }

    @Test
    @DisplayName("左侧观察：价深绿 + 势弱 → leftPlan 给出分批纪律")
    void leftWatchWithDeepGreenGetsLeftPlan() {
        var history = DecisionTestBars.series(300, 100, -0.005);

        LightsResult result = ThreeLightsEngine.evaluate(ThreeLightsInput.builder("600519")
                .history(history)
                .valuationPercentile(0.2)
                .build());

        assertEquals(Verdict.LEFT_WATCH, result.getVerdict(),
                "实际结论 " + result.getVerdict() + " / " + result.lightsSummary());
        assertNotNull(result.getLeftPlan());
        assertEquals("dca", result.getLeftPlan().get("suggestedAction"));
        assertNull(result.getPlan());
    }

    @Test
    @DisplayName("toMap 序列化：结论/灯/计划/证据结构完整")
    void toMapStructureComplete() {
        var history = DecisionTestBars.alternating(300, 100, 0.008, -0.004);

        Map<String, Object> map = ThreeLightsEngine.evaluate(ThreeLightsInput.builder("600519")
                .stockName("测试股")
                .history(history)
                .marketContext(Map.of("market", "A"))
                .build()).toMap();

        assertEquals("600519", map.get("stockCode"));
        assertEquals("trend_entry", map.get("verdict"));
        assertEquals("趋势买点", map.get("verdictCn"));
        assertEquals("价灰+势绿+时绿", map.get("lightsSummary"));
        assertNotNull(map.get("lights"));
        assertNotNull(map.get("decision"));
        assertNotNull(map.get("evidence"));
        assertEquals(Map.of("market", "A"), map.get("marketContext"));
    }
}
