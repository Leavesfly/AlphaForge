package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.skills.SkillBridgeEnabledCondition;
import io.leavesfly.alphaforge.application.agent.skills.SkillCliBridge;
import io.leavesfly.alphaforge.application.agent.skills.SkillResult;
import io.leavesfly.alphaforge.application.agent.tools.Tool;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个股阶段定位工具 — 桥接 alpha-forge-skill 的 run_stage.py。
 *
 * <p>箱体（平台）识别 + 位置分位 + 均线结构 → 筑底/突破/推进/派发/破位/下降七态，
 * 补齐 AlphaForge 原生缺失的阶段语义（box_oscillation 仅为形态策略）。</p>
 */
@Component
@Conditional(SkillBridgeEnabledCondition.class)
public class StageAnalysisTool implements Tool {

    private final SkillCliBridge bridge;

    public StageAnalysisTool(SkillCliBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "stage_analysis";
    }

    @Override
    public String description() {
        return "对个股做阶段定位（筑底/突破/推进/派发/破位/下降等七态）：基于箱体（平台）识别"
                + "+ 位置分位 + 均线结构判定\"现在走到哪一步\"，并给出突破价/破位价与应对姿态。"
                + "用户问\"XX 现在处于什么阶段/是不是在筑底/是不是在派发/行情走完了吗\"时调用。"
                + "转述要求：① 必须包含阶段中文结论、置信度与判定依据、位置分位、突破/破位关键价位；"
                + "② 阶段只回答\"现在在哪\"，是描述性统计且有滞后，不预测涨跌；能不能买须再调 decision_score；"
                + "③ 置信度低（结构不清）时不得强行给结论。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("stockCode", Map.of("type", "string",
                "description", "股票代码，如 600519 / hk00700 / AAPL / 600519.SH"));
        properties.put("historyDays", Map.of("type", "integer",
                "description", "阶段迁移轨迹回看交易日数（可选，默认不回看；看阶段是否稳定用 120）"));
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
        List<String> cliArgs = new ArrayList<>(List.of(
                "--symbol", SkillCliBridge.toSkillSymbol(stockCode)));
        if (args.get("historyDays") instanceof Number n && n.intValue() > 0) {
            cliArgs.addAll(List.of("--history", String.valueOf(n.intValue())));
        }

        SkillResult result = bridge.run("run_stage.py", cliArgs);

        StringBuilder sb = new StringBuilder();
        if (result.summary() != null) {
            sb.append(result.summary()).append('\n');
        }
        appendIfPresent(sb, "阶段", result.str("stage_cn"));
        appendIfPresent(sb, "置信度", result.str("confidence"));
        appendIfPresent(sb, "判定依据", result.str("rule"));
        appendIfPresent(sb, "位置分位", result.str("price_position"));
        appendTrigger(sb, result.get("trigger"));
        appendPosture(sb, result.get("posture"));
        sb.append(result.formatNextSteps());
        sb.append("（阶段定位是描述性统计、有滞后，不预测涨跌；能不能买请再调 decision_score）");
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank() && !"null".equals(value)) {
            sb.append(label).append("：").append(value).append('\n');
        }
    }

    @SuppressWarnings("unchecked")
    private void appendTrigger(StringBuilder sb, Object trigger) {
        if (!(trigger instanceof Map<?, ?> raw)) {
            appendIfPresent(sb, "关键价位（突破/破位触发）", trigger == null ? null : String.valueOf(trigger));
            return;
        }
        Map<String, Object> t = (Map<String, Object>) raw;
        Object up = t.get("breakout_price");
        Object down = t.get("breakdown_price");
        if (up == null && down == null) {
            return;
        }
        sb.append("关键价位：突破价 ").append(up).append(" / 破位价 ").append(down);
        Object distUp = t.get("distance_to_breakout_pct");
        if (distUp instanceof Number n) {
            sb.append(String.format("（距突破 %+.1f%%）", n.doubleValue() * 100));
        }
        sb.append('\n');
    }

    @SuppressWarnings("unchecked")
    private void appendPosture(StringBuilder sb, Object posture) {
        if (!(posture instanceof Map<?, ?> m)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) m;
        Object p = map.get("posture");
        if (p != null) {
            sb.append("应对姿态：").append(p).append('\n');
        }
        Object note = map.get("note") != null ? map.get("note") : map.get("advice");
        if (note != null) {
            sb.append("  - ").append(note).append('\n');
        }
    }
}
