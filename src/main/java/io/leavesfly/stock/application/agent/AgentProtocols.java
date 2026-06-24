package io.leavesfly.stock.application.agent;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Agent协议定义
 * 
 * 对应Python版本的 src/agent/protocols.py
 * 定义Agent系统的共享数据结构和通信协议
 */
@Component
public class AgentProtocols {

    /** Agent运行统计 */
    public static class AgentRunStats {
        private int totalIterations;
        private int toolCallCount;
        private long totalDurationMs;
        private int tokenUsage;
        private String finalSignal;
        private int finalScore;

        public int getTotalIterations() { return totalIterations; }
        public void setTotalIterations(int v) { this.totalIterations = v; }
        public int getToolCallCount() { return toolCallCount; }
        public void setToolCallCount(int v) { this.toolCallCount = v; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public void setTotalDurationMs(long v) { this.totalDurationMs = v; }
        public int getTokenUsage() { return tokenUsage; }
        public void setTokenUsage(int v) { this.tokenUsage = v; }
        public String getFinalSignal() { return finalSignal; }
        public void setFinalSignal(String v) { this.finalSignal = v; }
        public int getFinalScore() { return finalScore; }
        public void setFinalScore(int v) { this.finalScore = v; }
    }

    /** Agent上下文(传递给各Agent的共享数据) */
    public static class AgentContext {
        private String stockCode;
        private String stockName;
        private String market;
        private Map<String, Object> historyData;
        private Map<String, Object> realtimeQuote;
        private Map<String, Object> technicalAnalysis;
        private List<Map<String, Object>> news;
        private Map<String, Object> marketContext;
        private String analysisMode; // quick/standard/full/specialist
        private Map<String, Object> extra = new LinkedHashMap<>();

        public String getStockCode() { return stockCode; }
        public void setStockCode(String v) { this.stockCode = v; }
        public String getStockName() { return stockName; }
        public void setStockName(String v) { this.stockName = v; }
        public String getMarket() { return market; }
        public void setMarket(String v) { this.market = v; }
        public Map<String, Object> getHistoryData() { return historyData; }
        public void setHistoryData(Map<String, Object> v) { this.historyData = v; }
        public Map<String, Object> getRealtimeQuote() { return realtimeQuote; }
        public void setRealtimeQuote(Map<String, Object> v) { this.realtimeQuote = v; }
        public Map<String, Object> getTechnicalAnalysis() { return technicalAnalysis; }
        public void setTechnicalAnalysis(Map<String, Object> v) { this.technicalAnalysis = v; }
        public List<Map<String, Object>> getNews() { return news; }
        public void setNews(List<Map<String, Object>> v) { this.news = v; }
        public Map<String, Object> getMarketContext() { return marketContext; }
        public void setMarketContext(Map<String, Object> v) { this.marketContext = v; }
        public String getAnalysisMode() { return analysisMode; }
        public void setAnalysisMode(String v) { this.analysisMode = v; }
        public Map<String, Object> getExtra() { return extra; }
        public void putExtra(String key, Object value) { this.extra.put(key, value); }
    }

    /** 阶段结果 */
    public static class StageResult {
        private String stageName;
        private String status; // success/failed/skipped
        private String output;
        private long durationMs;
        private String error;

        public StageResult(String stageName, String status, String output, long durationMs) {
            this.stageName = stageName;
            this.status = status;
            this.output = output;
            this.durationMs = durationMs;
        }

        public String getStageName() { return stageName; }
        public String getStatus() { return status; }
        public String getOutput() { return output; }
        public long getDurationMs() { return durationMs; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    /** 阶段状态枚举 */
    public enum StageStatus {
        PENDING, RUNNING, SUCCESS, FAILED, SKIPPED
    }
}
