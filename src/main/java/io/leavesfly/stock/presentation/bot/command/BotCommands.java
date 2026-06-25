package io.leavesfly.stock.presentation.bot.command;

import io.leavesfly.stock.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.stock.domain.model.entity.AnalysisReport;

import io.leavesfly.stock.application.service.AnalysisHistoryService;
import io.leavesfly.stock.application.service.BacktestService;
import io.leavesfly.stock.application.service.MarketLightService;
import io.leavesfly.stock.application.service.NameToCodeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Bot命令处理器集合
 */
@Component
public class BotCommands {

    private static final Logger log = LoggerFactory.getLogger(BotCommands.class);
    private final StockAnalysisPipeline pipeline;
    private final NameToCodeResolver resolver;
    private final AnalysisHistoryService historyService;
    private final MarketLightService marketLightService;
    private final BacktestService backtestService;

    public BotCommands(StockAnalysisPipeline pipeline, NameToCodeResolver resolver,
                       AnalysisHistoryService historyService, MarketLightService marketLightService,
                       BacktestService backtestService) {
        this.pipeline = pipeline;
        this.resolver = resolver;
        this.historyService = historyService;
        this.marketLightService = marketLightService;
        this.backtestService = backtestService;
    }

    /** /analyze 600519 - 分析指定股票 */
    public String handleAnalyze(String args) {
        String code = resolver.resolve(args.trim());
        AnalysisReport report = pipeline.analyzeSingleStock(code, false, false);
        if (report == null) return "❌ 分析失败: " + args;
        return String.format("%s %s(%s)\n评分: %d/100\n信号: %s\n%s",
                signalEmoji(report.getSignal()), report.getStockName(), report.getStockCode(),
                report.getTotalScore() != null ? report.getTotalScore() : 0,
                report.getSignal(), report.getSummary() != null ? report.getSummary() : "");
    }

    /** /ask 茅台最近怎么样 - AI问答 */
    public String handleAsk(String question) {
        return "🤖 AI问答功能请使用 /chat 命令进入对话模式";
    }

    /** /chat - 进入对话模式 */
    public String handleChat(String message) {
        return "💬 对话模式已开启，请直接发送您的问题";
    }

    /** /history 600519 - 查看分析历史 */
    public String handleHistory(String args) {
        String code = resolver.resolve(args.trim());
        var history = historyService.getRecentReports(code, 5);
        if (history.isEmpty()) return "暂无分析记录: " + args;
        StringBuilder sb = new StringBuilder("📋 最近分析记录:\n");
        for (var r : history) {
            sb.append(String.format("  %s | %s | %d分\n", r.getAnalysisDate(), r.getSignal(), r.getTotalScore()));
        }
        return sb.toString();
    }

    /** /market - 大盘概览 */
    public String handleMarket(String args) {
        Map<String, Object> light = marketLightService.getMarketLight();
        return String.format("🏛️ 市场信号灯: %s\n%s", colorEmoji((String) light.get("color")), light.get("reason"));
    }

    /** /batch 600519,AAPL,hk00700 - 批量分析 */
    public String handleBatch(String args) {
        List<String> codes = resolver.resolveBatch(args);
        pipeline.runFullAnalysis(String.join(",", codes), false, false);
        return "✅ 已提交批量分析: " + codes.size() + " 只股票";
    }

    /** /status - 系统状态 */
    public String handleStatus(String args) {
        return "✅ 系统运行正常\n数据源: 在线\nLLM: 就绪";
    }

    /** /help - 帮助 */
    public String handleHelp(String args) {
        return "📖 可用命令:\n" +
                "/analyze <股票> - 分析\n" +
                "/ask <问题> - AI问答\n" +
                "/history <股票> - 历史记录\n" +
                "/market - 大盘概览\n" +
                "/batch <股票列表> - 批量分析\n" +
                "/status - 系统状态\n" +
                "/strategies - 策略列表\n" +
                "/research <主题> - 研究\n" +
                "/help - 帮助";
    }

    /** /strategies - 查看可用策略 */
    public String handleStrategies(String args) {
        return "📊 可用策略:\n  均线金叉 | 放量突破 | 牛趋势 | 缩量回调 | 箱体震荡 | 底部放量\n  龙头首板 | 热点题材 | 事件驱动 | 波浪理论 | 缠论 | 情绪周期";
    }

    /** /research <主题> - 研究 */
    public String handleResearch(String args) {
        return "🔍 研究功能开发中，请使用 /analyze 进行分析";
    }

    /** 分发命令 */
    public String dispatch(String command, String args) {
        switch (command.toLowerCase()) {
            case "/analyze": case "/a": return handleAnalyze(args);
            case "/ask": return handleAsk(args);
            case "/chat": return handleChat(args);
            case "/history": case "/h": return handleHistory(args);
            case "/market": case "/m": return handleMarket(args);
            case "/batch": case "/b": return handleBatch(args);
            case "/status": case "/s": return handleStatus(args);
            case "/help": return handleHelp(args);
            case "/strategies": return handleStrategies(args);
            case "/research": return handleResearch(args);
            default: return "❓ 未知命令: " + command + "\n输入 /help 查看可用命令";
        }
    }

    private String signalEmoji(String s) {
        if (s == null) return "⚖️";
        switch (s) { case "strong_buy": return "🔥"; case "buy": return "📈"; case "sell": return "📉"; default: return "⚖️"; }
    }
    private String colorEmoji(String c) {
        if (c == null) return "⚪";
        switch (c) { case "red": return "🔴"; case "yellow": return "🟡"; case "green": return "🟢"; default: return "⚪"; }
    }
}
