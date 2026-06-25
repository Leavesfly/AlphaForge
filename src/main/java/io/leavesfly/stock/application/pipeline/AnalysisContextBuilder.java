package io.leavesfly.stock.application.pipeline;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import io.leavesfly.stock.domain.model.enums.MarketType;

import io.leavesfly.stock.application.service.MarketAnalysisService;
import io.leavesfly.stock.application.service.NewsSearchService;
import io.leavesfly.stock.domain.service.TechnicalAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * 分析上下文构建器
 *
 * 负责收集股票的全维度数据并组装为LLM可理解的分析上下文
 */
@Component
public class AnalysisContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(AnalysisContextBuilder.class);
    private final DataFetcherManager dataFetcher;
    private final TechnicalAnalysisService technicalService;
    private final NewsSearchService newsService;
    private final MarketAnalysisService marketService;
    private final AppConfig config;

    public AnalysisContextBuilder(DataFetcherManager dataFetcher, TechnicalAnalysisService technicalService,
                                  NewsSearchService newsService, MarketAnalysisService marketService, AppConfig config) {
        this.dataFetcher = dataFetcher;
        this.technicalService = technicalService;
        this.newsService = newsService;
        this.marketService = marketService;
        this.config = config;
    }

    /**
     * 构建完整分析上下文
     * 包含: 历史数据、实时行情、技术指标、新闻、大盘环境
     */
    public AnalysisContext build(String stockCode) {
        log.info("[{}] 开始构建分析上下文...", stockCode);
        AnalysisContext ctx = new AnalysisContext();
        ctx.setStockCode(stockCode);
        ctx.setMarket(MarketType.detectFromCode(stockCode));
        ctx.setAnalysisDate(LocalDate.now());

        // 1. 获取历史数据
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(config.getHistoryDays());
        List<StockDailyData> historyData = dataFetcher.getHistoryData(stockCode, startDate, endDate);
        ctx.setHistoryData(historyData);

        if (!historyData.isEmpty()) {
            ctx.setStockName(historyData.get(historyData.size() - 1).getStockName());
        }

        // 2. 实时行情
        Map<String, Object> realtimeQuote = dataFetcher.getRealtimeQuote(stockCode);
        ctx.setRealtimeQuote(realtimeQuote);

        // 3. 技术分析
        if (!historyData.isEmpty()) {
            Map<String, Object> technicalResult = technicalService.analyze(historyData);
            ctx.setTechnicalAnalysis(technicalResult);
        }

        // 4. 新闻搜索
        List<Map<String, Object>> news = newsService.searchNews(stockCode, ctx.getStockName());
        ctx.setNews(news);

        // 5. 大盘环境(仅A股)
        if (ctx.getMarket() == MarketType.A) {
            try {
                Map<String, Object> marketOverview = marketService.getMarketOverview();
                ctx.setMarketContext(marketOverview);
            } catch (Exception e) {
                log.debug("获取大盘环境失败: {}", e.getMessage());
            }
        }

        // 6. 股票基本信息
        Map<String, Object> stockInfo = dataFetcher.getStockInfo(stockCode);
        ctx.setStockInfo(stockInfo);

        log.info("[{}] 上下文构建完成: 历史{}条, 新闻{}条", stockCode, historyData.size(), news.size());
        return ctx;
    }

    /**
     * 将上下文格式化为LLM可理解的文本
     */
    public String formatForLlm(AnalysisContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 股票基本信息\n");
        sb.append("- 代码: ").append(ctx.getStockCode()).append("\n");
        sb.append("- 名称: ").append(ctx.getStockName() != null ? ctx.getStockName() : "未知").append("\n");
        sb.append("- 市场: ").append(ctx.getMarket().getName()).append("\n");
        sb.append("- 分析日期: ").append(ctx.getAnalysisDate()).append("\n\n");

        // 实时行情
        if (ctx.getRealtimeQuote() != null && !ctx.getRealtimeQuote().isEmpty()) {
            sb.append("## 实时行情\n");
            ctx.getRealtimeQuote().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }

        // 历史行情(最近20天)
        if (ctx.getHistoryData() != null && !ctx.getHistoryData().isEmpty()) {
            sb.append("## 近期行情(最近20个交易日)\n");
            sb.append("日期 | 开盘 | 最高 | 最低 | 收盘 | 成交量 | 涨跌幅\n");
            List<StockDailyData> recent = ctx.getHistoryData();
            int start = Math.max(0, recent.size() - 20);
            for (int i = start; i < recent.size(); i++) {
                StockDailyData d = recent.get(i);
                sb.append(String.format("%s | %.2f | %.2f | %.2f | %.2f | %d | %.2f%%\n",
                        d.getTradeDate(), safe(d.getOpenPrice()), safe(d.getHighPrice()),
                        safe(d.getLowPrice()), safe(d.getClosePrice()),
                        d.getVolume() != null ? d.getVolume() : 0,
                        safe(d.getChangePct())));
            }
            sb.append("\n");
        }

        // 技术分析
        if (ctx.getTechnicalAnalysis() != null && !ctx.getTechnicalAnalysis().isEmpty()) {
            sb.append("## 技术指标分析\n");
            ctx.getTechnicalAnalysis().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }

        // 新闻
        if (ctx.getNews() != null && !ctx.getNews().isEmpty()) {
            sb.append("## 相关新闻\n");
            for (Map<String, Object> n : ctx.getNews()) {
                sb.append("- ").append(n.get("title")).append("\n");
                Object content = n.get("content");
                if (content != null) {
                    String c = content.toString();
                    sb.append("  ").append(c.length() > 150 ? c.substring(0, 150) + "..." : c).append("\n");
                }
            }
            sb.append("\n");
        }

        // 大盘环境
        if (ctx.getMarketContext() != null && !ctx.getMarketContext().isEmpty()) {
            sb.append("## 大盘环境\n");
            ctx.getMarketContext().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }

        return sb.toString();
    }

    private double safe(Double v) { return v != null ? v : 0.0; }

    /**
     * 分析上下文数据类
     */
    public static class AnalysisContext {
        private String stockCode;
        private String stockName;
        private MarketType market;
        private LocalDate analysisDate;
        private List<StockDailyData> historyData;
        private Map<String, Object> realtimeQuote;
        private Map<String, Object> technicalAnalysis;
        private List<Map<String, Object>> news;
        private Map<String, Object> marketContext;
        private Map<String, Object> stockInfo;

        // Getters & Setters
        public String getStockCode() { return stockCode; }
        public void setStockCode(String stockCode) { this.stockCode = stockCode; }
        public String getStockName() { return stockName; }
        public void setStockName(String stockName) { this.stockName = stockName; }
        public MarketType getMarket() { return market; }
        public void setMarket(MarketType market) { this.market = market; }
        public LocalDate getAnalysisDate() { return analysisDate; }
        public void setAnalysisDate(LocalDate analysisDate) { this.analysisDate = analysisDate; }
        public List<StockDailyData> getHistoryData() { return historyData; }
        public void setHistoryData(List<StockDailyData> historyData) { this.historyData = historyData; }
        public Map<String, Object> getRealtimeQuote() { return realtimeQuote; }
        public void setRealtimeQuote(Map<String, Object> realtimeQuote) { this.realtimeQuote = realtimeQuote; }
        public Map<String, Object> getTechnicalAnalysis() { return technicalAnalysis; }
        public void setTechnicalAnalysis(Map<String, Object> technicalAnalysis) { this.technicalAnalysis = technicalAnalysis; }
        public List<Map<String, Object>> getNews() { return news; }
        public void setNews(List<Map<String, Object>> news) { this.news = news; }
        public Map<String, Object> getMarketContext() { return marketContext; }
        public void setMarketContext(Map<String, Object> marketContext) { this.marketContext = marketContext; }
        public Map<String, Object> getStockInfo() { return stockInfo; }
        public void setStockInfo(Map<String, Object> stockInfo) { this.stockInfo = stockInfo; }
    }
}
