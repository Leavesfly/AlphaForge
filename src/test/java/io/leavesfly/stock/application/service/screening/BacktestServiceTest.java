package io.leavesfly.stock.application.service.screening;

import io.leavesfly.stock.application.backtest.BacktestSimulator;
import io.leavesfly.stock.application.strategy.StrategyTestData;
import io.leavesfly.stock.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.stock.application.strategy.engine.BacktestSignalEngine;
import io.leavesfly.stock.domain.model.entity.backtest.BacktestRecord;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.infrastructure.persistence.backtest.BacktestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BacktestService 回测服务测试")
class BacktestServiceTest {

    @Mock
    private DataFetcherManager dataFetcher;
    @Mock
    private BacktestRepository backtestRepo;

    private BacktestService service;

    @BeforeEach
    void setUp() {
        BacktestSignalEngine signalEngine = new BacktestSignalEngine(new BacktestConditionEvaluator());
        service = new BacktestService(dataFetcher, backtestRepo, StrategyTestData.loadCatalog(),
                new BacktestSimulator(signalEngine), signalEngine);
    }

    @Test
    @DisplayName("未知策略应返回 null 且不保存记录")
    void unknownStrategyReturnsNull() {
        BacktestRecord record = service.runBacktest("600519", "unknown_strategy",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1), 100_000);
        assertNull(record);
        verify(backtestRepo, never()).save(any());
        verify(dataFetcher, never()).getHistoryData(anyString(), any(), any());
    }

    @Test
    @DisplayName("历史数据不足应返回 null")
    void insufficientDataReturnsNull() {
        when(dataFetcher.getHistoryData(eq("600519"), any(), any()))
                .thenReturn(StrategyTestData.risingBars(10, 100, 0.5));

        BacktestRecord record = service.runBacktest("600519", "ma_golden_cross",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1), 100_000);

        assertNull(record);
        verify(backtestRepo, never()).save(any());
    }

    @Test
    @DisplayName("有效策略应完成回测并持久化记录")
    void validStrategyRunsAndSaves() {
        List<StockDailyData> history = StrategyTestData.risingBars(80, 80, 0.8);
        when(dataFetcher.getHistoryData(eq("600519"), any(), any())).thenReturn(history);
        when(backtestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BacktestRecord record = service.runBacktest("600519", "ma_golden_cross",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1), 100_000);

        assertNotNull(record);
        assertEquals("600519", record.getStockCode());
        assertEquals("ma_golden_cross", record.getStrategyName());
        assertEquals(100_000.0, record.getInitialCapital());
        assertNotNull(record.getFinalCapital());
        assertNotNull(record.getTradeDetails());
        assertNotNull(record.getParameters());
        assertNotNull(record.getDiagnostics());

        ArgumentCaptor<BacktestRecord> captor = ArgumentCaptor.forClass(BacktestRecord.class);
        verify(backtestRepo).save(captor.capture());
        assertEquals("ma_golden_cross", captor.getValue().getStrategyName());
    }
}
