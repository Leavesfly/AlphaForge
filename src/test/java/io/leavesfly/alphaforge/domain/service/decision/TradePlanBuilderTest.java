package io.leavesfly.alphaforge.domain.service.decision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("交易计划构建测试 — ATR 数学与风险预算法仓位")
class TradePlanBuilderTest {

    @Test
    @DisplayName("ATR 价位数学：止损 2×ATR / R / 2R 3R 止盈 / 追价上限 0.5×ATR")
    void atrPlanMath() {
        TradePlan plan = TradePlanBuilder.build(100.0, 98.0, 2.0);
        assertEquals(100.0, plan.getEntry(), 1e-9);
        assertEquals(98.0, plan.getPullbackRef(), 1e-9);
        assertEquals(96.0, plan.getStop(), 1e-9);
        assertEquals(4.0, plan.getR(), 1e-9);
        assertEquals(108.0, plan.getTarget2R(), 1e-9);
        assertEquals(112.0, plan.getTarget3R(), 1e-9);
        assertEquals(101.0, plan.getChaseLimit(), 1e-9);
        assertEquals(2.0, plan.getAtr(), 1e-9);
    }

    @Test
    @DisplayName("收盘/ATR 无效或 R 非正时返回 null（诚实降级）")
    void invalidInputReturnsNull() {
        assertNull(TradePlanBuilder.build(Double.NaN, 98.0, 2.0));
        assertNull(TradePlanBuilder.build(100.0, 98.0, Double.NaN));
        assertNull(TradePlanBuilder.build(100.0, 98.0, 0.0));
        assertNull(TradePlanBuilder.build(0.0, 98.0, 2.0));
    }

    @Test
    @DisplayName("风险预算法：1% 资金 ÷ R，按 lot 向下取整")
    void riskBudgetSizing() {
        TradePlan plan = TradePlanBuilder.build(100.0, 98.0, 2.0);
        TradePlanBuilder.attachSizing(plan, 100_000.0, 1.0, 100, 1.0);

        TradePlan.Sizing sizing = plan.getSizing();
        assertEquals(1000.0, sizing.getRiskAmount(), 1e-9);
        // 风险预算 1000/4=250 股，A 股 lot=100 → 200 股
        assertEquals(200, sizing.getSuggestedShares());
        assertEquals(20_000.0, sizing.getPositionValue(), 1e-9);
        assertEquals(0.2, sizing.getPositionPct(), 1e-9);
    }

    @Test
    @DisplayName("激进乘数 1.5：风险预算放大至 1.5%")
    void riskMultiplierScalesBudget() {
        TradePlan plan = TradePlanBuilder.build(100.0, 98.0, 2.0);
        TradePlanBuilder.attachSizing(plan, 100_000.0, 1.5, 100, 1.0);
        assertEquals(1500.0, plan.getSizing().getRiskAmount(), 1e-9);
        assertEquals(300, plan.getSizing().getSuggestedShares());
    }

    @Test
    @DisplayName("单票仓位上限：maxPositionPct 约束优先于风险预算")
    void maxPositionPctCapsSizing() {
        TradePlan plan = TradePlanBuilder.build(100.0, 98.0, 2.0);
        // 风险预算 250 股，但市值上限 10%（10000 元 / 100 元 = 100 股）
        TradePlanBuilder.attachSizing(plan, 100_000.0, 1.0, 100, 0.1);
        assertEquals(100, plan.getSizing().getSuggestedShares());
    }

    @Test
    @DisplayName("低波动大 R 标的：市值不超过资金约束生效")
    void marketValueCappedAtCapital() {
        // R=40（ATR=20）：风险预算 1000/40=25 股 → 不足一手 lot=100 → 0 股
        TradePlan plan = TradePlanBuilder.build(100.0, 98.0, 20.0);
        TradePlanBuilder.attachSizing(plan, 100_000.0, 1.0, 100, 1.0);
        assertEquals(0, plan.getSizing().getSuggestedShares());
    }

    @Test
    @DisplayName("资金缺失不附加仓位（不猜测）")
    void missingCapitalSkipsSizing() {
        TradePlan plan = TradePlanBuilder.build(100.0, 98.0, 2.0);
        TradePlanBuilder.attachSizing(plan, null, 1.0, 100, 1.0);
        assertNull(plan.getSizing());
    }
}
