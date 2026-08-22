package io.leavesfly.alphaforge.application.diagnostics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 诊断上下文 — 记录分析流程每步执行情况。
 *
 * <p>由 StockAnalysisPipeline（功能轨）、AgentKernel（认知轨）与 AgentAnalysisService
 * 三方共享。置于无外部依赖的独立共享包，使三者依赖同一叶子类型而非互相引用，
 * 避免 pipeline ↔ agent.kernel ↔ service 形成包环（与 application.simulation
 * 打破 strategy ↔ backtest 环的做法一致）。</p>
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
