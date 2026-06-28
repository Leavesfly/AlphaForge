package io.leavesfly.alphaforge.domain.model.schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 分析报告Schema
 * 用于LLM返回的结构化分析报告
 */
public class AnalysisReportSchema {

    /** 核心结论 */
    private CoreConclusion coreConclusion;

    /** 仪表盘数据 */
    private Dashboard dashboard;

    /** 技术面分析 */
    private TechnicalSection technicalSection;

    /** 基本面分析 */
    private FundamentalSection fundamentalSection;

    /** 消息面分析 */
    private NewsSection newsSection;

    /** 风险评估 */
    private RiskAssessment riskAssessment;

    /** 操作建议 */
    private OperationAdvice operationAdvice;

    /** 分析元数据 */
    private AnalysisMetadata metadata;

    // ========== 内部类定义 ==========

    /**
     * 核心结论
     */
    public static class CoreConclusion {
        private String signal;        // 交易信号
        private int totalScore;       // 综合评分(0-100)
        private double confidence;    // 置信度(0-1)
        private String summary;       // 一句话结论
        private String reasoning;     // 推理过程
        private List<String> keyPoints; // 关键要点

        public String getSignal() { return signal; }
        public void setSignal(String signal) { this.signal = signal; }
        public int getTotalScore() { return totalScore; }
        public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
        public List<String> getKeyPoints() { return keyPoints; }
        public void setKeyPoints(List<String> keyPoints) { this.keyPoints = keyPoints; }
    }

    /**
     * 仪表盘数据
     */
    public static class Dashboard {
        private Double currentPrice;
        private Double changePct;
        private Long volume;
        private Double turnoverRate;
        private Double pe;
        private Double pb;
        private Double marketCap;
        private String industry;

        public Double getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }
        public Double getChangePct() { return changePct; }
        public void setChangePct(Double changePct) { this.changePct = changePct; }
        public Long getVolume() { return volume; }
        public void setVolume(Long volume) { this.volume = volume; }
        public Double getTurnoverRate() { return turnoverRate; }
        public void setTurnoverRate(Double turnoverRate) { this.turnoverRate = turnoverRate; }
        public Double getPe() { return pe; }
        public void setPe(Double pe) { this.pe = pe; }
        public Double getPb() { return pb; }
        public void setPb(Double pb) { this.pb = pb; }
        public Double getMarketCap() { return marketCap; }
        public void setMarketCap(Double marketCap) { this.marketCap = marketCap; }
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
    }

    /**
     * 技术面分析
     */
    public static class TechnicalSection {
        private String trend;              // 趋势判断
        private Map<String, Object> maAnalysis;  // 均线分析
        private Map<String, Object> macd;        // MACD分析
        private Map<String, Object> kdj;         // KDJ分析
        private Map<String, Object> rsi;         // RSI分析
        private Map<String, Object> boll;        // 布林带分析
        private String volumeAnalysis;     // 量能分析
        private String patternAnalysis;    // 形态分析
        private int technicalScore;        // 技术面评分

        public String getTrend() { return trend; }
        public void setTrend(String trend) { this.trend = trend; }
        public Map<String, Object> getMaAnalysis() { return maAnalysis; }
        public void setMaAnalysis(Map<String, Object> maAnalysis) { this.maAnalysis = maAnalysis; }
        public Map<String, Object> getMacd() { return macd; }
        public void setMacd(Map<String, Object> macd) { this.macd = macd; }
        public Map<String, Object> getKdj() { return kdj; }
        public void setKdj(Map<String, Object> kdj) { this.kdj = kdj; }
        public Map<String, Object> getRsi() { return rsi; }
        public void setRsi(Map<String, Object> rsi) { this.rsi = rsi; }
        public Map<String, Object> getBoll() { return boll; }
        public void setBoll(Map<String, Object> boll) { this.boll = boll; }
        public String getVolumeAnalysis() { return volumeAnalysis; }
        public void setVolumeAnalysis(String volumeAnalysis) { this.volumeAnalysis = volumeAnalysis; }
        public String getPatternAnalysis() { return patternAnalysis; }
        public void setPatternAnalysis(String patternAnalysis) { this.patternAnalysis = patternAnalysis; }
        public int getTechnicalScore() { return technicalScore; }
        public void setTechnicalScore(int technicalScore) { this.technicalScore = technicalScore; }
    }

    /**
     * 基本面分析
     */
    public static class FundamentalSection {
        private String financialHealth;
        private String growthPotential;
        private String valuation;
        private String industryPosition;
        private int fundamentalScore;

        public String getFinancialHealth() { return financialHealth; }
        public void setFinancialHealth(String financialHealth) { this.financialHealth = financialHealth; }
        public String getGrowthPotential() { return growthPotential; }
        public void setGrowthPotential(String growthPotential) { this.growthPotential = growthPotential; }
        public String getValuation() { return valuation; }
        public void setValuation(String valuation) { this.valuation = valuation; }
        public String getIndustryPosition() { return industryPosition; }
        public void setIndustryPosition(String industryPosition) { this.industryPosition = industryPosition; }
        public int getFundamentalScore() { return fundamentalScore; }
        public void setFundamentalScore(int fundamentalScore) { this.fundamentalScore = fundamentalScore; }
    }

    /**
     * 消息面分析
     */
    public static class NewsSection {
        private String sentiment;          // 情绪(positive/negative/neutral)
        private List<NewsItem> keyNews;    // 关键新闻
        private String marketImpact;       // 市场影响
        private int newsScore;

        public String getSentiment() { return sentiment; }
        public void setSentiment(String sentiment) { this.sentiment = sentiment; }
        public List<NewsItem> getKeyNews() { return keyNews; }
        public void setKeyNews(List<NewsItem> keyNews) { this.keyNews = keyNews; }
        public String getMarketImpact() { return marketImpact; }
        public void setMarketImpact(String marketImpact) { this.marketImpact = marketImpact; }
        public int getNewsScore() { return newsScore; }
        public void setNewsScore(int newsScore) { this.newsScore = newsScore; }
    }

    /**
     * 新闻条目
     */
    public static class NewsItem {
        private String title;
        private String source;
        private String date;
        private String sentiment;
        private String impact;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getSentiment() { return sentiment; }
        public void setSentiment(String sentiment) { this.sentiment = sentiment; }
        public String getImpact() { return impact; }
        public void setImpact(String impact) { this.impact = impact; }
    }

    /**
     * 风险评估
     */
    public static class RiskAssessment {
        private String riskLevel;          // 风险等级(low/medium/high/extreme)
        private List<String> riskFactors;  // 风险因素
        private String maxDrawdown;        // 最大回撤预估
        private String stopLoss;           // 止损建议

        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public List<String> getRiskFactors() { return riskFactors; }
        public void setRiskFactors(List<String> riskFactors) { this.riskFactors = riskFactors; }
        public String getMaxDrawdown() { return maxDrawdown; }
        public void setMaxDrawdown(String maxDrawdown) { this.maxDrawdown = maxDrawdown; }
        public String getStopLoss() { return stopLoss; }
        public void setStopLoss(String stopLoss) { this.stopLoss = stopLoss; }
    }

    /**
     * 操作建议
     */
    public static class OperationAdvice {
        private String shortTerm;       // 短期操作建议
        private String mediumTerm;      // 中期操作建议
        private String longTerm;        // 长期操作建议
        private String positionAdvice;  // 仓位建议
        private String entryPoint;      // 入场点位
        private String exitPoint;       // 出场点位

        public String getShortTerm() { return shortTerm; }
        public void setShortTerm(String shortTerm) { this.shortTerm = shortTerm; }
        public String getMediumTerm() { return mediumTerm; }
        public void setMediumTerm(String mediumTerm) { this.mediumTerm = mediumTerm; }
        public String getLongTerm() { return longTerm; }
        public void setLongTerm(String longTerm) { this.longTerm = longTerm; }
        public String getPositionAdvice() { return positionAdvice; }
        public void setPositionAdvice(String positionAdvice) { this.positionAdvice = positionAdvice; }
        public String getEntryPoint() { return entryPoint; }
        public void setEntryPoint(String entryPoint) { this.entryPoint = entryPoint; }
        public String getExitPoint() { return exitPoint; }
        public void setExitPoint(String exitPoint) { this.exitPoint = exitPoint; }
    }

    /**
     * 分析元数据
     */
    public static class AnalysisMetadata {
        private String model;
        private String agentMode;
        private Double durationSeconds;
        private Integer tokenUsage;
        private LocalDateTime timestamp;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getAgentMode() { return agentMode; }
        public void setAgentMode(String agentMode) { this.agentMode = agentMode; }
        public Double getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }
        public Integer getTokenUsage() { return tokenUsage; }
        public void setTokenUsage(Integer tokenUsage) { this.tokenUsage = tokenUsage; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    // ========== 主类Getter/Setter ==========
    public CoreConclusion getCoreConclusion() { return coreConclusion; }
    public void setCoreConclusion(CoreConclusion coreConclusion) { this.coreConclusion = coreConclusion; }
    public Dashboard getDashboard() { return dashboard; }
    public void setDashboard(Dashboard dashboard) { this.dashboard = dashboard; }
    public TechnicalSection getTechnicalSection() { return technicalSection; }
    public void setTechnicalSection(TechnicalSection technicalSection) { this.technicalSection = technicalSection; }
    public FundamentalSection getFundamentalSection() { return fundamentalSection; }
    public void setFundamentalSection(FundamentalSection fundamentalSection) { this.fundamentalSection = fundamentalSection; }
    public NewsSection getNewsSection() { return newsSection; }
    public void setNewsSection(NewsSection newsSection) { this.newsSection = newsSection; }
    public RiskAssessment getRiskAssessment() { return riskAssessment; }
    public void setRiskAssessment(RiskAssessment riskAssessment) { this.riskAssessment = riskAssessment; }
    public OperationAdvice getOperationAdvice() { return operationAdvice; }
    public void setOperationAdvice(OperationAdvice operationAdvice) { this.operationAdvice = operationAdvice; }
    public AnalysisMetadata getMetadata() { return metadata; }
    public void setMetadata(AnalysisMetadata metadata) { this.metadata = metadata; }
}
