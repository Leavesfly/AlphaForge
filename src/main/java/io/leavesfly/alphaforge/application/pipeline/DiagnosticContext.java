package io.leavesfly.alphaforge.application.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 诊断上下文 — 记录分析流程每步执行情况
 *
 * 从 StockAnalysisPipeline 提取为独立类，供 Pipeline 和 AgentAnalysisService 共享。
 */
public class DiagnosticContext {
    private final String stockCode;
    private final Map<String, Object> records = new LinkedHashMap<>();
    private String currentStage;
    private final long startTime = System.currentTimeMillis();

    public DiagnosticContext(String stockCode) {
        this.stockCode = stockCode;
    }

    public void stage(String name) {
        this.currentStage = name;
        records.put("stage_" + name + "_start", System.currentTimeMillis());
    }

    public void record(String key, Object value) {
        records.put(key, value);
    }

    public void fail(String reason) {
        records.put("failed_at", currentStage);
        records.put("error", reason);
    }

    public void complete(double elapsed) {
        records.put("total_elapsed", elapsed);
        records.put("status", "success");
    }

    public String getStockCode() {
        return stockCode;
    }

    public Map<String, Object> getRecords() {
        return records;
    }

    public long getStartTime() {
        return startTime;
    }
}
