package io.leavesfly.alphaforge.domain.service;

import io.leavesfly.alphaforge.domain.service.decision.DecisionTestBars;
import io.leavesfly.alphaforge.domain.service.decision.IndicatorMath;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RSI 口径统一测试 — 固化「同一核心算法 + 两种缺失值约定」的契约。
 *
 * <p>回测/策略链用 {@code rsiFromCloses}（数据不足降级为中性 50），决策三灯链用
 * {@code rsiOrNaN} 与 {@code IndicatorMath.rsi}（数据不足返回 NaN，调用方须守卫）。
 * 两种约定不可互换：把 NaN 折叠成 50 会让「无数据」被误判为「已评估且中性」。</p>
 */
@DisplayName("RSI 口径统一 — 核心算法唯一、缺失值约定分离")
class TechnicalIndicatorCalculatorRsiTest {

    private static double[] closes(double... v) {
        return v;
    }

    @Nested
    @DisplayName("缺失值约定")
    class MissingValueConvention {

        @Test
        @DisplayName("数据不足：回测口径降级为 50，决策口径返回 NaN")
        void insufficientData() {
            double[] tooShort = closes(10, 11, 12);

            assertEquals(50.0, TechnicalIndicatorCalculator.rsiFromCloses(tooShort, 14), 1e-9);
            assertTrue(Double.isNaN(TechnicalIndicatorCalculator.rsiOrNaN(tooShort, 14)),
                    "决策链依赖 NaN 表达『数据不足、不下结论』");
        }

        @Test
        @DisplayName("IndicatorMath.rsi 对外保持 NaN 约定")
        void indicatorMathKeepsNaN() {
            List<StockDailyData> tooShort = DecisionTestBars.series(10, 100, 0.01);

            assertTrue(Double.isNaN(IndicatorMath.rsi(tooShort, 14)));
        }
    }

    @Nested
    @DisplayName("边界形态")
    class EdgeShapes {

        @Test
        @DisplayName("完全无波动（停牌/一字板）：两个口径均为中性 50，而非超买 100")
        void flatSeriesIsNeutral() {
            double[] flat = new double[30];
            java.util.Arrays.fill(flat, 12.34);

            assertEquals(50.0, TechnicalIndicatorCalculator.rsiOrNaN(flat, 14), 1e-9);
            assertEquals(50.0, TechnicalIndicatorCalculator.rsiFromCloses(flat, 14), 1e-9);
        }

        @Test
        @DisplayName("区间内只涨不跌：RSI 为 100")
        void onlyGainsIs100() {
            double[] rising = new double[30];
            for (int i = 0; i < rising.length; i++) {
                rising[i] = 10 + i;
            }

            assertEquals(100.0, TechnicalIndicatorCalculator.rsiOrNaN(rising, 14), 1e-9);
        }

        @Test
        @DisplayName("区间内只跌不涨：RSI 为 0")
        void onlyLossesIs0() {
            double[] falling = new double[30];
            for (int i = 0; i < falling.length; i++) {
                falling[i] = 100 - i;
            }

            assertEquals(0.0, TechnicalIndicatorCalculator.rsiOrNaN(falling, 14), 1e-9);
        }
    }

    @Nested
    @DisplayName("跨入口一致性")
    class CrossEntryConsistency {

        @Test
        @DisplayName("实例方法、静态回测口径、IndicatorMath 三入口在数据充足时结果一致")
        void allEntriesAgree() {
            List<StockDailyData> bars = DecisionTestBars.alternating(60, 100, 0.02, -0.011);
            double[] arr = toCloses(bars);

            double viaInstance = new TechnicalIndicatorCalculator().rsi(arr, 14);
            double viaStatic = TechnicalIndicatorCalculator.rsiFromCloses(arr, 14);
            double viaNaN = TechnicalIndicatorCalculator.rsiOrNaN(arr, 14);
            double viaIndicatorMath = IndicatorMath.rsi(bars, 14);

            assertEquals(viaStatic, viaInstance, 1e-9);
            assertEquals(viaStatic, viaNaN, 1e-9);
            assertEquals(viaStatic, viaIndicatorMath, 1e-9,
                    "决策链与回测链必须使用同一 RSI 数值口径");
        }

        @Test
        @DisplayName("上涨序列 RSI 高于下跌序列，且均落在 [0,100]")
        void directionalSanity() {
            double[] up = new double[40];
            double[] down = new double[40];
            for (int i = 0; i < 40; i++) {
                up[i] = 50 + i * 0.5;
                down[i] = 50 - i * 0.5;
            }

            double rsiUp = TechnicalIndicatorCalculator.rsiOrNaN(up, 14);
            double rsiDown = TechnicalIndicatorCalculator.rsiOrNaN(down, 14);

            assertTrue(rsiUp > rsiDown);
            assertTrue(rsiUp >= 0 && rsiUp <= 100);
            assertTrue(rsiDown >= 0 && rsiDown <= 100);
        }
    }

    private static double[] toCloses(List<StockDailyData> bars) {
        double[] arr = new double[bars.size()];
        for (int i = 0; i < bars.size(); i++) {
            arr[i] = bars.get(i).getClosePrice();
        }
        return arr;
    }
}
