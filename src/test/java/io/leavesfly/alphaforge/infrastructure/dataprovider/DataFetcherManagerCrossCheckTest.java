package io.leavesfly.alphaforge.infrastructure.dataprovider;

import io.leavesfly.alphaforge.config.DataProviderConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DataFetcherManager 交叉校验接入")
class DataFetcherManagerCrossCheckTest {

    @Test
    @DisplayName("reject 模式下交叉校验失败应切换到下一数据源")
    void rejectModeShouldFailoverOnCrossCheckFailure() {
        DataProviderConfig config = mock(DataProviderConfig.class);
        when(config.getDataProvider()).thenReturn("auto");
        when(config.isCrossCheckEnabled()).thenReturn(true);
        when(config.isCrossCheckRejectMode()).thenReturn(true);
        when(config.getCrossCheckSampleDays()).thenReturn(20);
        when(config.getCrossCheckCloseTolerance()).thenReturn(0.005);
        when(config.getCrossCheckOhlcTolerance()).thenReturn(0.01);
        when(config.getCrossCheckRejectRatio()).thenReturn(0.10);
        when(config.getCrossCheckMode()).thenReturn("reject");

        BaseDataFetcher primary = mockFetcher("efinance", "eastmoney", 0, MarketType.A);
        BaseDataFetcher secondary = mockFetcher("tushare", "tushare", 2, MarketType.A);
        BaseDataFetcher fallback = mockFetcher("tickflow", "tickflow", 1, MarketType.A);

        LocalDate start = LocalDate.of(2024, 1, 2);
        LocalDate end = LocalDate.of(2024, 1, 3);

        // 脏主源 close=12；干净备源/fallback close=10
        when(primary.getHistoryData(eq("600519"), any(), any()))
                .thenReturn(List.of(bar(start, 12.0), bar(end, 12.0)));
        when(secondary.getHistoryData(eq("600519"), any(), any()))
                .thenReturn(List.of(bar(start, 10.0), bar(end, 10.0)));
        when(fallback.getHistoryData(eq("600519"), any(), any()))
                .thenReturn(List.of(bar(start, 10.0), bar(end, 10.0)));

        DataFetcherManager manager = new DataFetcherManager(
                config,
                new FetcherFailoverExecutor(List.of(primary, secondary, fallback), config),
                null,
                null,
                null,
                new CrossSourceValidator()
        );

        List<StockDailyData> result = manager.getHistoryData("600519", start, end);

        assertFalse(result.isEmpty());
        assertEquals(10.0, result.get(0).getClosePrice(), 1e-9);
        verify(primary, atLeastOnce()).getHistoryData(eq("600519"), any(), any());
        verify(fallback, atLeastOnce()).getHistoryData(eq("600519"), any(), any());
    }

    @Test
    @DisplayName("同族备源不应被选作交叉校验源")
    void sameFamilyShouldNotBePickedAsSecondary() {
        DataProviderConfig config = mock(DataProviderConfig.class);
        when(config.getDataProvider()).thenReturn("auto");
        when(config.isCrossCheckEnabled()).thenReturn(true);
        when(config.isCrossCheckRejectMode()).thenReturn(false);
        when(config.getCrossCheckSampleDays()).thenReturn(20);
        when(config.getCrossCheckCloseTolerance()).thenReturn(0.005);
        when(config.getCrossCheckOhlcTolerance()).thenReturn(0.01);
        when(config.getCrossCheckRejectRatio()).thenReturn(0.10);
        when(config.getCrossCheckMode()).thenReturn("warn");

        BaseDataFetcher efinance = mockFetcher("efinance", "eastmoney", 0, MarketType.A);
        BaseDataFetcher akshare = mockFetcher("akshare", "eastmoney", 1, MarketType.A);

        LocalDate start = LocalDate.of(2024, 1, 2);
        LocalDate end = LocalDate.of(2024, 1, 3);
        when(efinance.getHistoryData(eq("600519"), any(), any()))
                .thenReturn(List.of(bar(start, 10.0), bar(end, 10.0)));

        DataFetcherManager manager = new DataFetcherManager(
                config,
                new FetcherFailoverExecutor(List.of(efinance, akshare), config),
                null,
                null,
                null,
                new CrossSourceValidator()
        );

        List<StockDailyData> result = manager.getHistoryData("600519", start, end);
        assertEquals(2, result.size());
        // akshare 同族，不应被拉来做交叉校验
        verify(akshare, never()).getHistoryData(anyString(), any(), any());
    }

    private static BaseDataFetcher mockFetcher(String name, String family, int priority, MarketType market) {
        BaseDataFetcher f = mock(BaseDataFetcher.class);
        when(f.getName()).thenReturn(name);
        when(f.getDataFamily()).thenReturn(family);
        when(f.getPriority()).thenReturn(priority);
        when(f.isAvailable()).thenReturn(true);
        when(f.getSupportedMarkets()).thenReturn(Set.of(market));
        when(f.getRateLimitMs()).thenReturn(1L);
        when(f.getRealtimeQuote(anyString())).thenReturn(Map.of());
        when(f.getStockInfo(anyString())).thenReturn(Map.of());
        return f;
    }

    private static StockDailyData bar(LocalDate date, double close) {
        StockDailyData d = new StockDailyData();
        d.setStockCode("600519");
        d.setTradeDate(date);
        d.setOpenPrice(close);
        d.setHighPrice(close);
        d.setLowPrice(close);
        d.setClosePrice(close);
        d.setVolume(1000L);
        return d;
    }
}
