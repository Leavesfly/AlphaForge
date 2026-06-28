package io.leavesfly.alphaforge.domain.model.entity.analysis;

import java.time.LocalDateTime;

/**
 * 分析报告实体
 * 存储每次股票分析的完整结果
 */
public class AnalysisReport {

    private Long id;

    /** 股票代码 */
    private String stockCode;

    /** 股票名称 */
    private String stockName;

    /** 分析日期 */
    private LocalDateTime analysisDate;

    /** 市场类型 */
    private String market;

    /** 当前价格 */
    private Double currentPrice;

    /** 涨跌幅 */
    private Double changePct;

    /** 综合评分(0-100) */
    private Integer totalScore;

    /** 信号(strong_buy/buy/neutral/sell/strong_sell) */
    private String signal;

    /** 置信度(0-1) */
    private Double confidence;

    /** 分析摘要 */
    private String summary;

    /** 技术分析结果JSON */
    private String technicalAnalysis;

    /** 基本面分析结果JSON */
    private String fundamentalAnalysis;

    /** 新闻情绪分析结果JSON */
    private String newsAnalysis;

    /** 完整报告Markdown */
    private String fullReport;

    /** LLM原始回复 */
    private String llmResponse;

    /** Agent模式 */
    private String agentMode;

    /** 使用的LLM模型 */
    private String llmModel;

    /** 耗时(秒) */
    private Double durationSeconds;

    /** Token使用量 */
    private Integer tokenUsage;

    /** 是否为干运行 */
    private Boolean isDryRun = false;

    /** 报告语言 */
    private String reportLanguage;

    /** 使用的skills */
    private String skills;

    /** 分析阶段 */
    private String analysisPhase;

    /** 股票来源 */
    private String selectionSource;

    /** 关联任务ID */
    private String taskId;

    /** 创建时间 */
    private LocalDateTime createdAt;

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
    public String getReportLanguage() { return reportLanguage; }
    public void setReportLanguage(String reportLanguage) { this.reportLanguage = reportLanguage; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getAnalysisPhase() { return analysisPhase; }
    public void setAnalysisPhase(String analysisPhase) { this.analysisPhase = analysisPhase; }
    public String getSelectionSource() { return selectionSource; }
    public void setSelectionSource(String selectionSource) { this.selectionSource = selectionSource; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
