package io.leavesfly.stock.infrastructure.dataprovider;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import io.leavesfly.stock.domain.service.TradingCalendar;
import io.leavesfly.stock.infrastructure.dataprovider.impl.EFinanceFetcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * DataQualityValidator 真实数据验证测试
 *
 * 使用真实K线数据验证数据质量校验器的各项检查能力：
 * 1. 正常数据应通过校验
 * 2. OHLC一致性检查
 * 3. 缺失交易日检测
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("DataQualityValidator 真实数据验证测试")
class DataQualityValidatorIntegrationTest {

    private DataQualityValidator validator;
    private EFinanceFetcher fetcher;
    private static final String STOCK_CODE = "600519";

    @BeforeAll
    void setUp() {
        AppConfig mockConfig = mock(AppConfig.class);
        TradingCalendar calendar = new TradingCalendar();
        validator = new DataQualityValidator(calendar);
        fetcher = new EFinanceFetcher(mockConfig);
    }

    @Test
    @DisplayName("真实K线数据应通过质量校验或仅有少量问题")
    void realDataShouldPassQualityCheck() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);

        List<StockDailyData> data = fetcher.getHistoryData(STOCK_CODE, start, end);
        if (data == null || data.isEmpty()) {
            System.out.println("⚠ 无法获取K线数据（可能网络问题），跳过");
            return;
        }

        DataQualityValidator.ValidationResult result = validator.validate(data, STOCK_CODE);

        System.out.println("=== 数据质量校验结果 ===");
        System.out.println("数据量: " + data.size() + "条");
        System.out.println("校验通过: " + result.isValid());
        System.out.println("发现问题: " + result.getIssues().size() + "个");
        System.out.println("缺失交易日: " + result.getMissingDates().size() + "个");

        if (!result.getIssues().isEmpty()) {
            System.out.println("\n问题明细（前5条）:");
            int limit = Math.min(5, result.getIssues().size());
            for (int i = 0; i < limit; i++) {
                System.out.println("  - " + result.getIssues().get(i));
            }
        }

        // 真实数据可能存在少量问题（如停牌日缺失），但不应有大量严重问题
        assertTrue(result.getIssues().size() < data.size() / 2,
                "问题数不应超过数据量的一半，实际: " + result.getIssues().size() + "/" + data.size());
    }

    @Nested
    @DisplayName("OHLC一致性校验")
    class OHLCConsistencyTest {

        @Test
        @DisplayName("真实数据的OHLC应满足 high >= max(open,close) >= low")
        void realDataOHLCShouldBeConsistent() {
            LocalDate end = LocalDate.now();
            List<StockDailyData> data = fetcher.getHistoryData(STOCK_CODE, end.minusDays(10), end);
            if (data == null || data.isEmpty()) return;

            int violations = 0;
            for (StockDailyData d : data) {
                if (d.getHighPrice() != null && d.getLowPrice() != null &&
                    d.getOpenPrice() != null && d.getClosePrice() != null) {
                    double maxOC = Math.max(d.getOpenPrice(), d.getClosePrice());
                    double minOC = Math.min(d.getOpenPrice(), d.getClosePrice());
                    if (d.getHighPrice() < maxOC || d.getLowPrice() > minOC) {
                        violations++;
                        System.out.printf("⚠ OHLC不一致: %s 高%.2f 低%.2f 开%.2f 收%.2f\n",
                                d.getTradeDate(), d.getHighPrice(), d.getLowPrice(),
                                d.getOpenPrice(), d.getClosePrice());
                    }
                }
            }

            System.out.println("=== OHLC一致性校验 ===");
            System.out.println("检查数据: " + data.size() + "条");
            System.out.println("不一致: " + violations + "条");
            assertEquals(0, violations, "真实数据OHLC应一致，但发现" + violations + "条不一致");
        }
    }

    @Nested
    @DisplayName("缺失交易日检测")
    class MissingDayTest {

        @Test
        @DisplayName("应检测出真实数据中的缺失交易日（如有）")
        void shouldDetectMissingDaysInRealData() {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(20);

            List<StockDailyData> data = fetcher.getHistoryData(STOCK_CODE, start, end);
            if (data == null || data.isEmpty()) return;

            DataQualityValidator.ValidationResult result = validator.validate(data, STOCK_CODE);

            System.out.println("=== 缺失交易日检测 ===");
            System.out.println("请求范围: " + start + " ~ " + end);
            System.out.println("实际数据: " + data.size() + "条");
            System.out.println("缺失交易日: " + result.getMissingDates().size() + "个");

            if (!result.getMissingDates().isEmpty()) {
                System.out.println("缺失日期:");
                for (LocalDate d : result.getMissingDates()) {
                    System.out.println("  - " + d + " (" + d.getDayOfWeek() + ")");
                }
            }

            // 缺失交易日可能因停牌或API未返回，数量应合理
            assertTrue(result.getMissingDates().size() <= 5,
                    "缺失交易日不应超过5个，实际: " + result.getMissingDates().size());
        }
    }

    @Nested
    @DisplayName("异常价格检测")
    class AbnormalPriceTest {

        @Test
        @DisplayName("真实数据不应有零价或负价")
        void realDataShouldNotHaveZeroPrice() {
            LocalDate end = LocalDate.now();
            List<StockDailyData> data = fetcher.getHistoryData(STOCK_CODE, end.minusDays(10), end);
            if (data == null || data.isEmpty()) return;

            for (StockDailyData d : data) {
                assertNotNull(d.getClosePrice(), d.getTradeDate() + " 收盘价不应为null");
                assertTrue(d.getClosePrice() > 0,
                        d.getTradeDate() + " 收盘价应大于0, 实际: " + d.getClosePrice());

                if (d.getOpenPrice() != null) {
                    assertTrue(d.getOpenPrice() > 0,
                            d.getTradeDate() + " 开盘价应大于0, 实际: " + d.getOpenPrice());
                }

                if (d.getVolume() != null) {
                    assertTrue(d.getVolume() >= 0,
                            d.getTradeDate() + " 成交量不应为负, 实际: " + d.getVolume());
                }
            }

            System.out.println("=== 异常价格检测 ===");
            System.out.println("检查数据: " + data.size() + "条");
            System.out.println("全部通过: 无零价/负价/负成交量");
        }

        @Test
        @DisplayName("真实数据涨跌幅应在合理范围内（A股±22%）")
        void realDataChangePctShouldBeReasonable() {
            LocalDate end = LocalDate.now();
            List<StockDailyData> data = fetcher.getHistoryData(STOCK_CODE, end.minusDays(30), end);
            if (data == null || data.size() < 2) return;

            int anomalies = 0;
            for (int i = 1; i < data.size(); i++) {
                StockDailyData prev = data.get(i - 1);
                StockDailyData curr = data.get(i);
                if (prev.getClosePrice() != null && prev.getClosePrice() > 0 &&
                    curr.getClosePrice() != null) {
                    double changePct = Math.abs(
                            (curr.getClosePrice() - prev.getClosePrice()) / prev.getClosePrice() * 100);
                    if (changePct > 22) {
                        anomalies++;
                        System.out.printf("⚠ 涨跌幅异常: %s %.2f%% (前收%.2f 今收%.2f)\n",
                                curr.getTradeDate(), changePct,
                                prev.getClosePrice(), curr.getClosePrice());
                    }
                }
            }

            System.out.println("=== 涨跌幅检测 ===");
            System.out.println("检查数据: " + data.size() + "条");
            System.out.println("异常涨跌: " + anomalies + "条");
            assertTrue(anomalies == 0, "不应有超过22%的涨跌幅异常，发现" + anomalies + "条");
        }
    }
}
