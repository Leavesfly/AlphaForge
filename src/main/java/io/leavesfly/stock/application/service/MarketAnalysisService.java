package io.leavesfly.stock.application.service;

import io.leavesfly.stock.domain.service.TechnicalAnalysisService;

import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 市场分析服务
 * 
 * 对应Python版本的 src/market_analyzer.py
 * 功能: 大盘分析、板块分析、市场情绪判断
 */
@Service
public class MarketAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(MarketAnalysisService.class);
    private final DataFetcherManager dataFetcher;
    private final TechnicalAnalysisService technicalService;

    /** 主要市场指数 */
    private static final Map<String, String> MARKET_INDICES = Map.of(
            "000001", "上证指数",
            "399001", "深证成指",
            "399006", "创业板指",
            "000300", "沪深300",
            "000016", "上证50",
            "000905", "中证500"
    );

    public MarketAnalysisService(DataFetcherManager dataFetcher, TechnicalAnalysisService technicalService) {
        this.dataFetcher = dataFetcher;
        this.technicalService = technicalService;
    }

    /**
     * 大盘复盘分析
     */
    public Map<String, Object> marketReview() {
        Map<String, Object> review = new LinkedHashMap<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        List<Map<String, Object>> indexAnalyses = new ArrayList<>();
        for (Map.Entry<String, String> entry : MARKET_INDICES.entrySet()) {
            try {
                List<StockDailyData> data = dataFetcher.getHistoryData(entry.getKey(), startDate, endDate);
                if (!data.isEmpty()) {
                    Map<String, Object> analysis = new LinkedHashMap<>();
                    analysis.put("code", entry.getKey());
                    analysis.put("name", entry.getValue());
                    
                    StockDailyData latest = data.get(data.size() - 1);
                    analysis.put("close", latest.getClosePrice());
                    analysis.put("change_pct", latest.getChangePct());
                    
                    Map<String, Object> tech = technicalService.analyze(data);
                    analysis.put("trend", tech.get("trend"));
                    analysis.put("score", tech.get("total_score"));
                    indexAnalyses.add(analysis);
                }
            } catch (Exception e) {
                log.error("指数分析失败: {} - {}", entry.getKey(), e.getMessage());
            }
        }

        review.put("indices", indexAnalyses);
        review.put("market_sentiment", assessMarketSentiment(indexAnalyses));
        review.put("analysis_date", endDate.toString());
        return review;
    }

    /**
     * 评估市场情绪
     */
    private String assessMarketSentiment(List<Map<String, Object>> indices) {
        if (indices.isEmpty()) return "neutral";
        long bullish = indices.stream()
                .filter(i -> {
                    Object trend = i.get("trend");
                    return "强势上涨".equals(trend) || "震荡偏多".equals(trend);
                }).count();
        if (bullish >= indices.size() * 0.7) return "乐观";
        if (bullish >= indices.size() * 0.4) return "中性";
        return "谨慎";
    }

    /**
     * 获取市场概况
     */
    public Map<String, Object> getMarketOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : MARKET_INDICES.entrySet()) {
            try {
                Map<String, Object> quote = dataFetcher.getRealtimeQuote(entry.getKey());
                if (!quote.isEmpty()) {
                    quote.put("name", entry.getValue());
                    overview.put(entry.getKey(), quote);
                }
            } catch (Exception e) {
                log.debug("获取指数行情失败: {}", entry.getKey());
            }
        }
        return overview;
    }
}
