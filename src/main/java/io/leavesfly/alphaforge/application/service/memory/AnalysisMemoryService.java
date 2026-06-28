package io.leavesfly.alphaforge.application.service.memory;

import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.EmbeddingPort;
import io.leavesfly.alphaforge.domain.service.port.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 分析记忆服务 — 基于 Embedding + 向量检索的 AI 记忆能力
 *
 * 核心能力：
 * 1. 索引分析报告 — 将每次分析结果转为向量存储，构建语义索引
 * 2. 查找相似分析 — 根据当前分析上下文，检索历史相似场景的分析结论与效果
 * 3. 查找相似 K 线形态 — 将 K 线序列转为文本描述，检索历史相似形态及其后续走势
 *
 * 当未配置 EmbeddingPort / VectorStorePort 时，自动降级为无操作模式。
 */
@Service
public class AnalysisMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisMemoryService.class);

    private static final String COLLECTION_REPORTS = "analysis_reports";
    private static final String COLLECTION_PATTERNS = "kline_patterns";

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;

    public AnalysisMemoryService(
            @org.springframework.beans.factory.annotation.Autowired(required = false) EmbeddingPort embeddingPort,
            @org.springframework.beans.factory.annotation.Autowired(required = false) VectorStorePort vectorStorePort) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        if (embeddingPort != null && vectorStorePort != null) {
            log.info("分析记忆服务已启用 (embedding={}, vectorStore={})",
                    embeddingPort.getModelName(), vectorStorePort.getClass().getSimpleName());
        } else {
            log.info("分析记忆服务未启用（未配置 EmbeddingPort 或 VectorStorePort）");
        }
    }

    /** 是否已启用 */
    public boolean isEnabled() {
        return embeddingPort != null && vectorStorePort != null;
    }

    // ==================== 分析报告索引 ====================

    /**
     * 索引分析报告（将其转为向量并存储）
     */
    public void indexAnalysis(AnalysisReport report) {
        if (!isEnabled() || report == null) return;

        try {
            String text = buildReportText(report);
            float[] vector = embeddingPort.embed(text);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("stockCode", report.getStockCode());
            metadata.put("stockName", report.getStockName());
            metadata.put("signal", report.getSignal());
            metadata.put("score", report.getTotalScore());
            metadata.put("date", report.getAnalysisDate() != null ? report.getAnalysisDate().toString() : "");

            vectorStorePort.upsert(COLLECTION_REPORTS,
                    report.getStockCode() + "_" + report.getAnalysisDate(),
                    vector, metadata);

            log.debug("已索引分析报告: {} ({})", report.getStockCode(), report.getAnalysisDate());
        } catch (Exception e) {
            log.warn("索引分析报告失败: {} - {}", report.getStockCode(), e.getMessage());
        }
    }

    /**
     * 查找相似分析（检索历史相似场景的分析结论）
     *
     * @param stockCode   股票代码
     * @param description 当前分析上下文描述
     * @param topK        返回数量
     * @return 相似分析列表（按相似度降序）
     */
    public List<SimilarAnalysis> findSimilarAnalyses(String stockCode, String description, int topK) {
        if (!isEnabled()) return Collections.emptyList();

        try {
            String queryText = stockCode + " " + description;
            float[] queryVector = embeddingPort.embed(queryText);

            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("stockCode", stockCode);

            List<VectorStorePort.SearchResult> results =
                    vectorStorePort.search(COLLECTION_REPORTS, queryVector, topK, filter);

            List<SimilarAnalysis> analyses = new ArrayList<>();
            for (VectorStorePort.SearchResult r : results) {
                analyses.add(new SimilarAnalysis(
                        r.id,
                        r.score,
                        (String) r.metadata.get("date"),
                        (String) r.metadata.get("signal"),
                        r.metadata.get("score") instanceof Number n ? n.intValue() : 0,
                        (String) r.metadata.get("stockName")
                ));
            }
            return analyses;
        } catch (Exception e) {
            log.warn("查找相似分析失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== K 线形态检索 ====================

    /**
     * 查找相似 K 线形态
     *
     * @param history K 线数据
     * @param topK    返回数量
     * @return 相似形态列表
     */
    public List<SimilarPattern> findSimilarPatterns(List<StockDailyData> history, int topK) {
        if (!isEnabled() || history == null || history.size() < 10) return Collections.emptyList();

        try {
            String patternText = serializeKlinePattern(history);
            float[] queryVector = embeddingPort.embed(patternText);

            List<VectorStorePort.SearchResult> results =
                    vectorStorePort.search(COLLECTION_PATTERNS, queryVector, topK, null);

            List<SimilarPattern> patterns = new ArrayList<>();
            for (VectorStorePort.SearchResult r : results) {
                patterns.add(new SimilarPattern(
                        r.id,
                        r.score,
                        (String) r.metadata.getOrDefault("stockCode", ""),
                        (String) r.metadata.getOrDefault("date", ""),
                        (String) r.metadata.getOrDefault("subsequentMove", "")
                ));
            }
            return patterns;
        } catch (Exception e) {
            log.warn("查找相似 K 线形态失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 辅助方法 ====================

    /** 将分析报告转为可嵌入的文本 */
    private String buildReportText(AnalysisReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("股票: ").append(report.getStockName()).append("(").append(report.getStockCode()).append(")");
        sb.append(" 信号: ").append(report.getSignal());
        sb.append(" 评分: ").append(report.getTotalScore());
        if (report.getSummary() != null) {
            sb.append(" 摘要: ").append(report.getSummary());
        }
        if (report.getFullReport() != null) {
            sb.append(" 报告: ").append(report.getFullReport(), 0, Math.min(500, report.getFullReport().length()));
        }
        return sb.toString();
    }

    /** 将 K 线序列转为文本描述（用于嵌入） */
    private String serializeKlinePattern(List<StockDailyData> history) {
        int end = history.size();
        int start = Math.max(0, end - 20); // 最近20根K线
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            StockDailyData bar = history.get(i);
            double close = bar.getClosePrice() != null ? bar.getClosePrice() : 0;
            double open = bar.getOpenPrice() != null ? bar.getOpenPrice() : close;
            double change = close > open ? (close - open) / open * 100 : 0;
            long vol = bar.getVolume() != null ? bar.getVolume() : 0;
            sb.append(String.format("D%d:%s%.1f%% V%d ", i - start, change >= 0 ? "+" : "", change, vol));
        }
        return sb.toString();
    }

    // ==================== 数据类 ====================

    /** 相似分析结果 */
    public record SimilarAnalysis(
            String id,
            float similarityScore,
            String date,
            String signal,
            int score,
            String stockName
    ) {}

    /** 相似 K 线形态结果 */
    public record SimilarPattern(
            String id,
            float similarityScore,
            String stockCode,
            String date,
            String subsequentMove
    ) {}
}
