package io.leavesfly.alphaforge.application.strategy;

import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StrategyCatalogLoader 策略目录加载测试")
class StrategyCatalogLoaderTest {

    private StrategyCatalog catalog;

    @BeforeEach
    void setUp() {
        StrategyCatalogLoader loader = StrategyCatalogLoader.createAndLoad();
        catalog = loader.getCatalog();
    }

    @Test
    @DisplayName("应加载全部 23 个策略定义")
    void shouldLoadAllStrategies() {
        assertEquals(23, catalog.listAll().size());
    }

    @Test
    @DisplayName("全部 23 个策略应具备 backtest 能力且可用")
    void allStrategiesShouldSupportBacktest() {
        assertEquals(23, catalog.listByCapability("backtest").size());
        for (StrategyDefinition strategy : catalog.listAll()) {
            assertTrue(strategy.supports("backtest"), strategy.getId() + " 应声明 backtest");
            assertTrue(strategy.hasBacktest(), strategy.getId() + " 应有 entry_conditions");
            assertTrue(strategy.isAvailable(),
                    strategy.getId() + " 应可用: " + strategy.getUnavailableReason());
        }
    }

    @Test
    @DisplayName("新策略 short_reversal / multi_factor / channel_breakout / boll_mean_reversion 应可用")
    void newClassicStrategiesShouldBeAvailable() {
        for (String id : List.of("short_reversal", "multi_factor", "channel_breakout", "boll_mean_reversion")) {
            StrategyDefinition strategy = catalog.find(id).orElseThrow();
            assertTrue(strategy.isAvailable(), id + ": " + strategy.getUnavailableReason());
            assertTrue(strategy.hasBacktest());
        }
        assertTrue(catalog.find("short_reversal").orElseThrow().hasScoring());
        assertTrue(catalog.find("short_reversal").orElseThrow().hasScreening());
        assertTrue(catalog.find("multi_factor").orElseThrow().hasScoring());
        assertTrue(catalog.find("channel_breakout").orElseThrow().hasScoring());
        assertTrue(catalog.find("boll_mean_reversion").orElseThrow().hasScoring());
    }

    @Test
    @DisplayName("应能按 id 查找策略并解析 backtest 段")
    void shouldFindStrategyWithBacktestProfile() {
        StrategyDefinition strategy = catalog.find("ma_golden_cross").orElseThrow();
        assertEquals("均线金叉", strategy.getLabel());
        assertTrue(strategy.hasBacktest());
        assertTrue(strategy.supports("backtest"));
        assertEquals(0.95, strategy.getBacktest().getPositionSize(), 0.001);
    }

    @Test
    @DisplayName("应能查找含 screening 与 scoring 双能力的策略")
    void shouldFindMultiCapabilityStrategy() {
        StrategyDefinition bullTrend = catalog.find("bull_trend").orElseThrow();
        assertTrue(bullTrend.supports("backtest"));
        assertTrue(bullTrend.supports("scoring"));
        assertFalse(bullTrend.hasScreening());
    }

    @Test
    @DisplayName("未知策略 id 应返回空")
    void shouldReturnEmptyForUnknownId() {
        assertTrue(catalog.find("not_exist").isEmpty());
        assertTrue(catalog.find(null).isEmpty());
        assertTrue(catalog.find("").isEmpty());
    }

    @Test
    @DisplayName("catalog 应包含分类与能力中文说明")
    void shouldLoadCategoryAndCapabilityLabels() {
        assertFalse(catalog.getCategories().isEmpty());
        assertFalse(catalog.getCapabilities().isEmpty());
        assertEquals("技术面", catalog.getCategories().get("technical"));
        assertEquals("情绪面", catalog.getCategories().get("sentiment"));
    }
}
