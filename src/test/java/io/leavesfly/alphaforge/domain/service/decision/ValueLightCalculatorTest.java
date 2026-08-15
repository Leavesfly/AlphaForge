package io.leavesfly.alphaforge.domain.service.decision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("价灯计算器测试 — 硬伤否决/估值分位边界/灰灯降级")
class ValueLightCalculatorTest {

    @Test
    @DisplayName("ST 标的一票红灯（硬伤）")
    void stIsHardFlawRed() {
        LightResult light = ValueLightCalculator.calculate(true, 10.0, null, 0.2, null);
        assertEquals(LightColor.RED, light.getColor());
        assertEquals(Boolean.TRUE, light.getDetail().get("hardFlaw"));
    }

    @Test
    @DisplayName("每股净资产为负（资不抵债）一票红灯")
    void negativeNetAssetIsHardFlaw() {
        LightResult light = ValueLightCalculator.calculate(false, -0.5, null, 0.2, null);
        assertEquals(LightColor.RED, light.getColor());
        assertEquals(Boolean.TRUE, light.getDetail().get("hardFlaw"));
    }

    @Test
    @DisplayName("最近 4 季 EPS 均为负（连续亏损）一票红灯")
    void fourQuarterLossIsHardFlaw() {
        LightResult light = ValueLightCalculator.calculate(false, 5.0, List.of(-0.1, -0.2, -0.1, -0.3), 0.5, null);
        assertEquals(LightColor.RED, light.getColor());
        assertEquals(Boolean.TRUE, light.getDetail().get("hardFlaw"));
    }

    @Test
    @DisplayName("由盈转亏不算硬伤但价灯封顶黄")
    void profitToLossCapsYellow() {
        LightResult light = ValueLightCalculator.calculate(false, 5.0, List.of(0.1, 0.2, 0.1, -0.1), 0.2, null);
        assertEquals(LightColor.YELLOW, light.getColor());
        assertEquals(Boolean.FALSE, light.getDetail().get("hardFlaw"));
        assertEquals(Boolean.TRUE, light.getDetail().get("profitToLoss"));
    }

    @Test
    @DisplayName("估值分位边界：≤0.4 绿 / (0.4,0.7] 黄 / >0.7 红")
    void valuationBoundaries() {
        assertEquals(LightColor.GREEN, ValueLightCalculator
                .calculate(false, 5.0, null, 0.4, null).getColor());
        assertEquals(LightColor.YELLOW, ValueLightCalculator
                .calculate(false, 5.0, null, 0.41, null).getColor());
        assertEquals(LightColor.YELLOW, ValueLightCalculator
                .calculate(false, 5.0, null, 0.7, null).getColor());
        assertEquals(LightColor.RED, ValueLightCalculator
                .calculate(false, 5.0, null, 0.71, null).getColor());
    }

    @Test
    @DisplayName("深绿：分位 ≤0.25 且无硬伤时 detail.deepGreen=true")
    void deepGreenFlag() {
        LightResult deep = ValueLightCalculator.calculate(false, 5.0, null, 0.25, null);
        assertEquals(Boolean.TRUE, deep.getDetail().get("deepGreen"));
        LightResult notDeep = ValueLightCalculator.calculate(false, 5.0, null, 0.3, null);
        assertEquals(Boolean.FALSE, notDeep.getDetail().get("deepGreen"));
    }

    @Test
    @DisplayName("无估值数据时灰灯降级并明示仅基于势/时")
    void missingValuationIsGray() {
        LightResult light = ValueLightCalculator.calculate(null, null, null, null, null);
        assertEquals(LightColor.GRAY, light.getColor());
        assertFalse(light.getReasons().isEmpty());
        assertTrue(light.getReasons().stream().anyMatch(r -> r.contains("仅基于势/时")));
    }

    @Test
    @DisplayName("EPS 序列不足 4 个时跳过连续亏损检查")
    void shortEpsSkipsLossCheck() {
        LightResult light = ValueLightCalculator.calculate(false, 5.0, List.of(-0.1, -0.2), 0.5, null);
        assertEquals(LightColor.YELLOW, light.getColor());
        assertEquals(Boolean.FALSE, light.getDetail().get("hardFlaw"));
    }
}
