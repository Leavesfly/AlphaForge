package io.leavesfly.alphaforge.application.strategy.lifecycle;

import io.leavesfly.alphaforge.application.autonomy.AutonomyAuditLog;
import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.application.simulation.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulator;
import io.leavesfly.alphaforge.application.evaluation.StrategyQualityScore;
import io.leavesfly.alphaforge.application.evaluation.StrategyQualityScorer;
import io.leavesfly.alphaforge.application.strategy.engine.WalkForwardValidator;
import io.leavesfly.alphaforge.application.strategy.model.BacktestProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.application.strategy.model.WalkForwardResult;
import io.leavesfly.alphaforge.application.strategy.validator.StrategyValidator;
import io.leavesfly.alphaforge.config.AutonomyConfig;
import io.leavesfly.alphaforge.config.EnvVarProvider;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("StrategyPromotionGate 晋升质量门")
class StrategyPromotionGateTest {

    private StrategyPromotionGate gate;
    private StrategyQualityScorer scorer;
    private WalkForwardValidator wfValidator;
    private BacktestSimulator simulator;

    @BeforeEach
    void setUp() {
        EnvVarProvider env = mock(EnvVarProvider.class);
        when(env.getBool(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(env.get(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(env.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        AutonomyConfig config = new AutonomyConfig(env);
        config.init();
        AutonomyPolicy policy = new AutonomyPolicy(config, new AutonomyAuditLog());

        scorer = mock(StrategyQualityScorer.class);
        wfValidator = mock(WalkForwardValidator.class);
        simulator = mock(BacktestSimulator.class);
        when(simulator.simulate(any(), any(), anyDouble(), any()))
                .thenReturn(new BacktestSimulationResult());

        gate = new StrategyPromotionGate(policy, scorer, wfValidator, simulator,
                mock(StrategyValidator.class), mock(MarketDataPort.class));
    }

    private StrategyDefinition strategyNoParamSpace() {
        StrategyDefinition def = new StrategyDefinition();
        def.setId("test_strat");
        BacktestProfile bp = new BacktestProfile();
        bp.setParameters(Map.of("fast", 5));
        def.setBacktest(bp);
        return def;
    }

    private List<StockDailyData> sampleData() {
        List<StockDailyData> data = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            StockDailyData bar = new StockDailyData();
            bar.setStockCode("600519");
            bar.setTradeDate(LocalDate.now().minusDays(10 - i));
            bar.setClosePrice(100.0);
            data.add(bar);
        }
        return data;
    }

    @Test
    void gradeCRejected() {
        when(scorer.score(any(), any(), anyString())).thenReturn(
                new StrategyQualityScore(60, Map.of(), "C", List.of()));
        PromotionDecision d = gate.evaluate(strategyNoParamSpace(), sampleData());
        assertFalse(d.isPromotable());
        assertEquals(StrategyQualityScore.QualityGrade.C, d.getGrade());
        assertThrows(IllegalStateException.class, () -> gate.assertPromotable(d));
    }

    @Test
    void gradeBWithoutParamSpacePasses() {
        when(scorer.score(any(), any(), anyString())).thenReturn(
                new StrategyQualityScore(75, Map.of(), "B", List.of()));
        PromotionDecision d = gate.evaluate(strategyNoParamSpace(), sampleData());
        assertTrue(d.isPromotable());
        assertTrue(d.isWalkForwardPassed());
        assertDoesNotThrow(() -> gate.assertPromotable(d));
        verify(wfValidator, never()).validate(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void walkForwardFailBlocksEvenWithGradeA() {
        StrategyDefinition def = strategyNoParamSpace();
        BacktestProfile bp = def.getBacktest();
        Map<String, List<Object>> space = new java.util.HashMap<>();
        space.put("fast", List.of(5, 10));
        bp.setParamSpace(space);

        WalkForwardResult wf = new WalkForwardResult();
        wf.setWindowCount(3);
        wf.setOverfitRatio(0.1); // below 0.3
        when(wfValidator.validate(any(), any(), anyInt(), anyDouble())).thenReturn(wf);
        when(scorer.score(any(), any(), anyString())).thenReturn(
                new StrategyQualityScore(90, Map.of(), "A", List.of()));

        PromotionDecision d = gate.evaluate(def, sampleData());
        assertFalse(d.isPromotable());
        assertFalse(d.isWalkForwardPassed());
    }
}
