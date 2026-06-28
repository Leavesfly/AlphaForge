package io.leavesfly.stock.application.strategy;

import io.leavesfly.stock.application.strategy.engine.CompositeScoringEngine;
import io.leavesfly.stock.application.strategy.engine.CompositeScoringResult;
import io.leavesfly.stock.application.strategy.engine.ScoringContext;
import io.leavesfly.stock.application.strategy.engine.StrategyPerformanceTracker;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import io.leavesfly.stock.domain.service.TechnicalAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompositeScoringEngine 综合分析评分测试")
class CompositeScoringEngineTest {

    private CompositeScoringEngine engine;
    private TechnicalAnalysisService technicalAnalysisService;

    @BeforeEach
    void setUp() {
        engine = new CompositeScoringEngine(StrategyTestData.loadCatalog(), new StrategyPerformanceTracker());
        technicalAnalysisService = new TechnicalAnalysisService();
    }

    @Test
    @DisplayName("多头排列应命中牛趋势策略")
    void bullishTrendShouldMatchBullTrend() {
        List<StockDailyData> history = buildBullishSeries();
        Map<String, Object> technical = technicalAnalysisService.analyze(history);
        ScoringContext ctx = ScoringContext.of(history, technical, Map.of(), Map.of());

        CompositeScoringResult result = engine.evaluate(ctx);

        assertTrue(result.getMaxWeight() > 0);
        assertTrue(result.getHits().stream()
                .anyMatch(h -> "bull_trend".equals(h.id()) && h.matched()));
        assertTrue(result.getTotalScore() > 0);
    }

    @Test
    @DisplayName("应加载全部 scoring 策略并归一化到 0~100")
    void shouldNormalizeScore() {
        List<StockDailyData> history = buildBullishSeries();
        Map<String, Object> technical = technicalAnalysisService.analyze(history);
        CompositeScoringResult result = engine.evaluate(
                ScoringContext.of(history, technical, Map.of(), Map.of()));

        assertTrue(result.getTotalScore() >= 0 && result.getTotalScore() <= 100);
        assertEquals(result.getEarnedWeight() <= result.getMaxWeight(), true);
    }

    private List<StockDailyData> buildBullishSeries() {
        final double[] price = {80};
        return java.util.stream.IntStream.range(0, 80)
                .mapToObj(i -> {
                    price[0] += (i > 40 ? 0.8 : 0.2);
                    StockDailyData bar = new StockDailyData();
                    bar.setTradeDate(LocalDate.of(2024, 1, 1).plusDays(i));
                    bar.setOpenPrice(price[0] - 0.3);
                    bar.setClosePrice(price[0]);
                    bar.setHighPrice(price[0] + 0.5);
                    bar.setLowPrice(price[0] - 0.5);
                    bar.setVolume(1_000_000L + i * 1000);
                    bar.setChangePct(0.5);
                    return bar;
                })
                .toList();
    }
}
