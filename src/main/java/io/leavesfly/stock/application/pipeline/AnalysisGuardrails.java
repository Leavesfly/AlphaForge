package io.leavesfly.stock.application.pipeline;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 分析上下文包 + 市场阶段 + 通知契约 + 决策护栏 + 报告语言
 * 对应Python: analysis_context_pack_overview.py / analysis_context_pack_prompt.py
 *             market_phase_prompt.py / market_phase_summary.py
 *             notification_capabilities.py / notification_contracts.py
 *             phase_decision_guardrail.py / report_language.py
 *             daily_market_context_guardrail.py
 */
@Component
public class AnalysisGuardrails {

    // ===== 分析上下文包(analysis_context_pack) =====

    /** 生成分析上下文概览 */
    public Map<String, Object> renderContextPackOverview(Map<String, Object> context) {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("stock_code", context.get("stock_code"));
        overview.put("stock_name", context.get("stock_name"));
        overview.put("has_history", context.containsKey("history_data"));
        overview.put("has_realtime", context.containsKey("realtime_quote"));
        overview.put("has_news", context.containsKey("news"));
        overview.put("has_technical", context.containsKey("technical_analysis"));
        overview.put("has_market_context", context.containsKey("market_context"));
        overview.put("has_intelligence", context.containsKey("intelligence"));
        return overview;
    }

    /** 格式化上下文包为Prompt片段 */
    public String formatContextPackPrompt(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 分析上下文\n");
        sb.append("股票: ").append(context.getOrDefault("stock_name", "")).append("(").append(context.getOrDefault("stock_code", "")).append(")\n");
        if (context.containsKey("market_context")) sb.append("大盘环境: ").append(context.get("market_context")).append("\n");
        if (context.containsKey("belong_boards")) sb.append("所属板块: ").append(context.get("belong_boards")).append("\n");
        return sb.toString();
    }

    // ===== 市场阶段(market_phase) =====

    /** 判断当前市场阶段 */
    public String determineMarketPhase(double indexChangePct, double breadth) {
        if (indexChangePct > 2 && breadth > 0.7) return "主升浪";
        if (indexChangePct > 0 && breadth > 0.5) return "上涨";
        if (indexChangePct < -2 && breadth < 0.3) return "主跌浪";
        if (indexChangePct < 0 && breadth < 0.5) return "回调";
        return "震荡";
    }

    /** 生成市场阶段Prompt */
    public String marketPhasePrompt(String phase) {
        switch (phase) {
            case "主升浪": return "当前市场处于主升浪阶段，多头强势，可适当提高仓位";
            case "主跌浪": return "当前市场处于主跌浪阶段，空头主导，应控制仓位，以防御为主";
            case "回调": return "市场处于回调阶段，关注止跌信号，分批建仓";
            default: return "市场震荡整理，建议轻仓观望，等待方向明确";
        }
    }

    // ===== 决策护栏(phase_decision_guardrail) =====

    /** 阶段决策护栏: 限制在特定市场阶段的激进信号 */
    public String applyPhaseGuardrail(String signal, String marketPhase) {
        if ("主跌浪".equals(marketPhase) && ("strong_buy".equals(signal) || "buy".equals(signal))) {
            return "neutral"; // 主跌浪不给买入信号
        }
        if ("回调".equals(marketPhase) && "strong_buy".equals(signal)) {
            return "buy"; // 回调中降级强买为买入
        }
        return signal;
    }

    // ===== 通知契约(notification_contracts) =====

    /** 通知消息契约: 定义每种消息类型的结构 */
    public Map<String, Object> getNotificationContract(String messageType) {
        switch (messageType) {
            case "analysis_report": return Map.of("required_fields", List.of("stock_code", "signal", "score", "summary"), "max_length", 4000);
            case "alert": return Map.of("required_fields", List.of("stock_code", "condition", "value"), "max_length", 500);
            case "market_review": return Map.of("required_fields", List.of("date", "indices"), "max_length", 3000);
            default: return Map.of("required_fields", List.of("title", "content"), "max_length", 2000);
        }
    }

    // ===== 通知能力(notification_capabilities) =====

    /** 查询渠道能力矩阵 */
    public Map<String, Object> getChannelCapabilities(String channel) {
        Map<String, Object> caps = new LinkedHashMap<>();
        switch (channel) {
            case "telegram": caps.put("markdown", true); caps.put("image", true); caps.put("max_len", 4096); break;
            case "wecom": caps.put("markdown", true); caps.put("image", false); caps.put("max_len", 4000); break;
            case "feishu": caps.put("markdown", true); caps.put("image", true); caps.put("max_len", 30000); break;
            case "discord": caps.put("markdown", true); caps.put("image", true); caps.put("max_len", 2000); break;
            case "email": caps.put("markdown", false); caps.put("html", true); caps.put("image", true); caps.put("max_len", 100000); break;
            default: caps.put("markdown", false); caps.put("max_len", 1000);
        }
        return caps;
    }

    // ===== 报告语言(report_language) =====

    /** 标准化操作建议文本 */
    public String localizeAdvice(String advice, String lang) {
        if (!"zh".equals(lang)) return advice;
        if (advice == null) return "观望";
        switch (advice.toLowerCase()) {
            case "strong_buy": return "强烈买入";
            case "buy": return "买入";
            case "sell": return "卖出";
            case "strong_sell": return "强烈卖出";
            case "hold": return "持有";
            default: return "观望";
        }
    }

    /** 标准化趋势预测 */
    public String localizeTrend(String trend) {
        if (trend == null) return "不明朗";
        switch (trend.toLowerCase()) {
            case "up": case "bullish": return "看涨";
            case "down": case "bearish": return "看跌";
            case "sideways": return "横盘";
            default: return "不明朗";
        }
    }
}
