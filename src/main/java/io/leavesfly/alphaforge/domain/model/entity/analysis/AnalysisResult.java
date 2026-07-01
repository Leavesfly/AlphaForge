package io.leavesfly.alphaforge.domain.model.entity.analysis;

import java.util.Map;

/**
 * 分析结果（中间态） — 从 AnalysisPostProcessor 提取到 domain 层
 *
 * 作为 LLM/Agent 分析与后处理/信号验证之间的数据载体，
 * 不依赖任何 application/infrastructure 层组件。
 */
public class AnalysisResult {
    public String stockCode;
    public String stockName;
    public String signal;
    public Integer score;
    public String fullReport;
    public String summary;
    public String operationAdvice;
    public String confidence;
    public String riskNote;
    public String trendLabel;
    public String fallbackSource;
    public String source;
    public Double currentPrice;
    public Double targetPrice;
    public Double stopLossPrice;
    public Double pricePosition;
    public Map<String, Object> compositeScoring;
    public Double qualityScore;

    public static AnalysisResult dryRun(String code, String name) {
        AnalysisResult r = new AnalysisResult();
        r.stockCode = code;
        r.stockName = name;
        r.signal = "neutral";
        r.score = 50;
        r.fullReport = "[DRY RUN] 模拟分析结果";
        r.source = "dry_run";
        return r;
    }
}
