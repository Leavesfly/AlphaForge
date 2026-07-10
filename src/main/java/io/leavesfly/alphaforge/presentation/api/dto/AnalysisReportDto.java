package io.leavesfly.alphaforge.presentation.api.dto;

import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分析报告响应 DTO：隔离 API 出参与领域实体。
 */
public record AnalysisReportDto(Long id, String stockCode, String stockName, LocalDateTime analysisDate, String market,
                                Double currentPrice, Double changePct, Integer totalScore, String signal,
                                Double confidence, String summary, String technicalAnalysis, String fundamentalAnalysis,
                                String newsAnalysis, String fullReport, String llmResponse, String agentMode,
                                String llmModel, Double durationSeconds, Integer tokenUsage, Boolean isDryRun,
                                String reportLanguage, String skills, String analysisPhase, String selectionSource,
                                String taskId, LocalDateTime createdAt) {

    public static AnalysisReportDto from(AnalysisReport r) {
        return new AnalysisReportDto(r.getId(), r.getStockCode(), r.getStockName(), r.getAnalysisDate(), r.getMarket(),
                r.getCurrentPrice(), r.getChangePct(), r.getTotalScore(), r.getSignal(), r.getConfidence(),
                r.getSummary(), r.getTechnicalAnalysis(), r.getFundamentalAnalysis(), r.getNewsAnalysis(),
                r.getFullReport(), r.getLlmResponse(), r.getAgentMode(), r.getLlmModel(), r.getDurationSeconds(),
                r.getTokenUsage(), r.getIsDryRun(), r.getReportLanguage(), r.getSkills(), r.getAnalysisPhase(),
                r.getSelectionSource(), r.getTaskId(), r.getCreatedAt());
    }

    public static List<AnalysisReportDto> from(List<AnalysisReport> list) {
        return list == null ? List.of() : list.stream().map(AnalysisReportDto::from).toList();
    }
}
