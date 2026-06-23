package io.leavesfly.stock.service;

import io.leavesfly.stock.model.entity.AnalysisReport;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报告格式化服务
 * 
 * 对应Python版本的 src/formatters.py + src/report_language.py
 * 功能: 报告格式转换(Markdown/HTML/纯文本/企微格式)
 */
@Service
public class ReportFormatterService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 格式化为Markdown报告
     */
    public String formatMarkdown(AnalysisReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 📊 ").append(report.getStockName()).append("(").append(report.getStockCode()).append(") 分析报告\n\n");
        sb.append("_分析时间: ").append(report.getAnalysisDate().format(DATE_FMT)).append("_\n\n");

        // 核心结论
        sb.append("## 核心结论\n\n");
        if (report.getSignal() != null) sb.append("- **交易信号**: ").append(formatSignal(report.getSignal())).append("\n");
        if (report.getTotalScore() != null) sb.append("- **综合评分**: ").append(report.getTotalScore()).append("/100\n");
        if (report.getCurrentPrice() != null) {
            sb.append("- **当前价格**: ").append(String.format("%.2f", report.getCurrentPrice()));
            if (report.getChangePct() != null) sb.append(String.format(" (%+.2f%%)", report.getChangePct()));
            sb.append("\n");
        }
        sb.append("\n");

        // 完整报告
        if (report.getFullReport() != null && !report.getFullReport().isEmpty()) {
            sb.append("## 详细分析\n\n");
            sb.append(report.getFullReport()).append("\n\n");
        }

        // 元数据
        sb.append("---\n");
        sb.append("_模型: ").append(report.getLlmModel()).append(" | 耗时: ")
          .append(String.format("%.1f", report.getDurationSeconds())).append("秒_\n");
        return sb.toString();
    }

    /**
     * 格式化为简短文本(通知用)
     */
    public String formatBrief(AnalysisReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(report.getStockName()).append("】").append(report.getStockCode()).append("\n");
        if (report.getCurrentPrice() != null) {
            sb.append("价格: ").append(String.format("%.2f", report.getCurrentPrice()));
            if (report.getChangePct() != null) sb.append(String.format("(%+.2f%%)", report.getChangePct()));
            sb.append("\n");
        }
        if (report.getSignal() != null) sb.append("信号: ").append(formatSignal(report.getSignal())).append("\n");
        if (report.getTotalScore() != null) sb.append("评分: ").append(report.getTotalScore()).append("/100\n");
        if (report.getSummary() != null) sb.append("摘要: ").append(report.getSummary()).append("\n");
        return sb.toString();
    }

    /**
     * 格式化为企业微信消息格式
     */
    public String formatWecom(AnalysisReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(report.getStockName()).append("(").append(report.getStockCode()).append(")\n");
        if (report.getCurrentPrice() != null) {
            sb.append("> 价格: <font color=\"info\">").append(String.format("%.2f", report.getCurrentPrice())).append("</font>");
            if (report.getChangePct() != null) {
                String color = report.getChangePct() >= 0 ? "warning" : "comment";
                sb.append(" <font color=\"").append(color).append("\">").append(String.format("%+.2f%%", report.getChangePct())).append("</font>");
            }
            sb.append("\n");
        }
        if (report.getSignal() != null) sb.append("> 信号: **").append(formatSignal(report.getSignal())).append("**\n");
        if (report.getTotalScore() != null) sb.append("> 评分: ").append(report.getTotalScore()).append("/100\n");
        return sb.toString();
    }

    /**
     * 批量格式化多个报告
     */
    public String formatBatchMarkdown(List<AnalysisReport> reports) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 📊 股票分析报告汇总 (").append(reports.size()).append("只)\n\n");
        for (AnalysisReport report : reports) {
            sb.append(formatMarkdown(report)).append("\n---\n\n");
        }
        return sb.toString();
    }

    /**
     * 信号中文化
     */
    private String formatSignal(String signal) {
        if (signal == null) return "中性";
        switch (signal.toLowerCase()) {
            case "strong_buy": return "🔥 强烈买入";
            case "buy": return "📈 买入";
            case "weak_buy": return "↗️ 偏多";
            case "neutral": return "⚖️ 中性";
            case "weak_sell": return "↘️ 偏空";
            case "sell": return "📉 卖出";
            case "strong_sell": return "⚠️ 强烈卖出";
            default: return signal;
        }
    }
}
