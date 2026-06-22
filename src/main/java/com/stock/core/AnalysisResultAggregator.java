package com.stock.core;

import com.stock.config.AppConfig;
import com.stock.model.entity.AnalysisReport;
import com.stock.notification.NotificationService;
import com.stock.service.ReportFormatterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 分析结果汇总器
 * 
 * 对应Python版本 pipeline.py 中的结果汇总和报告生成逻辑
 * 功能:
 * 1. 汇总多只股票的分析结果
 * 2. 生成综合报告
 * 3. 统计分析指标
 * 4. 推送通知
 */
@Component
public class AnalysisResultAggregator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisResultAggregator.class);
    private final NotificationService notificationService;
    private final ReportFormatterService formatterService;

    public AnalysisResultAggregator(NotificationService notificationService, ReportFormatterService formatterService) {
        this.notificationService = notificationService;
        this.formatterService = formatterService;
    }

    /**
     * 汇总分析结果并推送
     *
     * @param reports 所有股票的分析报告
     * @param dryRun  是否干运行(不推送)
     * @return 汇总统计
     */
    public Map<String, Object> aggregateAndNotify(List<AnalysisReport> reports, boolean dryRun) {
        Map<String, Object> summary = buildSummary(reports);

        if (!dryRun && !reports.isEmpty()) {
            try {
                notificationService.sendAnalysisReports(reports);
                summary.put("notification_sent", true);
            } catch (Exception e) {
                log.error("通知推送失败: {}", e.getMessage());
                summary.put("notification_sent", false);
                summary.put("notification_error", e.getMessage());
            }
        }

        return summary;
    }

    /**
     * 构建汇总统计
     */
    public Map<String, Object> buildSummary(List<AnalysisReport> reports) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_stocks", reports.size());
        summary.put("analysis_time", java.time.LocalDateTime.now().toString());

        if (reports.isEmpty()) return summary;

        // 按信号分类
        Map<String, List<AnalysisReport>> bySignal = reports.stream()
                .filter(r -> r.getSignal() != null)
                .collect(Collectors.groupingBy(AnalysisReport::getSignal));
        summary.put("signal_distribution", bySignal.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));

        // 统计评分
        OptionalDouble avgScore = reports.stream()
                .filter(r -> r.getTotalScore() != null)
                .mapToInt(AnalysisReport::getTotalScore)
                .average();
        summary.put("avg_score", avgScore.isPresent() ? String.format("%.1f", avgScore.getAsDouble()) : "N/A");

        // 买入信号股票
        List<String> buySignals = reports.stream()
                .filter(r -> "buy".equals(r.getSignal()) || "strong_buy".equals(r.getSignal()))
                .map(r -> r.getStockName() + "(" + r.getStockCode() + ")")
                .collect(Collectors.toList());
        summary.put("buy_signals", buySignals);

        // 卖出信号股票
        List<String> sellSignals = reports.stream()
                .filter(r -> "sell".equals(r.getSignal()) || "strong_sell".equals(r.getSignal()))
                .map(r -> r.getStockName() + "(" + r.getStockCode() + ")")
                .collect(Collectors.toList());
        summary.put("sell_signals", sellSignals);

        // 总耗时
        double totalDuration = reports.stream()
                .filter(r -> r.getDurationSeconds() != null)
                .mapToDouble(AnalysisReport::getDurationSeconds)
                .sum();
        summary.put("total_duration_seconds", String.format("%.1f", totalDuration));

        // 成功率
        long successCount = reports.stream().filter(r -> r.getFullReport() != null && !r.getFullReport().isEmpty()).count();
        summary.put("success_rate", String.format("%.0f%%", (double) successCount / reports.size() * 100));

        return summary;
    }

    /**
     * 生成综合文本报告
     */
    public String generateTextReport(List<AnalysisReport> reports) {
        if (reports.isEmpty()) return "无分析结果";
        return formatterService.formatBatchMarkdown(reports);
    }

    /**
     * 获取强信号股票(买入或卖出评分极端)
     */
    public List<AnalysisReport> getStrongSignals(List<AnalysisReport> reports) {
        return reports.stream()
                .filter(r -> r.getTotalScore() != null)
                .filter(r -> r.getTotalScore() >= 80 || r.getTotalScore() <= 20)
                .collect(Collectors.toList());
    }
}
