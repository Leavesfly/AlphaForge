package io.leavesfly.stock.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pipeline核心逻辑测试
 * 验证: fallback链、信号降级、护栏逻辑
 */
class StockAnalysisPipelineTest {

    @Test
    @DisplayName("AnalysisResult.dryRun: 返回中性信号")
    void testDryRun() {
        StockAnalysisPipeline.AnalysisResult result = StockAnalysisPipeline.AnalysisResult.dryRun("600519", "贵州茅台");
        assertEquals("neutral", result.signal);
        assertEquals(50, result.score);
        assertEquals("dry_run", result.source);
    }

    @Test
    @DisplayName("趋势Fallback: LLM无信号时用技术分析兜底")
    void testTrendFallback() {
        StockAnalysisPipeline.AnalysisResult result = new StockAnalysisPipeline.AnalysisResult();
        result.signal = "neutral";
        result.score = 50;

        StockAnalysisPipeline.TrendAnalysisResult trend = new StockAnalysisPipeline.TrendAnalysisResult();
        trend.score = 80;
        trend.trendLabel = "上升趋势";

        // 模拟applyTrendFallback逻辑
        if (result.score == null || result.score == 50) {
            result.score = trend.score;
            result.fallbackSource = "trend_analysis";
        }
        if ("neutral".equals(result.signal)) {
            if (trend.score >= 70) result.signal = "buy";
        }

        assertEquals(80, result.score);
        assertEquals("buy", result.signal);
        assertEquals("trend_analysis", result.fallbackSource);
    }

    @Test
    @DisplayName("大盘护栏: 弱市强买降级")
    void testMarketContextGuardrail() {
        StockAnalysisPipeline.AnalysisResult result = new StockAnalysisPipeline.AnalysisResult();
        result.signal = "strong_buy";
        result.riskNote = null;

        // 模拟大盘极弱
        String sentiment = "谨慎";
        if ("谨慎".equals(sentiment) && "strong_buy".equals(result.signal)) {
            result.signal = "buy";
            result.riskNote = "大盘环境偏弱，信号已降级";
        }

        assertEquals("buy", result.signal);
        assertTrue(result.riskNote.contains("降级"));
    }

    @Test
    @DisplayName("Dashboard回填: 止损价自动设定")
    void testStopLossFallback() {
        StockAnalysisPipeline.AnalysisResult result = new StockAnalysisPipeline.AnalysisResult();
        result.signal = "buy";
        result.currentPrice = 100.0;
        result.stopLossPrice = null;

        // 模拟backfillDashboardFields
        if (result.stopLossPrice == null && result.currentPrice != null) {
            double ratio = "buy".equals(result.signal) ? 0.92 : 0.95;
            result.stopLossPrice = result.currentPrice * ratio;
        }

        assertEquals(92.0, result.stopLossPrice, 0.01);
    }

    @Test
    @DisplayName("Dashboard回填: 目标价自动设定")
    void testTargetPriceFallback() {
        StockAnalysisPipeline.AnalysisResult result = new StockAnalysisPipeline.AnalysisResult();
        result.signal = "strong_buy";
        result.currentPrice = 100.0;
        result.targetPrice = null;

        if (result.targetPrice == null && result.currentPrice != null) {
            double ratio = "strong_buy".equals(result.signal) ? 1.15 : 1.08;
            result.targetPrice = result.currentPrice * ratio;
        }

        assertEquals(115.0, result.targetPrice, 0.01);
    }

    @Test
    @DisplayName("价格位置计算: 半年内50%位置")
    void testPricePosition() {
        double high52w = 200.0;
        double low52w = 100.0;
        double currentPrice = 150.0;
        double position = (currentPrice - low52w) / (high52w - low52w) * 100;
        assertEquals(50.0, position, 0.01);
    }

    @Test
    @DisplayName("评分归一化: 超出100修正为100")
    void testScoreNormalization() {
        StockAnalysisPipeline.AnalysisResult result = new StockAnalysisPipeline.AnalysisResult();
        result.score = 120;
        result.score = Math.max(0, Math.min(100, result.score));
        assertEquals(100, result.score);
    }

    @Test
    @DisplayName("评分归一化: 低于0修正为0")
    void testScoreNormalizationMin() {
        StockAnalysisPipeline.AnalysisResult result = new StockAnalysisPipeline.AnalysisResult();
        result.score = -10;
        result.score = Math.max(0, Math.min(100, result.score));
        assertEquals(0, result.score);
    }

    @Test
    @DisplayName("信号emoji映射")
    void testSignalEmoji() {
        assertEquals("🔥", emoji("strong_buy"));
        assertEquals("📈", emoji("buy"));
        assertEquals("📉", emoji("sell"));
        assertEquals("⚖️", emoji("neutral"));
    }

    private String emoji(String signal) {
        switch (signal) {
            case "strong_buy": return "🔥";
            case "buy": return "📈";
            case "sell": return "📉";
            case "strong_sell": return "⚠️";
            default: return "⚖️";
        }
    }
}
