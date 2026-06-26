package io.leavesfly.stock.application.service;

import io.leavesfly.stock.application.strategy.StrategyTestData;
import io.leavesfly.stock.application.strategy.engine.ScreeningScoreEngine;
import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.infrastructure.persistence.AlphaSiftTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AlphaSiftScreeningEngine 智能选股测试")
class AlphaSiftScreeningEngineTest {

    @Mock
    private DataFetcherManager dataFetcher;
    @Mock
    private AppConfig config;
    @Mock
    private WatchlistService watchlistService;
    @Mock
    private AlphaSiftTaskRepository taskRepo;

    private AlphaSiftScreeningEngine engine;

    @BeforeEach
    void setUp() {
        when(config.getEnv(eq("SCREENING_UNIVERSE"), anyString())).thenReturn("watchlist");
        when(watchlistService.asStockPool()).thenReturn(List.of(
                Map.of("code", "600519", "name", "贵州茅台", "market", "A"),
                Map.of("code", "000858", "name", "五粮液", "market", "A")
        ));
        engine = new AlphaSiftScreeningEngine(dataFetcher, config, StrategyTestData.loadCatalog(),
                new ScreeningScoreEngine(), watchlistService, taskRepo, new ObjectMapper());
    }

    @Test
    @DisplayName("未知选股策略应返回空列表")
    void unknownStrategyReturnsEmpty() {
        List<Map<String, Object>> results = engine.screen("not_exist", "A", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("双低策略应对低估值股票打分并排序")
    void dualLowScreensLowValuationStocks() {
        when(dataFetcher.getRealtimeQuote(anyString())).thenReturn(Map.of());
        when(dataFetcher.getRealtimeQuote("600519")).thenReturn(Map.of("pe", 10.0, "pb", 1.2, "price", 1800));
        when(dataFetcher.getRealtimeQuote("000858")).thenReturn(Map.of("pe", 25.0, "pb", 3.0, "price", 150));

        List<Map<String, Object>> results = engine.screen("dual_low", "A", 5);

        assertFalse(results.isEmpty());
        assertEquals("dual_low", results.get(0).get("strategy"));
        assertTrue(((Number) results.get(0).get("score")).doubleValue() > 0);
        assertNotNull(results.get(0).get("reason"));
        if (results.size() >= 2) {
            double first = ((Number) results.get(0).get("score")).doubleValue();
            double second = ((Number) results.get(1).get("score")).doubleValue();
            assertTrue(first >= second);
        }
    }

    @Test
    @DisplayName("动量策略应对强势涨幅股票给出更高分")
    void momentumPrefersStrongMovers() {
        when(dataFetcher.getRealtimeQuote(anyString())).thenReturn(Map.of("change_pct", 0.2));
        when(dataFetcher.getRealtimeQuote("600519")).thenReturn(Map.of("change_pct", 3.5, "price", 100));

        List<Map<String, Object>> results = engine.screen("momentum", "A", 10);

        assertFalse(results.isEmpty());
        assertEquals("600519", results.get(0).get("stock_code"));
    }
}
