package io.leavesfly.alphaforge.application.pipeline;

import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import io.leavesfly.alphaforge.application.service.market.NewsSearchService;
import io.leavesfly.alphaforge.application.prompt.PromptManager;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import io.leavesfly.alphaforge.domain.service.TechnicalIndicatorCalculator;
import io.leavesfly.alphaforge.util.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * 分析上下文增强器
 *
 * 从 StockAnalysisPipeline 提取的上下文增强逻辑，负责：
 * - 实时数据注入历史
 * - 板块/情报/大盘环境注入
 * - 量价关系描述
 * - 均线状态计算
 * - 智能情报收集（原 IntelligenceService 逻辑，合并消除薄服务层）
 */
@Component
public class AnalysisContextEnhancer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisContextEnhancer.class);
    private static final String DEFAULT_INTELLIGENCE_PROMPT =
            "你是一位市场情报分析师，请分析以下新闻对股价的潜在影响，给出情绪判断和影响程度评估。";

    private final MarketDataPort dataFetcherManager;
    private final NewsSearchService newsService;
    private final LlmPort llmService;
    private final PromptManager promptManager;
    private final TechnicalIndicatorCalculator indicatorCalculator;

    public AnalysisContextEnhancer(MarketDataPort dataFetcherManager,
                                     NewsSearchService newsService, LlmPort llmService,
                                     PromptManager promptManager,
                                     TechnicalIndicatorCalculator indicatorCalculator) {
        this.dataFetcherManager = dataFetcherManager;
        this.newsService = newsService;
        this.llmService = llmService;
        this.promptManager = promptManager;
        this.indicatorCalculator = indicatorCalculator;
    }

    /**
     * 增强分析上下文 - 注入板块、情报、大盘环境等
     */
    public Map<String, Object> enhance(String stockCode, String stockName, MarketType market,
                                        List<StockDailyData> historyData, Map<String, Object> realtimeQuote,
                                        Map<String, Object> technicalResult, List<Map<String, Object>> news,
                                        Map<String, Object> marketContext) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("stock_code", stockCode);
        context.put("stock_name", stockName);
        context.put("market", market.getCode());
        context.put("analysis_date", LocalDate.now().toString());
        context.put("history_data", formatHistoryForLlm(historyData));
        context.put("realtime_quote", realtimeQuote);
        context.put("technical_analysis", technicalResult);
        context.put("news", news);

        // 注入大盘环境
        if (marketContext != null) {
            context.put("market_context", marketContext);
            context.put("market_sentiment", marketContext.get("market_sentiment"));
        }

        // 注入所属板块
        try {
            List<String> boards = dataFetcherManager.getStockBoards(stockCode);
            if (boards != null && !boards.isEmpty()) {
                context.put("belong_boards", boards);
            }
        } catch (Exception e) {
            log.debug("[{}] 板块获取失败: {}", stockCode, e.getMessage());
        }

        // 量价关系描述
        if (!historyData.isEmpty()) {
            context.put("volume_desc", describeVolumeRatio(historyData));
            StockDailyData latest = historyData.get(historyData.size() - 1);
            if (latest.getClosePrice() != null && latest.getClosePrice() > 0) {
                context.put("ma_status", computeMaStatus(historyData));
            }
        }

        return context;
    }

    /**
     * 实时数据注入历史（最后一条增强）
     */
    public void augmentHistoricalWithRealtime(List<StockDailyData> historyData, Map<String, Object> realtime) {
        if (realtime == null || realtime.isEmpty() || historyData.isEmpty()) return;
        StockDailyData latest = historyData.get(historyData.size() - 1);
        LocalDate today = LocalDate.now();

        if (latest.getTradeDate() != null && latest.getTradeDate().isBefore(today)) {
            Object price = realtime.get("current_price");
            if (price instanceof Number) {
                StockDailyData todayData = new StockDailyData();
                todayData.setStockCode(latest.getStockCode());
                todayData.setStockName(latest.getStockName());
                todayData.setTradeDate(today);
                todayData.setClosePrice(((Number) price).doubleValue());
                todayData.setOpenPrice(realtime.containsKey("open_price") ? ((Number) realtime.get("open_price")).doubleValue() : todayData.getClosePrice());
                todayData.setHighPrice(realtime.containsKey("high_price") ? ((Number) realtime.get("high_price")).doubleValue() : todayData.getClosePrice());
                todayData.setLowPrice(realtime.containsKey("low_price") ? ((Number) realtime.get("low_price")).doubleValue() : todayData.getClosePrice());
                todayData.setVolume(realtime.containsKey("volume") ? ((Number) realtime.get("volume")).longValue() : 0L);
                Object pct = realtime.get("change_pct");
                if (pct instanceof Number) todayData.setChangePct(((Number) pct).doubleValue());
                todayData.setDataSource("realtime");
                historyData.add(todayData);
            }
        }
    }

    /**
     * 加载智能情报（原 IntelligenceService.getIntelligence 逻辑）
     */
    public Map<String, Object> loadPersistedIntelligence(String stockCode, MarketType market) {
        try {
            return getIntelligence(stockCode, stockCode);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取股票智能情报摘要
     */
    private Map<String, Object> getIntelligence(String stockCode, String stockName) {
        Map<String, Object> intel = new LinkedHashMap<>();
        intel.put("stock_code", stockCode);
        intel.put("stock_name", stockName);

        List<Map<String, Object>> news = newsService.searchNews(stockCode, stockName);
        intel.put("news_count", news.size());
        intel.put("news", news);

        if (!news.isEmpty()) {
            String newsText = formatNewsForAnalysis(news);
            String systemPrompt = promptManager.getTemplateOrDefault("intelligence_system", DEFAULT_INTELLIGENCE_PROMPT);
            String analysis = llmService.chat(systemPrompt,
                    "股票: " + stockName + "(" + stockCode + ")\n\n相关新闻:\n" + newsText);
            intel.put("analysis", analysis);
            intel.put("sentiment", extractSentiment(analysis));
        } else {
            intel.put("analysis", "暂无相关情报");
            intel.put("sentiment", "neutral");
        }
        return intel;
    }

    private String formatNewsForAnalysis(List<Map<String, Object>> news) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(news.size(), 5); i++) {
            Map<String, Object> n = news.get(i);
            sb.append(i + 1).append(". ").append(n.get("title")).append("\n");
            Object content = n.get("content");
            if (content != null) {
                sb.append("   ").append(CommonUtils.truncate(content.toString(), 200)).append("\n");
            }
        }
        return sb.toString();
    }

    private String extractSentiment(String analysis) {
        String lower = analysis.toLowerCase();
        if (lower.contains("正面") || lower.contains("利好") || lower.contains("积极")) return "positive";
        if (lower.contains("负面") || lower.contains("利空") || lower.contains("消极")) return "negative";
        return "neutral";
    }

    // ========== 辅助方法 ==========

    public String formatHistoryForLlm(List<StockDailyData> historyData) {
        StringBuilder sb = new StringBuilder("日期 | 开盘 | 最高 | 最低 | 收盘 | 成交量 | 涨跌幅\n");
        int start = Math.max(0, historyData.size() - 20);
        for (int i = start; i < historyData.size(); i++) {
            StockDailyData d = historyData.get(i);
            sb.append(String.format("%s | %.2f | %.2f | %.2f | %.2f | %d | %.2f%%\n",
                    d.getTradeDate(), safe(d.getOpenPrice()), safe(d.getHighPrice()),
                    safe(d.getLowPrice()), safe(d.getClosePrice()),
                    d.getVolume() != null ? d.getVolume() : 0, safe(d.getChangePct())));
        }
        return sb.toString();
    }

    private String describeVolumeRatio(List<StockDailyData> data) {
        if (data.size() < 6) return "数据不足";
        long todayVol = data.get(data.size() - 1).getVolume() != null ? data.get(data.size() - 1).getVolume() : 0;
        long avg5Vol = 0;
        for (int i = data.size() - 6; i < data.size() - 1; i++) avg5Vol += data.get(i).getVolume() != null ? data.get(i).getVolume() : 0;
        avg5Vol /= 5;
        if (avg5Vol == 0) return "无量";
        double ratio = (double) todayVol / avg5Vol;
        if (ratio > 3) return "极度放量(" + String.format("%.1f", ratio) + "倍)";
        if (ratio > 2) return "显著放量(" + String.format("%.1f", ratio) + "倍)";
        if (ratio > 1.5) return "温和放量";
        if (ratio > 0.7) return "量能平稳";
        return "明显缩量(" + String.format("%.1f", ratio) + "倍)";
    }

    private String computeMaStatus(List<StockDailyData> data) {
        if (data.size() < 20) return "数据不足";
        double[] closes = data.stream().mapToDouble(StockDailyData::getClosePrice).toArray();
        double close = closes[closes.length - 1];
        double ma5 = indicatorCalculator.sma(closes, 5);
        double ma10 = indicatorCalculator.sma(closes, 10);
        double ma20 = indicatorCalculator.sma(closes, 20);
        if (close > ma5 && ma5 > ma10 && ma10 > ma20) return "多头排列";
        if (close < ma5 && ma5 < ma10 && ma10 < ma20) return "空头排列";
        return "均线交织";
    }

    private double avgClose(List<StockDailyData> data, int period) {
        int start = Math.max(0, data.size() - period);
        double sum = 0; int count = 0;
        for (int i = start; i < data.size(); i++) { sum += data.get(i).getClosePrice(); count++; }
        return count > 0 ? sum / count : 0;
    }

    private double safe(Double v) { return v != null ? v : 0.0; }
}
