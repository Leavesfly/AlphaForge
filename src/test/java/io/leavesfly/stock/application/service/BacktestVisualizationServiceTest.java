package io.leavesfly.stock.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.leavesfly.stock.application.backtest.BacktestSimulator;
import io.leavesfly.stock.application.backtest.BacktestSimulationConfig;
import io.leavesfly.stock.application.backtest.BacktestSimulationResult;
import io.leavesfly.stock.application.strategy.StrategyTestData;
import io.leavesfly.stock.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.stock.application.strategy.engine.BacktestSignalEngine;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import io.leavesfly.stock.domain.model.entity.BacktestRecord;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.infrastructure.persistence.BacktestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BacktestVisualizationService 可视化测试")
class BacktestVisualizationServiceTest {

    @Mock
    private BacktestRepository backtestRepo;
    @Mock
    private DataFetcherManager dataFetcher;

    private BacktestVisualizationService service;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        service = new BacktestVisualizationService(backtestRepo, dataFetcher);
    }

    @Test
    @DisplayName("应生成净值曲线与月度收益数据")
    void shouldBuildVisualizationPayload() throws Exception {
        StrategyDefinition maStrategy = StrategyTestData.loadCatalog().find("ma_golden_cross").orElseThrow();
        BacktestSignalEngine signalEngine = new BacktestSignalEngine(new BacktestConditionEvaluator());
        BacktestSimulator simulator = new BacktestSimulator(signalEngine);
        BacktestSimulationConfig config = BacktestSimulationConfig.merge(
                BacktestSimulationConfig.forStockCode("600519"),
                Map.of("execution_mode", "CLOSE", "min_commission", 0.0));

        BacktestSimulationResult result = simulator.simulate(
                StrategyTestData.risingBars(80, 80, 0.8), maStrategy, 100_000, config);

        BacktestRecord record = new BacktestRecord();
        record.setId(1L);
        record.setStockCode("600519");
        record.setStrategyName("ma_golden_cross");
        record.setStartDate(LocalDate.of(2024, 1, 1).atStartOfDay());
        record.setEndDate(LocalDate.of(2024, 4, 1).atStartOfDay());
        record.setInitialCapital(100_000.0);
        record.setFinalCapital(result.getFinalCapital());
        record.setTotalReturnPct(result.getTotalReturnPct());
        record.setMaxDrawdownPct(result.getMaxDrawdownPct());
        record.setSharpeRatio(result.getSharpeRatio());
        record.setWinRatePct(result.getWinRatePct());
        record.setTradeDetails(objectMapper.writeValueAsString(result.getTrades()));
        record.setDiagnostics(objectMapper.writeValueAsString(result.getDiagnostics()));
        record.setCreatedAt(LocalDateTime.now());

        when(backtestRepo.findById(1L)).thenReturn(record);

        Map<String, Object> viz = service.buildVisualization(1L).orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> curve = (Map<String, Object>) viz.get("equity_curve");
        @SuppressWarnings("unchecked")
        List<String> dates = (List<String>) curve.get("dates");

        assertFalse(dates.isEmpty());
        assertNotNull(viz.get("monthly_returns"));
        assertNotNull(viz.get("summary"));
    }
}
