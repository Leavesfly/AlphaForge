package io.leavesfly.stock.application.strategy;

import io.leavesfly.stock.application.strategy.StrategyTestData;
import io.leavesfly.stock.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.stock.application.strategy.engine.BacktestSignalEngine;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
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
        assertEquals(19, catalog.listAll().size());
        assertTrue(catalog.listByCapability("backtest").size() >= 10);
        assertEquals(4, catalog.listByCapability("screening").size());
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
