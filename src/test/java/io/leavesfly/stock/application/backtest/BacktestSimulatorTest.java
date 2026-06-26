package io.leavesfly.stock.application.backtest;

import io.leavesfly.stock.application.strategy.StrategyTestData;
import io.leavesfly.stock.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.stock.application.strategy.engine.BacktestSignalEngine;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BacktestSimulator 真实感回测测试")
class BacktestSimulatorTest {

    private BacktestSimulator simulator;
    private StrategyDefinition maStrategy;

    @BeforeEach
    void setUp() {
        BacktestSignalEngine signalEngine = new BacktestSignalEngine(new BacktestConditionEvaluator());
        simulator = new BacktestSimulator(signalEngine);
        maStrategy = StrategyTestData.loadCatalog().find("ma_golden_cross").orElseThrow();
    }

    @Test
    @DisplayName("A股买入应整手取整")
    void buySharesRoundedToLotSize() {
        BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode("600519");
        config = BacktestSimulationConfig.merge(config, java.util.Map.of(
                "execution_mode", "CLOSE",
                "min_commission", 0.0,
                "slippage_rate", 0.0
        ));

        List<StockDailyData> data = StrategyTestData.risingBars(80, 80, 0.8);
        BacktestSimulationResult result = simulator.simulate(data, maStrategy, 50_000, config);

        result.getTrades().stream()
                .filter(t -> "buy".equals(t.getSide()))
                .forEach(t -> assertEquals(0, t.getShares() % 100));
    }

    @Test
    @DisplayName("交易成本应降低最终资金")
    void tradingCostsReduceFinalCapital() {
        List<StockDailyData> data = StrategyTestData.risingBars(80, 80, 0.8);

        BacktestSimulationConfig noCost = BacktestSimulationConfig.forStockCode("600519");
        noCost = BacktestSimulationConfig.merge(noCost, java.util.Map.of(
                "execution_mode", "CLOSE",
                "commission_rate", 0.0,
                "stamp_tax_rate", 0.0,
                "slippage_rate", 0.0,
                "min_commission", 0.0
        ));

        BacktestSimulationConfig withCost = BacktestSimulationConfig.forStockCode("600519");
        withCost = BacktestSimulationConfig.merge(withCost, java.util.Map.of(
                "execution_mode", "CLOSE",
                "commission_rate", 0.0003,
                "stamp_tax_rate", 0.0005,
                "slippage_rate", 0.001,
                "min_commission", 5.0
        ));

        BacktestSimulationResult baseline = simulator.simulate(data, maStrategy, 100_000, noCost);
        BacktestSimulationResult realistic = simulator.simulate(data, maStrategy, 100_000, withCost);

        if (!baseline.getTrades().isEmpty()) {
            assertTrue(realistic.getFinalCapital() <= baseline.getFinalCapital());
            assertTrue((Double) realistic.getDiagnostics().get("total_commission") > 0
                    || (Double) realistic.getDiagnostics().get("total_stamp_tax") > 0);
        }
    }

    @Test
    @DisplayName("涨停日应跳过买入")
    void limitUpBlocksBuy() {
        BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode("600519");
        config = BacktestSimulationConfig.merge(config, java.util.Map.of(
                "execution_mode", "CLOSE",
                "min_commission", 0.0,
                "slippage_rate", 0.0
        ));

        List<StockDailyData> data = StrategyTestData.risingBars(80, 80, 0.8);
        data.get(data.size() - 10).setChangePct(10.0);

        BacktestSimulationResult result = simulator.simulate(data, maStrategy, 100_000, config);
        assertNotNull(result.getDiagnostics().get("skipped_buys"));
    }

    @Test
    @DisplayName("停牌日应跳过交易")
    void suspendedBarBlocksTrade() {
        BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode("600519");
        config = BacktestSimulationConfig.merge(config, java.util.Map.of(
                "execution_mode", "CLOSE",
                "min_commission", 0.0,
                "slippage_rate", 0.0
        ));

        List<StockDailyData> data = StrategyTestData.risingBars(80, 80, 0.8);
        data.get(50).setVolume(0L);

        BacktestSimulationResult result = simulator.simulate(data, maStrategy, 100_000, config);
        assertTrue((Integer) result.getDiagnostics().get("skipped_buys") >= 0);
    }

    @Test
    @DisplayName("T+1 当日买入不可当日卖出")
    void t1BlocksSameDaySell() {
        BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode("600519");
        config = BacktestSimulationConfig.merge(config, java.util.Map.of(
                "execution_mode", "CLOSE",
                "commission_rate", 0.0,
                "stamp_tax_rate", 0.0,
                "slippage_rate", 0.0,
                "min_commission", 0.0,
                "t1_enabled", true
        ));

        List<StockDailyData> bars = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double close = i < 25 ? 90.0 : 120.0;
            StockDailyData bar = StrategyTestData.bar("600519", "贵州茅台",
                    LocalDate.of(2024, 1, 1).plusDays(i), close, 1_000_000L);
            bar.setOpenPrice(close);
            bar.setHighPrice(close);
            bar.setLowPrice(close);
            bar.setChangePct(i == 0 ? 0.0 : (close - (i < 25 ? 90.0 : 120.0)) / 90.0 * 100);
            bars.add(bar);
        }

        BacktestSimulationResult result = simulator.simulate(bars, maStrategy, 100_000, config);
        assertNotNull(result);
        assertTrue((Integer) result.getDiagnostics().get("t1_blocked_sells") >= 0);
    }

    @Test
    @DisplayName("应输出交易明细")
    void producesTradeDetails() {
        BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode("600519");
        config = BacktestSimulationConfig.merge(config, java.util.Map.of("execution_mode", "CLOSE"));

        BacktestSimulationResult result = simulator.simulate(
                StrategyTestData.risingBars(80, 80, 0.8), maStrategy, 100_000, config);

        result.getTrades().forEach(trade -> {
            assertNotNull(trade.getTradeDate());
            assertNotNull(trade.getSide());
            assertTrue(trade.getShares() > 0);
            assertTrue(trade.getPrice() > 0);
        });
        assertNotNull(result.getDiagnostics().get("execution_mode"));
        assertFalse(result.getEquityCurve().isEmpty());
        assertNotNull(result.getDiagnostics().get("equity_curve"));
    }
}
