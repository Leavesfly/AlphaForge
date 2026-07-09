package io.leavesfly.alphaforge.application.backtest;

import io.leavesfly.alphaforge.application.strategy.condition.BacktestConditionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PointInTimeFundamentals 点时基本面")
class PointInTimeFundamentalsTest {

    private final BacktestConditionEvaluator evaluator = new BacktestConditionEvaluator();

    @Test
    @DisplayName("应按披露滞后取当时可知的最新财报")
    void shouldUseLatestAvailableReport() {
        List<Map<String, Object>> rows = List.of(
                Map.of("report_date", "2023-12-31",
                        "operate_income_yoy", 10.0, "roe_avg", 12.0,
                        "basic_eps_yoy", 8.0, "basic_eps", 2.0, "bps", 20.0),
                Map.of("report_date", "2024-03-31",
                        "operate_income_yoy", 25.0, "roe_avg", 18.0,
                        "basic_eps_yoy", 20.0, "basic_eps", 2.5, "bps", 22.0)
        );
        PointInTimeFundamentals pit = PointInTimeFundamentals.fromKeyIndicators(rows, 45);
        assertEquals(2, pit.size());

        // 2024-03-31 + 45 = 2024-05-15 才可用 Q1
        Map<String, Object> before = pit.asOf(LocalDate.of(2024, 5, 1), 50.0);
        assertEquals(true, before.get("fundamentals_available"));
        assertEquals(10.0, ((Number) before.get("actual_revenue_growth")).doubleValue(), 0.001);
        assertEquals(12.0, ((Number) before.get("actual_roe")).doubleValue(), 0.001);
        assertEquals(25.0, ((Number) before.get("actual_pe")).doubleValue(), 0.001); // 50/2

        Map<String, Object> after = pit.asOf(LocalDate.of(2024, 5, 20), 50.0);
        assertEquals(25.0, ((Number) after.get("actual_revenue_growth")).doubleValue(), 0.001);
        assertEquals(18.0, ((Number) after.get("actual_roe")).doubleValue(), 0.001);
        assertEquals(20.0, ((Number) after.get("actual_pe")).doubleValue(), 0.001); // 50/2.5
    }

    @Test
    @DisplayName("披露前应标记 fundamentals_available=false")
    void beforeFirstReportUnavailable() {
        List<Map<String, Object>> rows = List.of(
                Map.of("report_date", "2024-03-31",
                        "operate_income_yoy", 20.0, "roe_avg", 15.0,
                        "basic_eps", 1.0, "bps", 10.0)
        );
        PointInTimeFundamentals pit = PointInTimeFundamentals.fromKeyIndicators(rows, 45);
        Map<String, Object> early = pit.asOf(LocalDate.of(2024, 4, 1), 20.0);
        assertEquals(false, early.get("fundamentals_available"));
    }

    @Test
    @DisplayName("点时注入后 fundamental_filter 应按实际 ROE/增速过滤")
    void fundamentalFilterUsesInjectedValues() {
        Map<String, Object> pass = Map.of(
                "fundamentals_available", true,
                "actual_revenue_growth", 30.0,
                "actual_roe", 18.0
        );
        Map<String, Object> fail = Map.of(
                "fundamentals_available", true,
                "actual_revenue_growth", 5.0,
                "actual_roe", 8.0
        );
        Map<String, Object> condition = Map.of(
                "type", "fundamental_filter",
                "revenue_growth_min", 20,
                "roe_min", 15
        );
        assertTrue(evaluator.evaluate(condition, List.of(), 0, pass, false, 0, -1));
        assertFalse(evaluator.evaluate(condition, List.of(), 0, fail, false, 0, -1));
        assertFalse(evaluator.evaluate(condition, List.of(), 0,
                Map.of("fundamentals_available", false), false, 0, -1));
    }

    @Test
    @DisplayName("营收增速恶化应触发 fundamental_deterioration")
    void deteriorationFromYoYDecline() {
        List<Map<String, Object>> rows = List.of(
                Map.of("report_date", "2023-12-31",
                        "operate_income_yoy", 5.0, "roe_avg", 15.0,
                        "basic_eps_yoy", 3.0, "basic_eps", 1.0, "bps", 10.0),
                Map.of("report_date", "2024-03-31",
                        "operate_income_yoy", -10.0, "roe_avg", 10.0,
                        "basic_eps_yoy", -8.0, "basic_eps", 0.8, "bps", 10.0)
        );
        PointInTimeFundamentals pit = PointInTimeFundamentals.fromKeyIndicators(rows, 0);
        Map<String, Object> overlay = pit.asOf(LocalDate.of(2024, 4, 1), 20.0);
        assertEquals(true, overlay.get("revenue_decline"));
        assertTrue(evaluator.evaluate(
                Map.of("type", "fundamental_deterioration"),
                List.of(), 0, overlay, true, 20, 0));
    }

    @Test
    @DisplayName("needsFundamentals 应识别基本面策略")
    void needsFundamentalsDetectsStrategies() {
        var catalog = io.leavesfly.alphaforge.application.strategy.StrategyTestData.loadCatalog();
        assertTrue(FundamentalSnapshotLoader.needsFundamentals(
                catalog.find("growth_quality").orElseThrow()));
        assertTrue(FundamentalSnapshotLoader.needsFundamentals(
                catalog.find("dual_low").orElseThrow()));
        assertFalse(FundamentalSnapshotLoader.needsFundamentals(
                catalog.find("ma_golden_cross").orElseThrow()));
    }
}
