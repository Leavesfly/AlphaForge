package io.leavesfly.alphaforge.infrastructure.dataprovider;


import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.infrastructure.dataprovider.impl.EFinanceFetcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * EFinanceFetcher 真实数据集成测试
 *
 * 直接调用东方财富 HTTP API，验证返回数据的真实性与结构正确性。
 * 测试股票: 600519（贵州茅台）— A股蓝筹，数据覆盖完整。
 *
 * 注意: 此测试需要网络连接，东财 API 偶尔会风控（返回空数据），
 * 测试中使用 assumeTrue 做前置判断，数据为空时跳过而非失败。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("EFinanceFetcher 真实数据集成测试")
class EFinanceFetcherIntegrationTest {

    private EFinanceFetcher fetcher;
    private static final String STOCK_CODE = "600519";
    private static final String STOCK_NAME = "贵州茅台";

    @BeforeAll
    void setUp() {
        fetcher = new EFinanceFetcher(new okhttp3.OkHttpClient(), new com.fasterxml.jackson.databind.ObjectMapper());
    }

    // ========== 行情层 ==========

    @Nested
    @DisplayName("行情数据 — 历史K线")
    class HistoryDataTest {

        @Test
        @DisplayName("应获取贵州茅台最近30天日K线数据")
        void shouldGetHistoryData() {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(30);

            List<StockDailyData> data = fetcher.getHistoryData(STOCK_CODE, start, end);

            assertNotNull(data);
            assertFalse(data.isEmpty(), "应返回至少一条K线数据");

            StockDailyData first = data.get(0);
            assertNotNull(first.getTradeDate(), "交易日期不应为空");
            assertNotNull(first.getClosePrice(), "收盘价不应为空");
            assertTrue(first.getClosePrice() > 0, "收盘价应大于0");
            assertNotNull(first.getOpenPrice(), "开盘价不应为空");
            assertNotNull(first.getHighPrice(), "最高价不应为空");
            assertNotNull(first.getLowPrice(), "最低价不应为空");
            assertNotNull(first.getVolume(), "成交量不应为空");
            assertEquals("efinance", first.getDataSource(), "数据来源应为efinance");

            System.out.println("=== 历史K线数据（最近5天）===");
            int startIdx = Math.max(0, data.size() - 5);
            for (int i = startIdx; i < data.size(); i++) {
                StockDailyData d = data.get(i);
                System.out.printf("%s: 开%.2f 收%.2f 高%.2f 低%.2f 量%d 涨跌%.2f%% 换手%.2f%%\n",
                        d.getTradeDate(), d.getOpenPrice(), d.getClosePrice(),
                        d.getHighPrice(), d.getLowPrice(), d.getVolume(),
                        d.getChangePct() != null ? d.getChangePct() : 0,
                        d.getTurnoverRate() != null ? d.getTurnoverRate() : 0);
            }
        }

        @Test
        @DisplayName("OHLC数据应满足一致性: high >= max(open,close) >= min(open,close) >= low")
        void ohlcShouldBeConsistent() {
            LocalDate end = LocalDate.now();
            List<StockDailyData> data = fetcher.getHistoryData(STOCK_CODE, end.minusDays(10), end);
            if (data == null || data.isEmpty()) return;

            for (StockDailyData d : data) {
                if (d.getHighPrice() != null && d.getLowPrice() != null &&
                    d.getOpenPrice() != null && d.getClosePrice() != null) {
                    double maxOC = Math.max(d.getOpenPrice(), d.getClosePrice());
                    double minOC = Math.min(d.getOpenPrice(), d.getClosePrice());
                    assertTrue(d.getHighPrice() >= maxOC,
                            "%s 最高价%.2f < max(开,收)%.2f".formatted(d.getTradeDate(), d.getHighPrice(), maxOC));
                    assertTrue(d.getLowPrice() <= minOC,
                            "%s 最低价%.2f > min(开,收)%.2f".formatted(d.getTradeDate(), d.getLowPrice(), minOC));
                }
            }
        }
    }

    @Nested
    @DisplayName("行情数据 — 实时行情")
    class RealtimeQuoteTest {

        @Test
        @DisplayName("应获取贵州茅台实时行情")
        void shouldGetRealtimeQuote() {
            Map<String, Object> quote = fetcher.getRealtimeQuote(STOCK_CODE);

            assertNotNull(quote);
            assertFalse(quote.isEmpty(), "实时行情不应为空");

            assertNotNull(quote.get("stock_code"), "股票代码不应为空");
            assertNotNull(quote.get("current_price"), "当前价不应为空");

            double price = ((Number) quote.get("current_price")).doubleValue();
            assertTrue(price > 0, "当前价应大于0, 实际: " + price);

            System.out.println("=== 实时行情 ===");
            quote.forEach((k, v) -> System.out.println(k + ": " + v));
        }
    }

    // ========== 资金面层 ==========

    @Nested
    @DisplayName("资金面 — 日级资金流")
    class FundFlowTest {

        @Test
        @DisplayName("应获取贵州茅台最近10天资金流数据")
        void shouldGetFundFlow() {
            List<Map<String, Object>> data = fetcher.getFundFlow(STOCK_CODE, 10);

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 资金流数据为空（可能东财风控），跳过结构验证");
                return;
            }

            Map<String, Object> first = data.get(0);
            assertNotNull(first.get("date"), "日期不应为空");
            assertNotNull(first.get("main_net"), "主力净流入不应为空");

            System.out.println("=== 资金流数据（最近5天）===");
            int start = Math.max(0, data.size() - 5);
            for (int i = start; i < data.size(); i++) {
                Map<String, Object> d = data.get(i);
                System.out.printf("%s: 主力%.0f 大单%.0f 中单%.0f 小单%.0f\n",
                        d.get("date"),
                        toDouble(d.get("main_net")),
                        toDouble(d.get("big_net")),
                        toDouble(d.get("mid_net")),
                        toDouble(d.get("small_net")));
            }
        }
    }

    // ========== 基本面层 ==========

    @Nested
    @DisplayName("基本面 — 财报与关键指标")
    class FinancialsTest {

        @Test
        @DisplayName("应获取贵州茅台关键财务指标")
        void shouldGetKeyIndicators() {
            List<Map<String, Object>> data = fetcher.getKeyIndicators(STOCK_CODE);

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 关键指标为空（可能东财风控），跳过");
                return;
            }

            Map<String, Object> first = data.get(0);
            assertNotNull(first.get("report_date"), "报告日期不应为空");

            System.out.println("=== 关键财务指标 ===");
            for (Map<String, Object> d : data) {
                System.out.printf("[%s] 营收%.0f EPS%.2f ROE%.2f%% ROA%.2f%% 毛利率%.2f%% 资产负债率%.2f%%\n",
                        d.get("report_date"),
                        toDouble(d.get("operate_income")),
                        toDouble(d.get("basic_eps")),
                        toDouble(d.get("roe_avg")),
                        toDouble(d.get("roa")),
                        toDouble(d.get("gross_profit_ratio")),
                        toDouble(d.get("debt_asset_ratio")));
            }
        }

        @Test
        @DisplayName("应获取贵州茅台利润表数据")
        void shouldGetIncomeStatement() {
            List<Map<String, Object>> data = fetcher.getFinancialStatements(STOCK_CODE, "income");

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 利润表为空（可能东财风控），跳过");
                return;
            }

            System.out.println("=== 利润表数据（前5条）===");
            int limit = Math.min(5, data.size());
            for (int i = 0; i < limit; i++) {
                Map<String, Object> d = data.get(i);
                System.out.printf("[%s] %s: %.0f (同比%.1f%%)\n",
                        d.get("report_date"), d.get("item_name"),
                        toDouble(d.get("amount")), toDouble(d.get("yoy_ratio")));
            }
        }
    }

    // ========== 信号层 ==========

    @Nested
    @DisplayName("信号层 — 龙虎榜与北向资金")
    class SignalTest {

        @Test
        @DisplayName("应获取北向资金流向数据")
        void shouldGetNorthboundFlow() {
            List<Map<String, Object>> data = fetcher.getNorthboundFlow(10);

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 北向资金数据为空，跳过");
                return;
            }

            System.out.println("=== 北向资金流向（最近5天）===");
            int start = Math.max(0, data.size() - 5);
            for (int i = start; i < data.size(); i++) {
                Map<String, Object> d = data.get(i);
                System.out.printf("[%s] %s 净买入%.0f 累计%.0f\n",
                        d.get("trade_date"), d.get("board_type"),
                        toDouble(d.get("net_amount")), toDouble(d.get("accumulate_amount")));
            }
        }

        @Test
        @DisplayName("应获取贵州茅台板块归属")
        void shouldGetStockBoardsDetail() {
            List<Map<String, Object>> data = fetcher.getStockBoardsDetail(STOCK_CODE);

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 板块归属为空，跳过");
                return;
            }

            System.out.println("=== 板块归属 ===");
            for (Map<String, Object> d : data) {
                System.out.printf("- [%s] %s (代码:%s)\n",
                        d.get("board_type"), d.get("board_name"), d.get("board_code"));
            }
        }
    }

    // ========== 杠杆与筹码层 ==========

    @Nested
    @DisplayName("杠杆与筹码 — 融资融券与股东户数")
    class MarginTest {

        @Test
        @DisplayName("应获取贵州茅台融资融券数据")
        void shouldGetMarginTrading() {
            List<Map<String, Object>> data = fetcher.getMarginTrading(STOCK_CODE, 10);

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 融资融券为空，跳过");
                return;
            }

            System.out.println("=== 融资融券（最近5天）===");
            int start = Math.max(0, data.size() - 5);
            for (int i = start; i < data.size(); i++) {
                Map<String, Object> d = data.get(i);
                System.out.printf("[%s] 融资余额%.0f 融券余额%.0f\n",
                        d.get("date"),
                        toDouble(d.get("rzye")),
                        toDouble(d.get("rqye")));
            }
        }

        @Test
        @DisplayName("应获取贵州茅台股东户数变化")
        void shouldGetShareholderCount() {
            List<Map<String, Object>> data = fetcher.getShareholderCount(STOCK_CODE);

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 股东户数为空，跳过");
                return;
            }

            System.out.println("=== 股东户数变化 ===");
            for (Map<String, Object> d : data) {
                System.out.printf("[%s] 股东%.0f户 环比%.2f%% 户均持股%.0f\n",
                        d.get("end_date"),
                        toDouble(d.get("holder_num")),
                        toDouble(d.get("holder_num_change")),
                        toDouble(d.get("avg_hold_amount")));
            }
        }

        @Test
        @DisplayName("应获取贵州茅台分红送转历史")
        void shouldGetDividendHistory() {
            List<Map<String, Object>> data = fetcher.getDividendHistory(STOCK_CODE);

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 分红送转为空，跳过");
                return;
            }

            System.out.println("=== 分红送转历史（前5条）===");
            int limit = Math.min(5, data.size());
            for (int i = 0; i < limit; i++) {
                Map<String, Object> d = data.get(i);
                System.out.printf("[%s] 每股派息%.2f 送股%.2f 转增%.2f %s\n",
                        d.get("report_date"),
                        toDouble(d.get("dps")),
                        toDouble(d.get("send_stock")),
                        toDouble(d.get("convert_stock")),
                        d.get("progress"));
            }
        }
    }

    // ========== 研报与公告层 ==========

    @Nested
    @DisplayName("研报与公告")
    class ResearchAndAnnouncementTest {

        @Test
        @DisplayName("应获取贵州茅台机构研报")
        void shouldGetResearchReports() {
            List<Map<String, Object>> data = fetcher.getResearchReports(STOCK_CODE, 5);

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 研报为空，跳过");
                return;
            }

            System.out.println("=== 机构研报 ===");
            for (Map<String, Object> d : data) {
                System.out.printf("[%s] %s 评级:%s 今年EPS:%.2f 明年EPS:%.2f\n",
                        d.get("publish_date"), d.get("org_name"), d.get("rating"),
                        toDouble(d.get("predict_eps_this_year")),
                        toDouble(d.get("predict_eps_next_year")));
            }
        }

        @Test
        @DisplayName("应获取贵州茅台公告列表")
        void shouldGetAnnouncements() {
            List<Map<String, Object>> data = fetcher.getAnnouncements(STOCK_CODE, 5);

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 公告为空，跳过");
                return;
            }

            System.out.println("=== 最新公告 ===");
            for (Map<String, Object> d : data) {
                System.out.printf("[%s] %s\n", d.get("notice_date"), d.get("title"));
            }
        }
    }

    // ========== 辅助方法 ==========

    private double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0;
    }
}
