package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.service.decision.DecisionScoreService;
import io.leavesfly.alphaforge.domain.service.decision.LightResult;
import io.leavesfly.alphaforge.domain.service.decision.LightsResult;
import io.leavesfly.alphaforge.domain.service.decision.TradePlan;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 买点三灯评分工具 — 供 CHAT 工具循环响应"XX 现在能买吗"。
 *
 * <p>转述守则（写进 description 供模型遵循）：回复必须包含三灯速览（价X+势X+时X）、
 * 行动结论（七态之一）与关键理由；价灯灰时如实告知"结论仅基于势/时"；
 * 持仓态须区分"减风险纪律 ≠ 预测下跌"；结尾注明纪律预设未经样本外验证。</p>
 */
@Component
public class DecisionScoreTool implements Tool {

    private final DecisionScoreService decisionScoreService;

    public DecisionScoreTool(DecisionScoreService decisionScoreService) {
        this.decisionScoreService = decisionScoreService;
    }

    @Override
    public String name() {
        return "decision_score";
    }

    @Override
    public String description() {
        return "对个股执行买点三灯评估（价=值不值得拥有/势=市场是否认同/时=现在是不是好时机）。"
                + "用户问\"XX 现在能买吗/买点如何\"时调用；已登记持仓的股票会自动带出持仓视角（可不传 cost）。"
                + "转述要求：① 必须包含三灯速览（价X+势X+时X）与行动结论（七态：趋势买点/纯趋势仓/"
                + "等回踩/左侧观察/回避/持仓需减风险/无法评分）；② 价灯灰时如实说明结论仅基于势/时；"
                + "③ 持仓态结论（持仓需减风险）是减仓纪律而非预测下跌，不要说\"会跌\"；"
                + "④ 结尾注明三灯规则为纪律预设值，未经样本外验证。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("stockCode", Map.of("type", "string", "description", "股票代码，如 600519"));
        properties.put("cost", Map.of("type", "number", "description", "持仓成本价（可选，已持有时给出持仓视角）"));
        params.put("properties", properties);
        params.put("required", List.of("stockCode"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String stockCode = args.get("stockCode") != null ? String.valueOf(args.get("stockCode")).trim() : "";
        if (stockCode.isEmpty()) {
            return "缺少 stockCode 参数";
        }
        Double cost = args.get("cost") instanceof Number n ? n.doubleValue() : null;
        LightsResult result = decisionScoreService.score(stockCode, cost);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s %s 买点三灯：%s%n", result.getStockCode(),
                result.getStockName() != null ? result.getStockName() : "", result.lightsSummary()));
        sb.append(String.format("结论：%s（%s）%n", result.getVerdict().getCn(),
                result.getVerdict().name().toLowerCase()));
        sb.append("规则：").append(result.getDecision().rule()).append('\n');
        appendLight(sb, "价（值不值得拥有）", result.getLights().get("value"));
        appendLight(sb, "势（市场是否认同）", result.getLights().get("trend"));
        appendLight(sb, "时（现在是不是好时机）", result.getLights().get("timing"));

        TradePlan plan = result.getPlan();
        if (plan != null) {
            sb.append(String.format("交易计划：入场 %.2f（追价上限 %.2f，超过等回踩）/ 止损 %.2f（2×ATR，R=%.2f）/ 止盈 %.2f ~ %.2f%n",
                    plan.getEntry(), plan.getChaseLimit(), plan.getStop(), plan.getR(),
                    plan.getTarget2R(), plan.getTarget3R()));
            TradePlan.Sizing sizing = plan.getSizing();
            if (sizing != null && sizing.getSuggestedShares() > 0) {
                sb.append(String.format("建议仓位：%d 股（市值约 %.0f，占资金 %.1f%%；若触发止损约亏 %.0f 元）%n",
                        sizing.getSuggestedShares(), sizing.getPositionValue(),
                        sizing.getPositionPct() * 100, sizing.getRiskAmount()));
            }
        }
        if (result.getPosition() != null) {
            sb.append("持仓视角：").append(result.getPosition().get("advice")).append('\n');
        }
        sb.append("（三灯规则为纪律预设值，未经样本外验证；供决策参考，不构成投资建议）");
        return sb.toString();
    }

    private void appendLight(StringBuilder sb, String label, LightResult light) {
        if (light == null) {
            return;
        }
        sb.append(label).append("：").append(light.getColor().getCn()).append('\n');
        for (String reason : light.getReasons()) {
            sb.append("  - ").append(reason).append('\n');
        }
    }
}
