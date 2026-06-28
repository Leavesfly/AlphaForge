package io.leavesfly.stock.application.pipeline;

import io.leavesfly.stock.domain.service.port.MarketDataPort;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import io.leavesfly.stock.domain.model.enums.MarketType;
import io.leavesfly.stock.application.service.market.IntelligenceService;
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
 * - 持久化情报加载
 */
@Component
public class AnalysisContextEnhancer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisContextEnhancer.class);
    private final MarketDataPort dataFetcherManager;
    private final IntelligenceService intelligenceService;

    public AnalysisContextEnhancer(MarketDataPort dataFetcherManager, IntelligenceService intelligenceService) {
        this.dataFetcherManager = dataFetcherManager;
        this.intelligenceService = intelligenceService;
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
     * 加载持久化情报
     */
    public Map<String, Object> loadPersistedIntelligence(String stockCode, MarketType market) {
        try {
            return intelligenceService.getIntelligence(stockCode, stockCode);
        } catch (Exception e) {
            return null;
        }
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
        double close = data.get(data.size() - 1).getClosePrice();
        double ma5 = avgClose(data, 5);
        double ma10 = avgClose(data, 10);
        double ma20 = avgClose(data, 20);
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
