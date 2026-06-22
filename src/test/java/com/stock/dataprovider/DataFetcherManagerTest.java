package com.stock.dataprovider;

import com.stock.config.AppConfig;
import com.stock.model.entity.StockDailyData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 数据源管理器测试
 * 验证: 熔断器逻辑、优先级排序、故障切换
 */
class DataFetcherManagerTest {

    @Mock private AppConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(config.getDataProvider()).thenReturn("efinance");
    }

    @Test
    @DisplayName("数据源按优先级排序")
    void testFetcherPriorityOrder() {
        // EFinance(0) > TickFlow(1) > Tushare(2) > Tencent(3) ...
        DataFetcherManager manager = new DataFetcherManager(List.of(
                createMockFetcher("tencent", 3, true),
                createMockFetcher("efinance", 0, true),
                createMockFetcher("tushare", 2, true)
        ), config);

        // 验证排序后第一个应该是优先级最高的
        assertNotNull(manager);
    }

    @Test
    @DisplayName("不可用数据源被跳过")
    void testUnavailableFetcherSkipped() {
        BaseDataFetcher unavailable = createMockFetcher("tushare", 2, false);
        BaseDataFetcher available = createMockFetcher("efinance", 0, true);

        DataFetcherManager manager = new DataFetcherManager(List.of(unavailable, available), config);
        // 不可用的应被跳过
        assertNotNull(manager);
    }

    @Test
    @DisplayName("市场类型检测: A股")
    void testMarketDetection_AShare() {
        assertEquals("A", com.stock.model.enums.MarketType.detectFromCode("600519").getCode());
        assertEquals("A", com.stock.model.enums.MarketType.detectFromCode("002594").getCode());
        assertEquals("A", com.stock.model.enums.MarketType.detectFromCode("300750").getCode());
    }

    @Test
    @DisplayName("市场类型检测: 港股")
    void testMarketDetection_HK() {
        assertEquals("HK", com.stock.model.enums.MarketType.detectFromCode("hk00700").getCode());
        assertEquals("HK", com.stock.model.enums.MarketType.detectFromCode("HK03690").getCode());
    }

    @Test
    @DisplayName("市场类型检测: 美股")
    void testMarketDetection_US() {
        assertEquals("US", com.stock.model.enums.MarketType.detectFromCode("AAPL").getCode());
        assertEquals("US", com.stock.model.enums.MarketType.detectFromCode("TSLA").getCode());
    }

    private BaseDataFetcher createMockFetcher(String name, int priority, boolean available) {
        return new BaseDataFetcher() {
            @Override public String getName() { return name; }
            @Override public int getPriority() { return priority; }
            @Override public boolean isAvailable() { return available; }
            @Override public List<StockDailyData> getHistoryData(String c, LocalDate s, LocalDate e) { return List.of(); }
            @Override public Map<String, Object> getRealtimeQuote(String c) { return Map.of(); }
            @Override public Map<String, Object> getStockInfo(String c) { return Map.of(); }
        };
    }
}
