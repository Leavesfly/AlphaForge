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
 * 定投（DCA）规划工具 — 桥接 alpha-forge-skill 的 run_dca.py。
 *
 * <p>现金流账本 + XIRR 真实年化，支持定期定额/均线智能/超跌加码/价值平均等模式，
 * 并与一次性投入基准对比，补齐 AlphaForge 原生缺失的定投能力。</p>
 */
@Component
@Conditional(SkillBridgeEnabledCondition.class)
public class DcaPlanTool implements Tool {

    private final SkillCliBridge bridge;

    public DcaPlanTool(SkillCliBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "dca_plan";
    }

    @Override
    public String description() {
        return "对个股做定投（DCA）回测规划：现金流账本计算 XIRR 真实年化，支持五种模式"
                + "（fixed 定期定额/ma 均线智能/smart 智能分档/dip 超跌加码/value_avg 价值平均），"
                + "并与一次性投入基准对比。用户问\"我想定投 XX/定投划不划算/每月投多少\"时调用。"
                + "转述要求：① 必须包含 XIRR 与一次性投入 XIRR 的对比结论；"
                + "② 说明定投价值在纪律与摊薄成本，而非必然更高收益；"
                + "③ A 股可建议加 --dividends 建模分红（本工具 dividends=true 即启用）。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("stockCode", Map.of("type", "string",
                "description", "股票代码，如 600519 / AAPL / 600519.SH"));
        properties.put("mode", Map.of("type", "string",
                "enum", List.of("fixed", "ma", "smart", "dip", "value_avg"),
                "description", "定投模式，默认 fixed（定期定额）"));
        properties.put("freq", Map.of("type", "string",
                "enum", List.of("daily", "weekly", "monthly"),
                "description", "定投频率，默认 monthly"));
        properties.put("amount", Map.of("type", "number",
                "description", "每期基准投入金额，默认 1000"));
        properties.put("dividends", Map.of("type", "boolean",
                "description", "是否显式建模分红（A 股自动拉分红历史），默认 false"));
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
        if (args.get("mode") instanceof String mode && !mode.isBlank()) {
            cliArgs.addAll(List.of("--mode", mode.trim()));
        }
        if (args.get("freq") instanceof String freq && !freq.isBlank()) {
            cliArgs.addAll(List.of("--freq", freq.trim()));
        }
        if (args.get("amount") instanceof Number amount && amount.doubleValue() > 0) {
            cliArgs.addAll(List.of("--amount", String.valueOf(amount.doubleValue())));
        }
        if (Boolean.TRUE.equals(args.get("dividends"))) {
            cliArgs.add("--dividends");
        }

        SkillResult result = bridge.run("run_dca.py", cliArgs);

        StringBuilder sb = new StringBuilder();
        if (result.summary() != null) {
            sb.append(result.summary()).append('\n');
        }
        appendMetrics(sb, "定投指标", result.get("metrics"));
        appendMetrics(sb, "一次性投入基准", result.get("lumpsum_metrics"));
        sb.append(result.formatNextSteps());
        sb.append("（定投价值在纪律与摊薄成本，而非必然更高收益；回测不代表未来，不构成投资建议）");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendMetrics(StringBuilder sb, String label, Object metricsObj) {
        if (!(metricsObj instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        sb.append(label).append("：");
        appendField(sb, m, "xirr", "XIRR", v -> String.format("%.2f%%", ((Number) v).doubleValue() * 100));
        appendField(sb, m, "total_invested", "总投入", v -> String.format("%.0f", ((Number) v).doubleValue()));
        appendField(sb, m, "final_value", "期末市值", v -> String.format("%.0f", ((Number) v).doubleValue()));
        appendField(sb, m, "max_drawdown", "最大回撤", v -> String.format("%.2f%%", ((Number) v).doubleValue() * 100));
        sb.append('\n');
    }

    private void appendField(StringBuilder sb, Map<String, Object> m, String key, String label,
                             java.util.function.Function<Object, String> fmt) {
        Object v = m.get(key);
        if (v instanceof Number) {
            sb.append(label).append(' ').append(fmt.apply(v)).append('；');
        }
    }
}
