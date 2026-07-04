package io.leavesfly.alphaforge.application.service.market;

import io.leavesfly.alphaforge.domain.service.TechnicalAnalysisService;

import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.service.port.NotificationPort;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 市场分析服务 — 统一的市场行情、情绪判断和上下文服务
 *
 * 功能: 大盘分析、板块分析、市场情绪判断、每日市场上下文（含缓存）、市场信号灯
 * 统一市场行情、情绪判断和上下文服务，消除重复的指数行情获取和情绪计算。
 */
@Service
public class MarketAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(MarketAnalysisService.class);
    private final MarketDataPort dataFetcher;
    private final TechnicalAnalysisService technicalService;
    private final NotificationPort notificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NewsSearchService newsSearchService;

    /** 缓存当日上下文(同一天不重复计算) */
    private Map<String, Object> cachedContext;
    private LocalDate cachedDate;

    /** 行情简览缓存: 市场代码 -> {data, timestamp}，TTL 10分钟 */
    private final Map<String, long[]> briefingCacheTime = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> briefingCacheData = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long BRIEFING_CACHE_TTL_MS = 600_000L; // 10分钟

    public MarketAnalysisService(MarketDataPort dataFetcher, TechnicalAnalysisService technicalService,
                                 @org.springframework.beans.factory.annotation.Autowired(required = false)
                                 NotificationPort notificationService) {
        this.dataFetcher = dataFetcher;
        this.technicalService = technicalService;
        this.notificationService = notificationService;
    }

    /**
     * 大盘复盘分析
     */
    public Map<String, Object> marketReview() {
        Map<String, Object> review = new LinkedHashMap<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        List<Map<String, Object>> indexAnalyses = new ArrayList<>();
        for (Map.Entry<String, String> entry : MarketConstants.MARKET_INDICES.entrySet()) {
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
        review.put("market_sentiment", MarketConstants.assessSentimentFromTrends(indexAnalyses));
        review.put("analysis_date", endDate.toString());
        return review;
    }

    /**
     * 获取市场概况
     */
    public Map<String, Object> getMarketOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : MarketConstants.MARKET_INDICES.entrySet()) {
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

    /**
     * 获取单只股票实时行情
     */
    public Map<String, Object> getQuote(String stockCode) {
        return dataFetcher.getRealtimeQuote(stockCode);
    }

    // ==================== 每日市场上下文 ====================

    /**
     * 获取当日市场上下文（含缓存）
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
        for (Map.Entry<String, String> entry : MarketConstants.CORE_INDICES.entrySet()) {
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

        String sentiment = MarketConstants.assessSentiment(bullCount, indices.size());
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

    // ==================== 市场信号灯 ====================

    /**
     * 获取市场信号灯状态（红/黄/绿）
     */
    public Map<String, Object> getMarketLight() {
        Map<String, Object> light = new LinkedHashMap<>();
        List<StockDailyData> shIndex = dataFetcher.getHistoryData("000001", LocalDate.now().minusDays(20), LocalDate.now());
        if (shIndex.isEmpty()) {
            light.put("color", "gray");
            light.put("reason", "数据不可用");
            return light;
        }
        StockDailyData latest = shIndex.get(shIndex.size() - 1);
        double changePct = latest.getChangePct() != null ? latest.getChangePct() : 0;
        double ma5 = avgClose(shIndex, 5), ma20 = avgClose(shIndex, 20);

        String color, reason;
        if (changePct < -3 || (ma5 < ma20 && changePct < -1)) {
            color = "red"; reason = "大盘大幅下跌，高风险";
        } else if (changePct < -1 || ma5 < ma20) {
            color = "yellow"; reason = "市场偏弱，建议谨慎";
        } else {
            color = "green"; reason = "市场正常，可正常操作";
        }
        light.put("color", color);
        light.put("reason", reason);
        light.put("index_change", changePct);
        light.put("ma5_above_ma20", ma5 > ma20);
        return light;
    }

    /** 信号灯变化时发送告警 */
    public void checkAndAlert(String previousColor) {
        if (notificationService == null) return;
        Map<String, Object> current = getMarketLight();
        String newColor = (String) current.get("color");
        if (!newColor.equals(previousColor) && "red".equals(newColor)) {
            notificationService.sendMessage("🔴 市场信号灯: 红灯",
                    "大盘进入高风险区域: " + current.get("reason"));
        }
    }

    private double avgClose(List<StockDailyData> data, int period) {
        int start = Math.max(0, data.size() - period);
        double sum = 0;
        for (int i = start; i < data.size(); i++) {
            sum += data.get(i).getClosePrice();
        }
        return sum / (data.size() - start);
    }

    // ==================== 行情简览 ====================

    /**
     * 获取指定市场的行情简览（指数 + 热门股 + 新闻）
     *
     * @param marketType 市场类型（A/HK/US）
     * @return 行情简览数据
     */
    public Map<String, Object> getMarketBriefing(MarketType marketType) {
        // 检查缓存
        String cacheKey = marketType.getCode();
        long[] ts = briefingCacheTime.get(cacheKey);
        if (ts != null && System.currentTimeMillis() - ts[0] < BRIEFING_CACHE_TTL_MS) {
            Map<String, Object> cached = briefingCacheData.get(cacheKey);
            if (cached != null) {
                log.debug("行情简览缓存命中: {}", cacheKey);
                return cached;
            }
        }

        long startTime = System.currentTimeMillis();
        Map<String, Object> briefing = new LinkedHashMap<>();
        briefing.put("market", marketType.getName());
        briefing.put("market_code", marketType.getCode());

        // 1. 指数行情 + 近5日迷你走势数据
        List<Map<String, Object>> indices = new ArrayList<>();
        Map<String, String> indexDefs = MarketConstants.getIndices(marketType);
        for (Map.Entry<String, String> entry : indexDefs.entrySet()) {
            try {
                Map<String, Object> quote = dataFetcher.getRealtimeQuote(entry.getKey(), marketType);
                if (quote != null && !quote.isEmpty()) {
                    quote.put("name", entry.getValue());
                    quote.put("code", entry.getKey());
                    quote.put("sparkline", fetchSparkline(entry.getKey(), marketType));
                    indices.add(quote);
                }
            } catch (Exception e) {
                log.debug("获取指数行情失败: {} - {}", entry.getKey(), e.getMessage());
            }
        }
        briefing.put("indices", indices);

        // 2. 热门股票行情（批量获取，1 次 API 代替 N 次逐个调用）
        List<Map<String, Object>> hotStocks = new ArrayList<>();
        Map<String, String> stockDefs = MarketConstants.getHotStocks(marketType);
        List<String> stockCodes = new ArrayList<>(stockDefs.keySet());
        Map<String, Map<String, Object>> batchQuotes = dataFetcher.getBatchRealtimeQuotes(stockCodes);
        for (Map.Entry<String, String> entry : stockDefs.entrySet()) {
            String code = entry.getKey();
            Map<String, Object> quote = batchQuotes.get(code);
            if (quote == null || quote.isEmpty()) {
                // 批量未命中，降级逐个获取
                quote = dataFetcher.getRealtimeQuote(code);
            }
            if (quote != null && !quote.isEmpty()) {
                quote.put("name", entry.getValue());
                quote.put("code", code);
                quote.put("sparkline", fetchSparkline(code, marketType));
                hotStocks.add(quote);
            }
        }
        briefing.put("hot_stocks", hotStocks);

        // 3. 市场新闻
        List<Map<String, Object>> news = new ArrayList<>();
        if (newsSearchService != null) {
            try {
                news = newsSearchService.searchNews(MarketConstants.getNewsKeyword(marketType), "");
            } catch (Exception e) {
                log.debug("获取市场新闻失败: {}", e.getMessage());
            }
        }
        briefing.put("news", news);

        // 4. 板块排行（仅 A 股支持）
        if (marketType == MarketType.A) {
            try {
                briefing.put("sectors", fetchSectorRanking());
            } catch (Exception e) {
                log.debug("获取板块排行失败: {}", e.getMessage());
            }
        }

        // 5. 市场温度（涨跌统计，基于指数涨跌）
        briefing.put("temperature", computeTemperature(indices));

        // 写入缓存
        briefingCacheData.put(cacheKey, briefing);
        briefingCacheTime.put(cacheKey, new long[]{System.currentTimeMillis()});

        log.info("行情简览生成完成: {} 耗时 {}ms (指数:{}, 热门股:{}, 新闻:{})",
                cacheKey, System.currentTimeMillis() - startTime,
                indices.size(), hotStocks.size(), news.size());

        return briefing;
    }

    /**
     * 获取近5日收盘价数组，用于前端迷你走势图
     * 使用显式指定市场类型路由，支持 ^HSI/^DJI 等 Yahoo 指数代码
     */
    private List<Double> fetchSparkline(String stockCode, MarketType marketType) {
        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(10); // 多取几天以防非交易日
            List<StockDailyData> data = dataFetcher.getHistoryData(stockCode, start, end, marketType);
            List<Double> closes = new ArrayList<>();
            for (StockDailyData d : data) {
                if (d.getClosePrice() != null) closes.add(d.getClosePrice());
            }
            // 只取最近5个
            if (closes.size() > 5) closes = closes.subList(closes.size() - 5, closes.size());
            return closes;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 获取行业板块排行（仅 A 股）
     */
    private List<Map<String, Object>> fetchSectorRanking() {
        List<Map<String, Object>> ranking = dataFetcher.getIndustryRanking();
        // 只取前10
        if (ranking.size() > 10) ranking = ranking.subList(0, 10);
        return ranking;
    }

    /**
     * 计算市场温度（0-100，50 为中性）
     */
    private Map<String, Object> computeTemperature(List<Map<String, Object>> indices) {
        Map<String, Object> temp = new LinkedHashMap<>();
        if (indices.isEmpty()) {
            temp.put("value", 50);
            temp.put("label", "中性");
            return temp;
        }
        long upCount = indices.stream()
                .filter(i -> {
                    Object pct = i.get("change_pct");
                    return pct instanceof Number && ((Number) pct).doubleValue() > 0;
                }).count();
        int value = 50 + (int) ((upCount - indices.size() / 2.0) / indices.size() * 50);
        value = Math.max(0, Math.min(100, value));
        temp.put("value", value);
        temp.put("label", value >= 70 ? "偏多" : value >= 40 ? "中性" : "偏空");
        temp.put("up_count", upCount);
        temp.put("down_count", indices.size() - upCount);
        return temp;
    }
}
