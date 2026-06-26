package io.leavesfly.stock.application.strategy;

import io.leavesfly.stock.application.strategy.engine.ScreeningScoreEngine;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScreeningScoreEngine YAML 驱动测试")
class ScreeningScoreEngineTest {

    private StrategyCatalog catalog;
    private ScreeningScoreEngine engine;

    @BeforeEach
    void setUp() {
        catalog = StrategyTestData.loadCatalog();
        engine = new ScreeningScoreEngine();
    }

    @Test
    @DisplayName("双低策略应对低 PE/PB 打出高分")
    void dualLowScoresLowValuation() {
        StrategyDefinition strategy = catalog.find("dual_low").orElseThrow();
        double score = engine.score(strategy, "600519", Map.of("pe", 10, "pb", 1.2));
        assertTrue(score > 0);
    }

    @Test
    @DisplayName("动量策略应对强势涨幅给出更高分")
    void momentumScoresStrongMoverHigher() {
        StrategyDefinition strategy = catalog.find("momentum").orElseThrow();
        double strong = engine.score(strategy, "600519", Map.of("change_pct", 3.0));
        double weak = engine.score(strategy, "600519", Map.of("change_pct", 0.5));
        assertTrue(strong > weak);
    }

    @Test
    @DisplayName("缺失行情字段时应返回 0 分")
    void fallbackWhenMetricsMissing() {
        StrategyDefinition strategy = catalog.find("dual_low").orElseThrow();
        double score = engine.score(strategy, "600519", Map.of());
        assertEquals(0, score);
    }
}
