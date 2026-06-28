package io.leavesfly.alphaforge.application.pipeline;

import io.leavesfly.alphaforge.application.pipeline.AnalysisPostProcessor.AnalysisResult;
import io.leavesfly.alphaforge.application.pipeline.AnalysisPostProcessor.TrendAnalysisResult;
import io.leavesfly.alphaforge.application.strategy.StrategyTestData;
import io.leavesfly.alphaforge.application.strategy.engine.CompositeScoringEngine;
import io.leavesfly.alphaforge.application.strategy.engine.StrategyPerformanceTracker;
import io.leavesfly.alphaforge.config.AppConfig;
import io.leavesfly.alphaforge.domain.service.TechnicalAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AnalysisPostProcessor 分析后处理测试")
class AnalysisPostProcessorTest {

    private AnalysisPostProcessor processor;
    private TechnicalAnalysisService technicalAnalysisService;

    @Mock
    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        when(appConfig.getLlmScoreBlendRatio()).thenReturn(0.6);
        when(appConfig.getBuyScoreThreshold()).thenReturn(70);
        when(appConfig.getSellScoreThreshold()).thenReturn(30);
        when(appConfig.isAdaptiveBlendEnabled()).thenReturn(false);
        processor = new AnalysisPostProcessor(
                new CompositeScoringEngine(StrategyTestData.loadCatalog(), new StrategyPerformanceTracker()), appConfig);
        technicalAnalysisService = new TechnicalAnalysisService();
    }

    @Test
    @DisplayName("LLM 无有效分时应用综合策略评分")
    void appliesCompositeScoringWhenLlmScoreMissing() {
        AnalysisResult result = new AnalysisResult();
        result.stockCode = "600519";
        result.score = null;
        result.signal = "neutral";

        var history = io.leavesfly.alphaforge.application.strategy.StrategyTestData.risingBars(80, 80, 0.8);
        Map<String, Object> technical = technicalAnalysisService.analyze(history);
        Map<String, Object> quote = Map.of("price", 100.0, "change_pct", 1.2);

        processor.applyCompositeScoring(result, technical, quote, Map.of(), history);

        assertNotNull(result.score);
        assertEquals("composite_scoring", result.fallbackSource);
        assertNotNull(result.compositeScoring);
        assertTrue(technical.containsKey("composite_scoring"));
    }

    @Test
    @DisplayName("LLM 有分时应与策略分按 6:4 融合")
    void blendsLlmAndStrategyScore() {
        AnalysisResult result = new AnalysisResult();
        result.stockCode = "600519";
        result.score = 80;

        var history = io.leavesfly.alphaforge.application.strategy.StrategyTestData.risingBars(80, 80, 0.8);
        Map<String, Object> technical = technicalAnalysisService.analyze(history);

        processor.applyCompositeScoring(result, technical, Map.of(), Map.of(), history);

        assertNotNull(result.score);
        assertTrue(result.score > 0 && result.score <= 100);
        assertEquals("llm_blend", result.fallbackSource);
    }

    @Test
    @DisplayName("趋势兜底应在无 LLM 分时补全评分与信号")
    void trendFallbackFillsScoreAndSignal() {
        AnalysisResult result = new AnalysisResult();
        result.score = 50;
        result.signal = "neutral";

        TrendAnalysisResult trend = new TrendAnalysisResult();
        trend.score = 75;
        trend.trendLabel = "强势上涨";

        processor.applyTrendFallback(result, trend);

        assertEquals(75, result.score);
        assertEquals("trend_analysis", result.fallbackSource);
        assertEquals("buy", result.signal);
    }

    @Test
    @DisplayName("综合评分后趋势兜底不应覆盖分数")
    void trendFallbackSkipsWhenCompositeApplied() {
        AnalysisResult result = new AnalysisResult();
        result.score = 62;
        result.fallbackSource = "composite_scoring";
        result.signal = "neutral";

        TrendAnalysisResult trend = new TrendAnalysisResult();
        trend.score = 75;

        processor.applyTrendFallback(result, trend);

        assertEquals(62, result.score);
        assertEquals("composite_scoring", result.fallbackSource);
    }

    @Test
    @DisplayName("大盘谨慎时应将 strong_buy 降级为 buy")
    void marketGuardrailDowngradesStrongBuy() {
        AnalysisResult result = new AnalysisResult();
        result.signal = "strong_buy";

        Map<String, Object> market = Map.of("market_sentiment", "谨慎");
        processor.applyMarketContextGuardrail(result, market);

        assertEquals("buy", result.signal);
        assertNotNull(result.riskNote);
        assertTrue(result.riskNote.contains("大盘环境偏弱"));
    }

    @Test
    @DisplayName("normalizeReportLanguage 应将分数限制在 0~100")
    void normalizeClampsScore() {
        AnalysisResult result = new AnalysisResult();
        result.score = 150;
        result.signal = " BUY ";

        processor.normalizeReportLanguage(result);

        assertEquals(100, result.score);
        assertEquals("buy", result.signal);
    }

    @Test
    @DisplayName("完整后处理链应填充价格与摘要字段")
    void fullProcessBackfillsDashboardFields() {
        AnalysisResult result = new AnalysisResult();
        result.stockCode = "600519";
        result.stockName = "贵州茅台";
        result.score = null;
        result.signal = "neutral";

        var history = io.leavesfly.alphaforge.application.strategy.StrategyTestData.risingBars(80, 80, 0.8);
        Map<String, Object> technical = technicalAnalysisService.analyze(history);
        Map<String, Object> quote = new HashMap<>();
        quote.put("current_price", 1680.0);

        TrendAnalysisResult trend = new TrendAnalysisResult();
        trend.score = 65;
        trend.trendLabel = "震荡偏多";

        processor.process(result, trend, technical, quote, Map.of(), history);

        assertNotNull(result.currentPrice);
        assertNotNull(result.summary);
        assertNotNull(result.stopLossPrice);
        assertNotNull(result.targetPrice);
        assertFalse(result.summary.isBlank());
    }
}
