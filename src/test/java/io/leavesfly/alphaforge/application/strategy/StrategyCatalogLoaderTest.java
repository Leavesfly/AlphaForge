package io.leavesfly.alphaforge.application.strategy;

import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("应加载全部 19 个策略定义")
    void shouldLoadAllStrategies() {
        assertEquals(19, catalog.listAll().size());
    }

    @Test
    @DisplayName("应按能力正确分类策略")
    void shouldGroupByCapability() {
        assertTrue(catalog.listByCapability("backtest").size() >= 10);
        assertEquals(4, catalog.listByCapability("screening").size());
        assertTrue(catalog.listByCapability("scoring").size() >= 10);
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
