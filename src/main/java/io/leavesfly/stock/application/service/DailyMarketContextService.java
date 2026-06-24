package io.leavesfly.stock.application.service;

import io.leavesfly.stock.domain.service.TechnicalAnalysisService;

import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 每日市场上下文服务
 * 对应Python版本的 src/services/daily_market_context.py
 * 提供当日大盘环境数据用于分析决策
 */
@Service
public class DailyMarketContextService {

    private static final Logger log = LoggerFactory.getLogger(DailyMarketContextService.class);
    private final DataFetcherManager dataFetcher;
    private final TechnicalAnalysisService technicalService;

    /** 缓存当日上下文(同一天不重复计算) */
    private Map<String, Object> cachedContext;
    private LocalDate cachedDate;

    public DailyMarketContextService(DataFetcherManager dataFetcher, TechnicalAnalysisService technicalService) {
        this.dataFetcher = dataFetcher;
        this.technicalService = technicalService;
    }

    /**
     * 获取当日市场上下文
     */
    public Map<String, Object> getDailyContext() {
        LocalDate today = LocalDate.now();
        if (cachedContext != null && today.equals(cachedDate)) {
            return cachedContext;
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("date", today.toString());

        // 主要指数分析
        Map<String, Object> indices = new LinkedHashMap<>();
        Map<String, String> indexMap = Map.of(
                "000001", "上证指数", "399001", "深证成指",
                "399006", "创业板指", "000300", "沪深300");

        for (Map.Entry<String, String> entry : indexMap.entrySet()) {
            try {
                Map<String, Object> quote = dataFetcher.getRealtimeQuote(entry.getKey());
                if (!quote.isEmpty()) {
                    quote.put("name", entry.getValue());
                    indices.put(entry.getKey(), quote);
                }
            } catch (Exception ignored) {}
        }
        context.put("indices", indices);

        // 市场情绪判断
        long bullCount = indices.values().stream()
                .filter(v -> v instanceof Map)
                .map(v -> (Map<?, ?>) v)
                .filter(m -> {
                    Object pct = m.get("change_pct");
                    return pct instanceof Number && ((Number) pct).doubleValue() > 0;
                }).count();
        
        String sentiment;
        if (bullCount >= 3) sentiment = "乐观";
        else if (bullCount >= 2) sentiment = "中性";
        else sentiment = "谨慎";
        context.put("market_sentiment", sentiment);
        context.put("bullish_indices", bullCount);

        // 缓存
        cachedContext = context;
        cachedDate = today;
        return context;
    }

    /**
     * 判断当前市场环境是否适合做多
     */
    public boolean isBullishEnvironment() {
        Map<String, Object> ctx = getDailyContext();
        return "乐观".equals(ctx.get("market_sentiment"));
    }

    /**
     * 获取市场风险等级
     */
    public String getMarketRiskLevel() {
        Map<String, Object> ctx = getDailyContext();
        String sentiment = (String) ctx.get("market_sentiment");
        if ("乐观".equals(sentiment)) return "low";
        if ("中性".equals(sentiment)) return "medium";
        return "high";
    }
}
