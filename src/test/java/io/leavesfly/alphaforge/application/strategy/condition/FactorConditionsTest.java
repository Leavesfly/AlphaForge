package io.leavesfly.alphaforge.application.strategy.condition;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FactorConditions / 经典因子条件")
class FactorConditionsTest {

    private final BacktestConditionEvaluator evaluator = new BacktestConditionEvaluator();

    @Test
    @DisplayName("reversal_5：近 5 日下跌后因子值应偏高并命中 min 阈值")
    void reversal5ShouldMatchAfterDrop() {
        List<StockDailyData> data = new ArrayList<>();
        // 前段平稳
        for (int i = 0; i < 10; i++) {
            data.add(bar(100, 100));
        }
        // 近 5 日连续下跌约 10%
        double price = 100;
        for (int i = 0; i < 5; i++) {
            price *= 0.98;
            data.add(bar(price / 0.98, price));
        }
        int end = data.size() - 1;
        double reversal = FactorConditions.compute("reversal_5", data, end);
        assertFalse(Double.isNaN(reversal));
        assertTrue(reversal > 0.05, "reversal_5=" + reversal);

        assertTrue(evaluator.evaluate(
                Map.of("type", "factor", "name", "reversal_5", "min", 0.05),
                data, end, Map.of(), false, 0, -1));
        assertFalse(evaluator.evaluate(
                Map.of("type", "factor", "name", "reversal_5", "min", 0.5),
                data, end, Map.of(), false, 0, -1));
    }

    @Test
    @DisplayName("momentum_20：上涨趋势应命中 min 阈值")
    void momentum20ShouldMatchUptrend() {
        List<StockDailyData> data = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            data.add(bar(100 + i, 100 + i + 0.5));
        }
        int end = data.size() - 1;
        double mom = FactorConditions.compute("momentum_20", data, end);
        assertTrue(mom > 0.03, "momentum_20=" + mom);
        assertTrue(FactorConditions.matches("momentum_20", data, end, 0.03, null));
        assertTrue(evaluator.evaluate(
                Map.of("type", "factor", "name", "momentum_20", "min", 0.03),
                data, end, Map.of(), false, 0, -1));
    }

    @Test
    @DisplayName("不支持的因子名应返回 false")
    void unsupportedFactorShouldFail() {
        List<StockDailyData> data = List.of(bar(10, 11));
        assertFalse(evaluator.evaluate(
                Map.of("type", "factor", "name", "not_a_factor", "min", 0),
                data, 0, Map.of(), false, 0, -1));
    }

    @Test
    @DisplayName("warmupDays 应对齐因子窗口")
    void warmupDaysShouldMatchWindows() {
        assertEquals(5, FactorConditions.warmupDays("reversal_5"));
        assertEquals(20, FactorConditions.warmupDays("momentum_20"));
        assertEquals(60, FactorConditions.warmupDays("momentum_60"));
        assertEquals(14, FactorConditions.warmupDays("rsi_14"));
    }

    private StockDailyData bar(double open, double close) {
        StockDailyData d = new StockDailyData();
        d.setStockCode("000001");
        d.setTradeDate(LocalDate.of(2024, 1, 1));
        d.setOpenPrice(open);
        d.setHighPrice(Math.max(open, close) + 0.5);
        d.setLowPrice(Math.min(open, close) - 0.5);
        d.setClosePrice(close);
        d.setVolume(1_000_000L);
        d.setChangePct(open > 0 ? (close - open) / open * 100 : 0);
        return d;
    }
}
