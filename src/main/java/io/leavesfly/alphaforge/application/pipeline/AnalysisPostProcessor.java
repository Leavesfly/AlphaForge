package io.leavesfly.alphaforge.application.pipeline;

import io.leavesfly.alphaforge.config.ScoringConfig;
import io.leavesfly.alphaforge.domain.service.port.FactorLibrary;
import io.leavesfly.alphaforge.application.strategy.engine.AdaptiveScoreBlender;
import io.leavesfly.alphaforge.application.strategy.engine.CompositeScoringEngine;
import io.leavesfly.alphaforge.application.strategy.engine.CompositeScoringResult;
import io.leavesfly.alphaforge.application.strategy.engine.ScoringContext;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisResult;
import io.leavesfly.alphaforge.domain.model.entity.analysis.TrendAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 分析结果后处理器
 * 
 * 从 StockAnalysisPipeline 提取的后处理逻辑，负责：
 * - Fallback 兜底（当 LLM 未给出明确结果时用技术分析补全）
 * - 决策动作刷新
 * - 大盘环境护栏
 * - 价格位置填充
 * - Dashboard 字段回填
 * - 报告语言标准化
 */
@Component
public class AnalysisPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPostProcessor.class);
    private final CompositeScoringEngine compositeScoringEngine;
    private final ScoringConfig scoringConfig;
    private final AdaptiveScoreBlender adaptiveBlender;
    private final FactorLibrary factorLibrary;

    @org.springframework.beans.factory.annotation.Autowired
    public AnalysisPostProcessor(CompositeScoringEngine compositeScoringEngine, ScoringConfig scoringConfig,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false) FactorLibrary factorLibrary) {
        this.compositeScoringEngine = compositeScoringEngine;
        this.scoringConfig = scoringConfig;
        this.factorLibrary = factorLibrary;
        this.adaptiveBlender = new AdaptiveScoreBlender(
                scoringConfig.getAdaptiveBlendBaseRatio(),
                scoringConfig.getAdaptiveStrategyConfidenceImpact(),
                scoringConfig.getAdaptiveMarketClarityImpact(),
                scoringConfig.getAdaptiveLlmConfidenceImpact(),
                scoringConfig.getAdaptiveBlendMinRatio(),
                scoringConfig.getAdaptiveBlendMaxRatio());
    }

    /**
     * 执行完整的后处理链
     */
    public void process(AnalysisResult result, TrendAnalysisResult trend,
                        Map<String, Object> technicalResult, Map<String, Object> realtimeQuote,
                        Map<String, Object> marketContext, List<StockDailyData> historyData) {
        applyCompositeScoring(result, technicalResult, realtimeQuote, marketContext, historyData);
        applyTrendFallback(result, trend);
        refreshDecisionAction(result, technicalResult, trend);
        applyMarketContextGuardrail(result, marketContext);
        fillPricePosition(result, realtimeQuote, historyData);
        backfillDashboardFields(result, trend, realtimeQuote);
        normalizeReportLanguage(result);
    }

    /**
     * YAML 多策略加权评分 — 用 CompositeScoringEngine 产出 0~100 综合分。
     */
    public void applyCompositeScoring(AnalysisResult result, Map<String, Object> technicalResult,
                                      Map<String, Object> realtimeQuote, Map<String, Object> marketContext,
                                      List<StockDailyData> historyData) {
        if (historyData == null || historyData.isEmpty()) {
            return;
        }
        ScoringContext ctx = ScoringContext.of(historyData, technicalResult, realtimeQuote, marketContext);
        CompositeScoringResult scoring = compositeScoringEngine.evaluate(ctx);
        result.compositeScoring = scoring.toMap();

        if (technicalResult != null) {
            technicalResult.put("composite_scoring", scoring.toMap());
        }

        Integer llmScore = result.score;
        if (scoringConfig.isAdaptiveBlendEnabled()) {
            AdaptiveScoreBlender.BlendResult blend = adaptiveBlender.blend(
                    llmScore, scoring.getTotalScore(),
                    scoring.getEarnedWeight(), scoring.getMaxWeight(),
                    marketContext, result.confidence);
            result.score = blend.score();
            result.fallbackSource = blend.source();
        } else {
            double llmRatio = scoringConfig.getLlmScoreBlendRatio();
            if (llmScore == null || llmScore == 50) {
                result.score = scoring.getTotalScore();
                result.fallbackSource = "composite_scoring";
            } else {
                result.score = (int) Math.round(llmScore * llmRatio + scoring.getTotalScore() * (1 - llmRatio));
                result.fallbackSource = result.fallbackSource != null ? result.fallbackSource : "llm_blend";
            }
        }

        int buyThreshold = scoringConfig.getBuyScoreThreshold();
        int sellThreshold = scoringConfig.getSellScoreThreshold();
        if (result.signal == null || "neutral".equals(result.signal)) {
            if (result.score >= buyThreshold) result.signal = "buy";
            else if (result.score <= sellThreshold) result.signal = "sell";
        }

        // 如果因子库可用，计算关键因子并注入到结果中
        if (factorLibrary != null && historyData != null && !historyData.isEmpty()) {
            try {
                List<String> factorNames = List.of("momentum_5d", "momentum_20d", "rsi_14", "volatility_20d");
                Map<String, Double> factors = factorLibrary.calculateBatch(factorNames, historyData);
                result.compositeScoring.put("factors", factors);
                log.debug("[{}] 因子计算完成: {}", result.stockCode, factors);
            } catch (Exception e) {
                log.debug("[{}] 因子计算失败: {}", result.stockCode, e.getMessage());
            }
        }

        log.debug("[{}] 综合策略评分: {} (命中权重 {}/{})",
                result.stockCode, scoring.getTotalScore(), scoring.getEarnedWeight(), scoring.getMaxWeight());
    }

    /**
     * 趋势分析Fallback - 当LLM未能给出明确信号时用技术分析兜底
     */
    public void applyTrendFallback(AnalysisResult result, TrendAnalysisResult trend) {
        if (trend == null) return;
        // 已由综合评分或 LLM 混合给出分数时，不再用趋势覆盖
        if ("composite_scoring".equals(result.fallbackSource) || "llm_blend".equals(result.fallbackSource)) {
            if (result.trendLabel == null) {
                result.trendLabel = trend.trendLabel;
            }
            return;
        }

        if (result.score == null || result.score == 50) {
            result.score = trend.score;
            result.fallbackSource = "trend_analysis";
        }

        if (result.signal == null || "neutral".equals(result.signal)) {
            int buyThreshold = scoringConfig.getBuyScoreThreshold();
            int sellThreshold = scoringConfig.getSellScoreThreshold();
            if (trend.score >= buyThreshold) result.signal = "buy";
            else if (trend.score <= sellThreshold) result.signal = "sell";
        }

        if (result.trendLabel == null) {
            result.trendLabel = trend.trendLabel;
        }
    }

    /**
     * 决策动作刷新
     */
    public void refreshDecisionAction(AnalysisResult result, Map<String, Object> tech, TrendAnalysisResult trend) {
        if (result.operationAdvice == null || result.operationAdvice.isEmpty()) {
            if (trend != null && trend.score >= 75) {
                result.operationAdvice = "技术面走强，可关注买入机会";
            } else if (trend != null && trend.score <= 25) {
                result.operationAdvice = "技术面走弱，建议减仓回避";
            } else {
                result.operationAdvice = "走势不明朗，建议观望";
            }
        }
    }

    /**
     * 应用大盘环境护栏
     */
    public void applyMarketContextGuardrail(AnalysisResult result, Map<String, Object> marketContext) {
        if (marketContext == null) return;
        String sentiment = (String) marketContext.get("market_sentiment");
        if ("谨慎".equals(sentiment) && "strong_buy".equals(result.signal)) {
            result.signal = "buy";
            result.riskNote = (result.riskNote != null ? result.riskNote + "; " : "") + "大盘环境偏弱，信号已降级";
            log.debug("[{}] 大盘护栏: strong_buy → buy", result.stockCode);
        }
    }

    /**
     * 价格位置填充
     */
    public void fillPricePosition(AnalysisResult result, Map<String, Object> realtime, List<StockDailyData> history) {
        if (result.currentPrice == null && realtime != null) {
            Object price = realtime.get("current_price");
            if (price instanceof Number) result.currentPrice = ((Number) price).doubleValue();
        }
        if (history != null && !history.isEmpty()) {
            double high52w = history.stream().mapToDouble(d -> d.getHighPrice() != null ? d.getHighPrice() : 0).max().orElse(0);
            double low52w = history.stream().mapToDouble(d -> d.getLowPrice() != null ? d.getLowPrice() : Double.MAX_VALUE).min().orElse(0);
            if (high52w > low52w && result.currentPrice != null) {
                result.pricePosition = (result.currentPrice - low52w) / (high52w - low52w) * 100;
            }
        }
    }

    /**
     * Dashboard字段回填
     */
    public void backfillDashboardFields(AnalysisResult result, TrendAnalysisResult trend, Map<String, Object> realtime) {
        if (result.confidence == null) {
            result.confidence = result.score != null && result.score != 50 ? "中等" : "低";
        }
        if (result.stopLossPrice == null && result.currentPrice != null) {
            double stopRatio = "buy".equals(result.signal) || "strong_buy".equals(result.signal) ? 0.92 : 0.95;
            result.stopLossPrice = result.currentPrice * stopRatio;
        }
        if (result.targetPrice == null && result.currentPrice != null) {
            double targetRatio = "buy".equals(result.signal) || "strong_buy".equals(result.signal) ? 1.15 : 1.08;
            result.targetPrice = result.currentPrice * targetRatio;
        }
        if (result.summary == null || result.summary.isEmpty()) {
            result.summary = buildSummaryFallback(result);
        }
    }

    /**
     * 报告语言标准化
     */
    public void normalizeReportLanguage(AnalysisResult result) {
        if (result.signal != null) {
            result.signal = result.signal.toLowerCase().trim();
        }
        if (result.score != null) {
            result.score = Math.max(0, Math.min(100, result.score));
        }
    }

    private String buildSummaryFallback(AnalysisResult result) {
        return String.format("%s 综合评分%d/100，信号: %s",
                result.stockName != null ? result.stockName : result.stockCode,
                result.score != null ? result.score : 50,
                result.signal != null ? result.signal : "中性");
    }

    // ========== 共享数据类已提取到 domain 层 ==========
    // AnalysisResult → io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisResult
    // TrendAnalysisResult → io.leavesfly.alphaforge.domain.model.entity.analysis.TrendAnalysisResult
}
