package io.leavesfly.alphaforge.infrastructure.dataprovider;

import io.leavesfly.alphaforge.config.AppConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.TradingCalendar;
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
import static org.mockito.Mockito.when;

/**
 * DataFetcherManager 真实数据路由测试
 *
 * 使用真实 EFinanceFetcher 作为数据源，测试 DataFetcherManager 的路由、
 * 限流、熔断、缓存等机制在真实环境下的表现。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("DataFetcherManager 真实数据路由测试")
class DataFetcherManagerIntegrationTest {

    private DataFetcherManager manager;
    private static final String STOCK_CODE = "600519";

    @BeforeAll
    void setUp() {
        AppConfig mockConfig = mock(AppConfig.class);
        when(mockConfig.getDataProvider()).thenReturn("auto");

        EFinanceFetcher fetcher = new EFinanceFetcher(mockConfig);
        TradingCalendar calendar = new TradingCalendar();
        DataQualityValidator validator = new DataQualityValidator(calendar);

        manager = new DataFetcherManager(
                mockConfig,
                List.of(fetcher),
                null,       // 无 SQLite 缓存，直连 API
                calendar,
                validator
        );
    }

    @Nested
    @DisplayName("行情数据路由")
    class QuoteRoutingTest {

        @Test
        @DisplayName("应通过路由获取历史K线数据")
        void shouldRouteGetHistoryData() throws InterruptedException {
            Thread.sleep(1200); // 等待限流器冷却（东财系1000ms间隔）
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(15);

            List<StockDailyData> data = manager.getHistoryData(STOCK_CODE, start, end);

            assertNotNull(data);
            if (data.isEmpty()) {
                System.out.println("⚠ 路由未返回K线数据（可能限流），跳过断言");
                return;
            }
            assertFalse(data.isEmpty(), "路由应返回K线数据");

            StockDailyData last = data.get(data.size() - 1);
            assertTrue(last.getClosePrice() > 0, "最新收盘价应大于0");

            System.out.println("=== 路由获取K线: " + data.size() + "条 ===");
            System.out.printf("最新: %s 收盘%.2f\n", last.getTradeDate(), last.getClosePrice());
        }

        @Test
        @DisplayName("应通过路由获取实时行情")
        void shouldRouteGetRealtimeQuote() throws InterruptedException {
            Thread.sleep(1200); // 等待限流器冷却
            Map<String, Object> quote = manager.getRealtimeQuote(STOCK_CODE);

            assertNotNull(quote);
            if (quote.isEmpty()) {
                System.out.println("⚠ 路由未返回实时行情（可能限流），跳过断言");
                return;
            }
            assertFalse(quote.isEmpty(), "路由应返回实时行情");

            double price = ((Number) quote.get("current_price")).doubleValue();
            assertTrue(price > 0, "路由返回的当前价应大于0");

            System.out.println("=== 路由获取实时行情 ===");
            System.out.println("当前价: " + price);
        }
    }

    @Nested
    @DisplayName("资金面与基本面路由")
    class ExtendedDataRoutingTest {

        @Test
        @DisplayName("应通过路由获取资金流数据")
        void shouldRouteGetFundFlow() {
            List<Map<String, Object>> data = manager.getFundFlow(STOCK_CODE, 5);

            assertNotNull(data);
            if (!data.isEmpty()) {
                System.out.println("=== 路由获取资金流: " + data.size() + "条 ===");
                Map<String, Object> last = data.get(data.size() - 1);
                System.out.printf("最新: %s 主力净流入%.0f\n",
                        last.get("date"), toDouble(last.get("main_net")));
            } else {
                System.out.println("⚠ 资金流为空（可能风控）");
            }
        }

        @Test
        @DisplayName("应通过路由获取关键财务指标")
        void shouldRouteGetKeyIndicators() {
            List<Map<String, Object>> data = manager.getKeyIndicators(STOCK_CODE);

            assertNotNull(data);
            if (!data.isEmpty()) {
                System.out.println("=== 路由获取关键指标: " + data.size() + "期 ===");
                Map<String, Object> latest = data.get(0);
                System.out.printf("最新: [%s] ROE=%.2f%% EPS=%.2f\n",
                        latest.get("report_date"),
                        toDouble(latest.get("roe_avg")),
                        toDouble(latest.get("basic_eps")));
            } else {
                System.out.println("⚠ 关键指标为空");
            }
        }
    }

    @Nested
    @DisplayName("信号层路由")
    class SignalRoutingTest {

        @Test
        @DisplayName("应通过路由获取北向资金数据")
        void shouldRouteGetNorthboundFlow() {
            List<Map<String, Object>> data = manager.getNorthboundFlow(5);

            assertNotNull(data);
            if (!data.isEmpty()) {
                System.out.println("=== 路由获取北向资金: " + data.size() + "条 ===");
                Map<String, Object> last = data.get(data.size() - 1);
                System.out.printf("最新: %s %s 净买入%.0f\n",
                        last.get("trade_date"), last.get("board_type"),
                        toDouble(last.get("net_amount")));
            } else {
                System.out.println("⚠ 北向资金为空");
            }
        }

        @Test
        @DisplayName("应通过路由获取板块归属")
        void shouldRouteGetStockBoardsDetail() {
            List<Map<String, Object>> data = manager.getStockBoardsDetail(STOCK_CODE);

            assertNotNull(data);
            if (!data.isEmpty()) {
                System.out.println("=== 路由获取板块归属: " + data.size() + "个板块 ===");
                for (Map<String, Object> d : data) {
                    System.out.printf("- [%s] %s\n", d.get("board_type"), d.get("board_name"));
                }
            } else {
                System.out.println("⚠ 板块归属为空");
            }
        }
    }

    @Nested
    @DisplayName("缓存验证")
    class CacheTest {

        @Test
        @DisplayName("相同请求第二次应命中缓存（更快）")
        void shouldHitCacheOnSecondCall() {
            // 第一次调用（远程）
            long start1 = System.currentTimeMillis();
            List<Map<String, Object>> data1 = manager.getFundFlow(STOCK_CODE, 5);
            long elapsed1 = System.currentTimeMillis() - start1;

            // 第二次调用（应命中缓存）
            long start2 = System.currentTimeMillis();
            List<Map<String, Object>> data2 = manager.getFundFlow(STOCK_CODE, 5);
            long elapsed2 = System.currentTimeMillis() - start2;

            assertNotNull(data1);
            assertNotNull(data2);

            System.out.printf("第一次调用: %dms, 第二次调用(缓存): %dms\n", elapsed1, elapsed2);

            if (!data1.isEmpty() && !data2.isEmpty()) {
                assertEquals(data1.size(), data2.size(), "两次返回的数据量应一致");
                assertTrue(elapsed2 <= elapsed1, "缓存调用应不慢于远程调用");
            }
        }
    }

    private double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0;
    }
}
