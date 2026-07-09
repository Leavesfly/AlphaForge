package io.leavesfly.alphaforge.application.strategy;

import io.leavesfly.alphaforge.application.strategy.engine.ScreeningScoreEngine;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
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
    @DisplayName("缺失行情字段时应走 fallback 兜底分")
    void fallbackWhenMetricsMissing() {
        StrategyDefinition strategy = catalog.find("dual_low").orElseThrow();
        double score = engine.score(strategy, "600519", Map.of());
        assertTrue(score >= 40 && score < 85, "fallback score=" + score);
    }

    @Test
    @DisplayName("短期反转策略应对大跌给出更高分")
    void shortReversalScoresDeepDropHigher() {
        StrategyDefinition strategy = catalog.find("short_reversal").orElseThrow();
        double deep = engine.score(strategy, "600519", Map.of("change_pct", -10.0));
        double mild = engine.score(strategy, "600519", Map.of("change_pct", -4.0));
        assertTrue(deep > mild);
    }

    @Test
    @DisplayName("截面因子分位规则应按 rank 打分")
    void factorRankRuleShouldScore() {
        StrategyDefinition strategy = catalog.find("multi_factor").orElseThrow();
        double high = engine.score(strategy, "600519", Map.of(
                "factor_momentum_20_rank", 0.9,
                "factor_volatility_20_rank", 0.2,
                "change_pct", 1.0));
        double low = engine.score(strategy, "600519", Map.of(
                "factor_momentum_20_rank", 0.3,
                "factor_volatility_20_rank", 0.8,
                "change_pct", 1.0));
        assertTrue(high > low, "high=" + high + " low=" + low);
    }
}
