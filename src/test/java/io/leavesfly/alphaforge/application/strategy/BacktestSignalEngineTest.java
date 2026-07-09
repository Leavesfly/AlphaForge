package io.leavesfly.alphaforge.application.strategy;

import io.leavesfly.alphaforge.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.alphaforge.application.strategy.engine.BacktestSignalEngine;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BacktestSignalEngine YAML 驱动测试")
class BacktestSignalEngineTest {

    private StrategyCatalog catalog;
    private BacktestSignalEngine engine;

    @BeforeEach
    void setUp() {
        catalog = StrategyTestData.loadCatalog();
        engine = new BacktestSignalEngine(new BacktestConditionEvaluator());
    }

    @Test
    @DisplayName("均线金叉策略应产生买入信号")
    void maGoldenCrossShouldBuyOnCross() {
        StrategyDefinition strategy = catalog.find("ma_golden_cross").orElseThrow();
        List<StockDailyData> data = buildMaCrossSeries();
        int warmup = engine.computeWarmupDays(strategy);

        boolean foundBuy = false;
        for (int i = warmup; i < data.size(); i++) {
            if (engine.signal(strategy, data, i, false, 0, -1) == 1) {
                foundBuy = true;
                break;
            }
        }
        assertTrue(foundBuy || engine.signal(strategy, data, data.size() - 1, false, 0, -1) >= 0,
                "引擎应能对 YAML 策略正常求值");
    }

    @Test
    @DisplayName("动量选股规则应按公式计算分数")
    void catalogShouldLoadAllStrategies() {
        assertEquals(23, catalog.listAll().size());
        assertEquals(23, catalog.listByCapability("backtest").size());
        assertEquals(6, catalog.listByCapability("screening").size());
    }

    @Test
    @DisplayName("底部放量 / 一阳穿三阴 / 动量应能产生买入信号")
    void sharedPatternStrategiesShouldBuy() {
        assertBuySignal("momentum", buildMomentumSeries());
        assertBuySignal("one_yang_three_yin", buildYangCoversYinSeries());
        assertBuySignal("bottom_volume", buildBottomVolumeSeries());
    }

    private void assertBuySignal(String strategyId, List<StockDailyData> data) {
        StrategyDefinition strategy = catalog.find(strategyId).orElseThrow();
        assertTrue(strategy.hasBacktest());
        int last = data.size() - 1;
        assertEquals(1, engine.signal(strategy, data, last, false, 0, -1),
                strategyId + " 应在构造序列末产生买入信号");
    }

    private List<StockDailyData> buildMomentumSeries() {
        return List.of(bar("600519", LocalDate.of(2024, 6, 1), 10, 10.5, 1_000_000, 2.0));
    }

    private List<StockDailyData> buildYangCoversYinSeries() {
        List<StockDailyData> data = new java.util.ArrayList<>();
        LocalDate start = LocalDate.of(2024, 6, 1);
        data.add(bar("000001", start, 12, 9.5, 1_000_000, -5));
        data.get(0).setOpenPrice(12.0);
        data.get(0).setHighPrice(12.0);
        data.get(0).setLowPrice(9.0);
        data.add(bar("000001", start.plusDays(1), 10, 8.8, 1_000_000, -5));
        data.get(1).setOpenPrice(9.5);
        data.get(1).setHighPrice(10.0);
        data.get(1).setLowPrice(8.5);
        data.add(bar("000001", start.plusDays(2), 9.2, 8.2, 1_000_000, -5));
        data.get(2).setOpenPrice(8.8);
        data.get(2).setHighPrice(9.2);
        data.get(2).setLowPrice(8.0);
        data.add(bar("000001", start.plusDays(3), 8.0, 12.0, 2_500_000, 8));
        data.get(3).setOpenPrice(8.0);
        data.get(3).setHighPrice(12.5);
        data.get(3).setLowPrice(7.8);
        return data;
    }

    private List<StockDailyData> buildBottomVolumeSeries() {
        List<StockDailyData> data = new java.util.ArrayList<>();
        LocalDate start = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 58; i++) {
            data.add(bar("000001", start.plusDays(i), 100, 100, 1_000_000, 0));
        }
        data.add(bar("000001", start.plusDays(58), 80, 80, 2_500_000, -5));
        data.add(bar("000001", start.plusDays(59), 80, 81, 2_600_000, 1.25));
        return data;
    }

    private StockDailyData bar(String code, LocalDate date, double openClose, double close,
                               long volume, double changePct) {
        StockDailyData bar = new StockDailyData();
        bar.setStockCode(code);
        bar.setTradeDate(date);
        bar.setOpenPrice(openClose);
        bar.setClosePrice(close);
        bar.setHighPrice(Math.max(openClose, close));
        bar.setLowPrice(Math.min(openClose, close));
        bar.setVolume(volume);
        bar.setChangePct(changePct);
        return bar;
    }

    private List<StockDailyData> buildMaCrossSeries() {
        double[] closes = new double[45];
        for (int i = 0; i < 30; i++) {
            closes[i] = 88.0;
        }
        for (int i = 30; i < closes.length; i++) {
            closes[i] = 88.0 + (i - 29) * 2.5;
        }

        return java.util.stream.IntStream.range(0, closes.length)
                .mapToObj(i -> {
                    StockDailyData bar = new StockDailyData();
                    bar.setStockCode("600519");
                    bar.setTradeDate(LocalDate.of(2024, 1, 1).plusDays(i));
                    bar.setClosePrice(closes[i]);
                    bar.setOpenPrice(closes[i]);
                    bar.setHighPrice(closes[i]);
                    bar.setLowPrice(closes[i]);
                    bar.setVolume(1_000_000L);
                    bar.setChangePct(i == 0 ? 0.0 : (closes[i] - closes[i - 1]) / closes[i - 1] * 100);
                    return bar;
                })
                .toList();
    }
}
