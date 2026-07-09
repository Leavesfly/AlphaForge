package io.leavesfly.alphaforge.application.backtest.pairs;

import io.leavesfly.alphaforge.application.backtest.BacktestSimulationConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PairBacktestSimulator 配对交易回测测试")
class PairBacktestSimulatorTest {

    private final PairBacktestSimulator simulator = new PairBacktestSimulator();

    private StockDailyData bar(String code, LocalDate date, double close) {
        StockDailyData b = new StockDailyData();
        b.setStockCode(code);
        b.setTradeDate(date);
        b.setClosePrice(close);
        b.setVolume(1_000_000L);
        return b;
    }

    private PairTradingConfig config() {
        PairTradingConfig cfg = PairTradingConfig.defaults();
        cfg.setLookbackWindow(10);
        cfg.setEntryZ(1.5);
        cfg.setExitZ(0.5);
        cfg.setStopZ(5.0);
        cfg.setMinCorrelation(0.5);
        cfg.setHedgeRatioMode("ratio");   // beta=1，价差即扰动，测试可控
        return cfg;
    }

    @Test
    @DisplayName("价差偏离时买入便宜腿、回归时平仓")
    void tradesOnDivergenceAndReversion() {
        PairTradingConfig cfg = config();
        List<StockDailyData> a = new ArrayList<>();
        List<StockDailyData> b = new ArrayList<>();
        LocalDate d0 = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 40; i++) {
            double base = 100 + i * 0.1;
            double wobble = i == 25 ? -3.0 : (i > 25 ? 0.0 : (i % 2 == 0 ? 0.3 : -0.3));
            a.add(bar("600000", d0.plusDays(i), base + wobble));
            b.add(bar("600519", d0.plusDays(i), base));
        }

        BacktestSimulationConfig aCost = BacktestSimulationConfig.forStockCode("600000");
        BacktestSimulationConfig bCost = BacktestSimulationConfig.forStockCode("600519");

        PairBacktestResult result = simulator.simulate(a, b, cfg, 100_000, aCost, bCost);

        assertFalse(result.getTrades().isEmpty(), "价差骤降应触发交易");
        assertTrue(result.getTotalTrades() >= 1, "至少完成一笔配对交易");

        boolean boughtLegA = result.getTrades().stream()
                .anyMatch(t -> "buy".equals(t.getSide()) && "entry_A".equals(t.getReason()));
        assertTrue(boughtLegA, "价差偏低时应买入 A 腿");

        boolean sold = result.getTrades().stream().anyMatch(t -> "sell".equals(t.getSide()));
        assertTrue(sold, "价差回归后应平仓卖出");

        assertFalse(result.getEquityCurve().isEmpty(), "应记录净值曲线");
        assertTrue(result.getFinalCapital() > 0, "最终资金应为正");
    }

    @Test
    @DisplayName("输出配对特有诊断指标")
    void producesPairDiagnostics() {
        PairTradingConfig cfg = config();
        List<StockDailyData> a = new ArrayList<>();
        List<StockDailyData> b = new ArrayList<>();
        LocalDate d0 = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 40; i++) {
            double base = 100 + i * 0.1;
            double wobble = i == 25 ? -3.0 : (i > 25 ? 0.0 : (i % 2 == 0 ? 0.3 : -0.3));
            a.add(bar("600000", d0.plusDays(i), base + wobble));
            b.add(bar("600519", d0.plusDays(i), base));
        }

        PairBacktestResult result = simulator.simulate(a, b, cfg, 100_000,
                BacktestSimulationConfig.forStockCode("600000"),
                BacktestSimulationConfig.forStockCode("600519"));

        assertNotNull(result.getDiagnostics().get("beta"));
        assertNotNull(result.getDiagnostics().get("correlation"));
        assertNotNull(result.getDiagnostics().get("mean_reversion_half_life"));
        assertNotNull(result.getDiagnostics().get("suitable_for_pairs"));
        assertEquals(40, result.getDiagnostics().get("aligned_days"));
    }

    @Test
    @DisplayName("重叠交易日不足窗口时返回空结果并标注原因")
    void insufficientOverlapReturnsEmpty() {
        PairTradingConfig cfg = config();  // window=10
        List<StockDailyData> a = new ArrayList<>();
        List<StockDailyData> b = new ArrayList<>();
        LocalDate d0 = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 8; i++) {   // 仅 8 天 <= window+1
            a.add(bar("600000", d0.plusDays(i), 100 + i));
            b.add(bar("600519", d0.plusDays(i), 100 + i));
        }

        PairBacktestResult result = simulator.simulate(a, b, cfg, 100_000,
                BacktestSimulationConfig.forStockCode("600000"),
                BacktestSimulationConfig.forStockCode("600519"));

        assertEquals(100_000, result.getFinalCapital(), 1e-9);
        assertEquals(Boolean.FALSE, result.getDiagnostics().get("suitable_for_pairs"));
        assertNotNull(result.getDiagnostics().get("reason"));
        assertTrue(result.getTrades().isEmpty());
    }
}
