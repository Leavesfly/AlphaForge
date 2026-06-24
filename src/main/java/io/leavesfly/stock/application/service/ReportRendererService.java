package io.leavesfly.stock.application.service;

import io.leavesfly.stock.domain.model.entity.AnalysisReport;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 报告渲染服务
 * 对应Python: report_renderer.py
 * 支持: Markdown/HTML/简洁/企微卡片/Telegram 等多种输出格式
 */
@Service
public class ReportRendererService {

    /** Markdown完整报告 */
    public String renderMarkdown(AnalysisReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(report.getStockName()).append("(").append(report.getStockCode()).append(") 分析报告\n\n");
        sb.append("**分析时间**: ").append(report.getAnalysisDate()).append("\n\n");
        sb.append("## 信号与评分\n");
        sb.append("- 信号: ").append(signalLabel(report.getSignal())).append("\n");
        sb.append("- 综合评分: ").append(report.getTotalScore()).append("/100\n");
        if (report.getCurrentPrice() != null)
            sb.append("- 当前价格: ").append(String.format("%.2f", report.getCurrentPrice())).append("\n");
        if (report.getChangePct() != null)
            sb.append("- 涨跌幅: ").append(String.format("%+.2f%%", report.getChangePct())).append("\n");
        sb.append("\n## 分析详情\n\n");
        sb.append(report.getFullReport() != null ? report.getFullReport() : "暂无详细分析");
        return sb.toString();
    }

    /** 简洁报告(通知用) */
    public String renderBrief(AnalysisReport report) {
        return String.format("%s %s(%s) | 评分:%d | %s | %.2f(%+.2f%%)",
                signalEmoji(report.getSignal()), report.getStockName(), report.getStockCode(),
                report.getTotalScore() != null ? report.getTotalScore() : 0,
                report.getSummary() != null ? report.getSummary() : "",
                report.getCurrentPrice() != null ? report.getCurrentPrice() : 0.0,
                report.getChangePct() != null ? report.getChangePct() : 0.0);
    }

    /** 企微卡片格式 */
    public String renderWecom(AnalysisReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(signalEmoji(report.getSignal())).append(" ").append(report.getStockName());
        sb.append("(").append(report.getStockCode()).append(")\n");
        sb.append("> 评分: **").append(report.getTotalScore()).append("**/100\n");
        sb.append("> 价格: ").append(String.format("%.2f", report.getCurrentPrice() != null ? report.getCurrentPrice() : 0));
        sb.append(" (").append(String.format("%+.2f%%", report.getChangePct() != null ? report.getChangePct() : 0)).append(")\n\n");
        if (report.getSummary() != null) sb.append(report.getSummary()).append("\n");
        return sb.toString();
    }

    /** 批量汇总报告 */
    public String renderBatchSummary(List<AnalysisReport> reports) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 每日分析汇总\n\n");
        sb.append("| 股票 | 信号 | 评分 | 价格 | 涨跌 |\n");
        sb.append("|------|------|------|------|------|\n");
        for (AnalysisReport r : reports) {
            sb.append(String.format("| %s | %s | %d | %.2f | %+.2f%% |\n",
                    r.getStockName(), signalLabel(r.getSignal()),
                    r.getTotalScore() != null ? r.getTotalScore() : 0,
                    r.getCurrentPrice() != null ? r.getCurrentPrice() : 0.0,
                    r.getChangePct() != null ? r.getChangePct() : 0.0));
        }
        return sb.toString();
    }

    private String signalLabel(String signal) {
        if (signal == null) return "中性";
        switch (signal) {
            case "strong_buy": return "强烈买入";
            case "buy": return "买入";
            case "sell": return "卖出";
            case "strong_sell": return "强烈卖出";
            default: return "中性";
        }
    }

    private String signalEmoji(String signal) {
        if (signal == null) return "⚖️";
        switch (signal) {
            case "strong_buy": return "🔥";
            case "buy": return "📈";
            case "sell": return "📉";
            case "strong_sell": return "⚠️";
            default: return "⚖️";
        }
    }
}
