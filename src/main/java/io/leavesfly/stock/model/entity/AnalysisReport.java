package io.leavesfly.stock.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 分析报告实体
 * 存储每次股票分析的完整结果
 */
@Entity
@Table(name = "analysis_report")
public class AnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 股票代码 */
    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    /** 股票名称 */
    @Column(name = "stock_name", length = 50)
    private String stockName;

    /** 分析日期 */
    @Column(name = "analysis_date")
    private LocalDateTime analysisDate;

    /** 市场类型 */
    @Column(name = "market", length = 10)
    private String market;

    /** 当前价格 */
    @Column(name = "current_price")
    private Double currentPrice;

    /** 涨跌幅 */
    @Column(name = "change_pct")
    private Double changePct;

    /** 综合评分(0-100) */
    @Column(name = "total_score")
    private Integer totalScore;

    /** 信号(strong_buy/buy/neutral/sell/strong_sell) */
    @Column(name = "signal", length = 20)
    private String signal;

    /** 置信度(0-1) */
    @Column(name = "confidence")
    private Double confidence;

    /** 分析摘要 */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** 技术分析结果JSON */
    @Column(name = "technical_analysis", columnDefinition = "TEXT")
    private String technicalAnalysis;

    /** 基本面分析结果JSON */
    @Column(name = "fundamental_analysis", columnDefinition = "TEXT")
    private String fundamentalAnalysis;

    /** 新闻情绪分析结果JSON */
    @Column(name = "news_analysis", columnDefinition = "TEXT")
    private String newsAnalysis;

    /** 完整报告Markdown */
    @Column(name = "full_report", columnDefinition = "TEXT")
    private String fullReport;

    /** LLM原始回复 */
    @Column(name = "llm_response", columnDefinition = "TEXT")
    private String llmResponse;

    /** Agent模式 */
    @Column(name = "agent_mode", length = 20)
    private String agentMode;

    /** 使用的LLM模型 */
    @Column(name = "llm_model", length = 100)
    private String llmModel;

    /** 耗时(秒) */
    @Column(name = "duration_seconds")
    private Double durationSeconds;

    /** Token使用量 */
    @Column(name = "token_usage")
    private Integer tokenUsage;

    /** 是否为干运行 */
    @Column(name = "is_dry_run")
    private Boolean isDryRun = false;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.analysisDate == null) {
            this.analysisDate = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public LocalDateTime getAnalysisDate() { return analysisDate; }
    public void setAnalysisDate(LocalDateTime analysisDate) { this.analysisDate = analysisDate; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }
    public Double getChangePct() { return changePct; }
    public void setChangePct(Double changePct) { this.changePct = changePct; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getTechnicalAnalysis() { return technicalAnalysis; }
    public void setTechnicalAnalysis(String technicalAnalysis) { this.technicalAnalysis = technicalAnalysis; }
    public String getFundamentalAnalysis() { return fundamentalAnalysis; }
    public void setFundamentalAnalysis(String fundamentalAnalysis) { this.fundamentalAnalysis = fundamentalAnalysis; }
    public String getNewsAnalysis() { return newsAnalysis; }
    public void setNewsAnalysis(String newsAnalysis) { this.newsAnalysis = newsAnalysis; }
    public String getFullReport() { return fullReport; }
    public void setFullReport(String fullReport) { this.fullReport = fullReport; }
    public String getLlmResponse() { return llmResponse; }
    public void setLlmResponse(String llmResponse) { this.llmResponse = llmResponse; }
    public String getAgentMode() { return agentMode; }
    public void setAgentMode(String agentMode) { this.agentMode = agentMode; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }
    public Integer getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(Integer tokenUsage) { this.tokenUsage = tokenUsage; }
    public Boolean getIsDryRun() { return isDryRun; }
    public void setIsDryRun(Boolean isDryRun) { this.isDryRun = isDryRun; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
