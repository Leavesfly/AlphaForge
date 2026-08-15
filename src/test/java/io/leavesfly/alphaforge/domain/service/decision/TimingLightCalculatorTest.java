package io.leavesfly.alphaforge.domain.service.decision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("时灯计算器测试 — 过热/回调/RSI/量价背离/事件风险")
class TimingLightCalculatorTest {

    @Test
    @DisplayName("温和交替序列：无过热无事件，时灯绿")
    void calmSeriesIsGreen() {
        var history = DecisionTestBars.alternating(250, 100, 0.008, -0.004);

        TimingLightCalculator.Assessment assessment =
                TimingLightCalculator.calculate(history, 1.0, null, lastDate(history));

        assertEquals(LightColor.GREEN, assessment.light().getColor());
    }

    @Test
    @DisplayName("末根暴涨偏离 MA20 超 15%：过热追高红灯")
    void overheatIsRed() {
        var history = DecisionTestBars.series(250, 100, 0.0);
        history.set(249, DecisionTestBars.bar(lastDate(history), 130));

        TimingLightCalculator.Assessment assessment =
                TimingLightCalculator.calculate(history, 1.0, null, lastDate(history));

        assertEquals(LightColor.RED, assessment.light().getColor());
        assertEquals(Boolean.TRUE, assessment.light().getDetail().get("overheated"));
    }

    @Test
    @DisplayName("收盘跌破 MA20：回调进行中黄灯")
    void belowMa20IsYellow() {
        var history = DecisionTestBars.series(250, 100, 0.0);
        history.set(249, DecisionTestBars.bar(lastDate(history), 95));

        TimingLightCalculator.Assessment assessment =
                TimingLightCalculator.calculate(history, 1.0, null, lastDate(history));

        assertEquals(LightColor.YELLOW, assessment.light().getColor());
        assertEquals(Boolean.TRUE, assessment.light().getDetail().get("belowMa20"));
    }

    @Test
    @DisplayName("距 60 日高点回撤超 8%：短期结构未修复黄灯")
    void drawdown60IsYellow() {
        var history = DecisionTestBars.series(250, 100, 0.0);
        // 60 日窗口内但 MA20 窗口外的一根高点：dd60 ≈ -9.1%，belowMa20 不触发
        history.set(200, DecisionTestBars.bar(history.get(200).getTradeDate(), 110));

        TimingLightCalculator.Assessment assessment =
                TimingLightCalculator.calculate(history, 1.0, null, lastDate(history));

        assertEquals(LightColor.YELLOW, assessment.light().getColor());
        double dd60 = (Double) assessment.snapshot().get("dd60");
        assertTrue(dd60 <= -0.08, "dd60 应超 8% 回撤，实际 " + dd60);
    }

    @Test
    @DisplayName("连续上涨 RSI 过热：短期过热黄灯")
    void rsiHotIsYellow() {
        var history = DecisionTestBars.series(250, 100, 0.005);

        TimingLightCalculator.Assessment assessment =
                TimingLightCalculator.calculate(history, 1.0, null, lastDate(history));

        assertEquals(LightColor.YELLOW, assessment.light().getColor());
        assertEquals(Boolean.TRUE, assessment.light().getDetail().get("rsiHot"));
    }

    @Test
    @DisplayName("价创 20 日新高但量能萎缩：量价背离黄灯")
    void volumeDivergenceIsYellow() {
        var history = DecisionTestBars.series(250, 100, 0.0);
        LocalDate last = lastDate(history);
        history.set(249, DecisionTestBars.bar(last, 100, 3_000L));

        TimingLightCalculator.Assessment assessment =
                TimingLightCalculator.calculate(history, 1.0, null, last);

        assertEquals(LightColor.YELLOW, assessment.light().getColor());
        assertTrue(assessment.light().getReasons().stream().anyMatch(r -> r.contains("量价背离")));
    }

    @Test
    @DisplayName("事件风险：近 30 天 high 红灯 / medium 黄灯 / 窗口外忽略")
    void riskEventsClassified() {
        var history = DecisionTestBars.series(250, 100, 0.0);
        LocalDate asof = lastDate(history);

        TimingLightCalculator.Assessment high = TimingLightCalculator.calculate(history, 1.0,
                List.of(new ThreeLightsInput.RiskEvent(asof, "high", "解禁")), asof);
        assertEquals(LightColor.RED, high.light().getColor());
        assertEquals(1, high.triggeredEvents().size());

        TimingLightCalculator.Assessment medium = TimingLightCalculator.calculate(history, 1.0,
                List.of(new ThreeLightsInput.RiskEvent(asof.minusDays(3), "medium", "业绩预告")), asof);
        assertEquals(LightColor.YELLOW, medium.light().getColor());

        TimingLightCalculator.Assessment stale = TimingLightCalculator.calculate(history, 1.0,
                List.of(new ThreeLightsInput.RiskEvent(asof.minusDays(40), "high", "旧事件")), asof);
        assertEquals(LightColor.GREEN, stale.light().getColor());
        assertEquals(0, stale.triggeredEvents().size());
    }

    private LocalDate lastDate(List<io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData> history) {
        return history.get(history.size() - 1).getTradeDate();
    }
}
