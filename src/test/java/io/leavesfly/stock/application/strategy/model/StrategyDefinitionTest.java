package io.leavesfly.stock.application.strategy.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StrategyDefinition 策略模型测试")
class StrategyDefinitionTest {

    @Test
    @DisplayName("hasScoring 要求有效权重")
    void hasScoringRequiresWeight() {
        StrategyDefinition def = new StrategyDefinition();
        assertFalse(def.hasScoring());

        ScoringProfile scoring = new ScoringProfile();
        scoring.setScoreWeight(0);
        def.setScoring(scoring);
        assertFalse(def.hasScoring());

        scoring.setScoreWeight(10);
        assertTrue(def.hasScoring());
    }

    @Test
    @DisplayName("hasBacktest 要求非空入场条件")
    void hasBacktestRequiresEntryConditions() {
        StrategyDefinition def = new StrategyDefinition();
        assertFalse(def.hasBacktest());

        BacktestProfile backtest = new BacktestProfile();
        backtest.setEntryConditions(List.of());
        def.setBacktest(backtest);
        assertFalse(def.hasBacktest());

        backtest.setEntryConditions(List.of(Map.of("type", "ma_cross")));
        assertTrue(def.hasBacktest());
    }

    @Test
    @DisplayName("hasScreening 要求非空打分规则")
    void hasScreeningRequiresRules() {
        StrategyDefinition def = new StrategyDefinition();
        assertFalse(def.hasScreening());

        ScreeningProfile screening = new ScreeningProfile();
        screening.setScoringRules(List.of());
        def.setScreening(screening);
        assertFalse(def.hasScreening());

        screening.setScoringRules(List.of(Map.of("metric", "pe")));
        assertTrue(def.hasScreening());
    }

    @Test
    @DisplayName("supports 应检查 capabilities 列表")
    void supportsCapability() {
        StrategyDefinition def = new StrategyDefinition();
        def.setCapabilities(List.of("backtest", "scoring"));

        assertTrue(def.supports("backtest"));
        assertTrue(def.supports("scoring"));
        assertFalse(def.supports("screening"));
    }
}
