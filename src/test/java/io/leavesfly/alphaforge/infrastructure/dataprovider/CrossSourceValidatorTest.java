package io.leavesfly.alphaforge.infrastructure.dataprovider;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrossSourceValidator 多源交叉校验")
class CrossSourceValidatorTest {

    private final CrossSourceValidator validator = new CrossSourceValidator();

    @Test
    @DisplayName("一致 OHLC 应通过")
    void matchingBarsShouldPass() {
        List<StockDailyData> primary = bars("600519",
                bar(LocalDate.of(2024, 1, 2), 10, 11, 9.5, 10.5),
                bar(LocalDate.of(2024, 1, 3), 10.5, 11.2, 10.2, 11.0));
        List<StockDailyData> secondary = bars("600519",
                bar(LocalDate.of(2024, 1, 2), 10.01, 11.01, 9.51, 10.51),
                bar(LocalDate.of(2024, 1, 3), 10.51, 11.21, 10.21, 11.01));

        CrossSourceValidator.CrossCheckResult result = validator.validate(
                primary, secondary, "600519", "efinance", "tushare");

        assertTrue(result.isPassed());
        assertEquals(2, result.getComparedDays());
        assertEquals(0, result.getMismatchDays());
    }

    @Test
    @DisplayName("close 偏差超阈值应记为可疑日")
    void closeMismatchShouldFailWhenRatioExceeded() {
        List<StockDailyData> primary = bars("600519",
                bar(LocalDate.of(2024, 1, 2), 10, 11, 9.5, 10.0),
                bar(LocalDate.of(2024, 1, 3), 10, 11, 9.5, 10.0));
        // 备源 close 偏差 2% > 0.5%
        List<StockDailyData> secondary = bars("600519",
                bar(LocalDate.of(2024, 1, 2), 10, 11, 9.5, 10.2),
                bar(LocalDate.of(2024, 1, 3), 10, 11, 9.5, 10.2));

        CrossSourceValidator.CrossCheckResult result = validator.validate(
                primary, secondary, "600519", "efinance", "tushare",
                0.005, 0.01, 20, 0.10);

        assertFalse(result.isPassed());
        assertEquals(2, result.getComparedDays());
        assertEquals(2, result.getMismatchDays());
        assertFalse(result.getIssues().isEmpty());
    }

    @Test
    @DisplayName("少量可疑日在 rejectRatio 内仍可通过")
    void smallMismatchRatioShouldPass() {
        List<StockDailyData> primary = new ArrayList<>();
        List<StockDailyData> secondary = new ArrayList<>();
        LocalDate start = LocalDate.of(2024, 1, 2);
        for (int i = 0; i < 10; i++) {
            LocalDate d = start.plusDays(i);
            primary.add(bar(d, 10, 11, 9, 10.0));
            // 仅第 1 天偏差大
            double close = (i == 0) ? 10.2 : 10.0;
            secondary.add(bar(d, 10, 11, 9, close));
        }

        CrossSourceValidator.CrossCheckResult result = validator.validate(
                primary, secondary, "600519", "efinance", "tushare",
                0.005, 0.01, 20, 0.15);

        assertTrue(result.isPassed());
        assertEquals(1, result.getMismatchDays());
    }

    @Test
    @DisplayName("备源为空应跳过且视为通过")
    void emptySecondaryShouldSkip() {
        List<StockDailyData> primary = bars("600519",
                bar(LocalDate.of(2024, 1, 2), 10, 11, 9.5, 10.5));

        CrossSourceValidator.CrossCheckResult result = validator.validate(
                primary, List.of(), "600519", "efinance", "tushare");

        assertTrue(result.isPassed());
        assertEquals(0, result.getComparedDays());
    }

    @Test
    @DisplayName("sampleTail 应取尾部 N 条")
    void sampleTailShouldTakeLastN() {
        List<StockDailyData> data = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            data.add(bar(LocalDate.of(2024, 1, i), 10, 11, 9, 10.0));
        }
        List<StockDailyData> sample = CrossSourceValidator.sampleTail(data, 2);
        assertEquals(2, sample.size());
        assertEquals(LocalDate.of(2024, 1, 4), sample.get(0).getTradeDate());
        assertEquals(LocalDate.of(2024, 1, 5), sample.get(1).getTradeDate());
    }

    @Test
    @DisplayName("relativeDiff 计算正确")
    void relativeDiffShouldBeCorrect() {
        assertEquals(0.0, CrossSourceValidator.relativeDiff(10, 10), 1e-9);
        assertEquals(0.2 / 10.2, CrossSourceValidator.relativeDiff(10, 10.2), 1e-9);
    }

    private static List<StockDailyData> bars(String code, StockDailyData... items) {
        List<StockDailyData> list = new ArrayList<>();
        for (StockDailyData item : items) {
            item.setStockCode(code);
            list.add(item);
        }
        return list;
    }

    private static StockDailyData bar(LocalDate date, double open, double high, double low, double close) {
        StockDailyData d = new StockDailyData();
        d.setTradeDate(date);
        d.setOpenPrice(open);
        d.setHighPrice(high);
        d.setLowPrice(low);
        d.setClosePrice(close);
        d.setVolume(1000L);
        return d;
    }
}
