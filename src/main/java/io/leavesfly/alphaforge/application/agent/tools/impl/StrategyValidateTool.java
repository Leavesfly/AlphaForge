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
 * 策略稳健性验证工具 — 桥接 alpha-forge-skill 的 run_validate.py。
 *
 * <p>走步样本外（walk-forward OOS）+ PBO 过拟合概率（CSCV），补齐 AlphaForge
 * 原生 WalkForwardValidator 缺失的过拟合统计诊断；与策略晋升质量门理念一致。</p>
 */
@Component
@Conditional(SkillBridgeEnabledCondition.class)
public class StrategyValidateTool implements Tool {

    private final SkillCliBridge bridge;

    public StrategyValidateTool(SkillCliBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "strategy_validate";
    }

    @Override
    public String description() {
        return "对\"标的 × 策略\"做稳健性验证：走步样本外（训练窗选参、测试块验证）对比 Buy&Hold 基准，"
                + "并计算过拟合概率 PBO（组合对称交叉验证 CSCV）。内置策略名：ma_cross/macd/rsi/bollinger/"
                + "momentum/donchian/kdj/grid/turtle/keltner/supertrend/dual_thrust/cci/williams_r。"
                + "用户问\"这策略靠谱吗/是不是过拟合/参数寻优后验证一下\"时调用。"
                + "转述要求：① 以样本外夏普为准对比基准；② PBO>50% 必须明确提示过拟合风险高、实盘大概率失效；"
                + "③ 夏普比率 > 3 应优先怀疑数据泄露或过拟合；④ 回测不代表未来。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("stockCode", Map.of("type", "string",
                "description", "股票代码，如 600519 / AAPL / 600519.SH"));
        properties.put("strategy", Map.of("type", "string",
                "description", "策略名，如 ma_cross/macd/rsi/turtle/supertrend 等 14 个内置策略之一"));
        properties.put("withPbo", Map.of("type", "boolean",
                "description", "是否计算 PBO 过拟合概率，默认 true（耗时略增）"));
        params.put("properties", properties);
        params.put("required", List.of("stockCode", "strategy"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String stockCode = args.get("stockCode") != null ? String.valueOf(args.get("stockCode")).trim() : "";
        String strategy = args.get("strategy") != null ? String.valueOf(args.get("strategy")).trim() : "";
        if (stockCode.isEmpty() || strategy.isEmpty()) {
            return "缺少 stockCode / strategy 参数";
        }
        List<String> cliArgs = new ArrayList<>(List.of(
                "--symbol", SkillCliBridge.toSkillSymbol(stockCode),
                "--strategy", strategy));
        if (!Boolean.FALSE.equals(args.get("withPbo"))) {
            cliArgs.add("--pbo");
        }

        SkillResult result = bridge.run("run_validate.py", cliArgs);

        StringBuilder sb = new StringBuilder();
        if (result.summary() != null) {
            sb.append(result.summary()).append('\n');
        }
        appendMetrics(sb, "样本外(OOS)", result.get("oos_metrics"));
        appendMetrics(sb, "基准 Buy&Hold", result.get("benchmark_metrics"));
        appendPbo(sb, result.get("pbo"));
        sb.append(result.formatNextSteps());
        sb.append("（以样本外为准，回测不代表未来，不构成投资建议）");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendMetrics(StringBuilder sb, String label, Object metricsObj) {
        if (!(metricsObj instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        sb.append(label).append("：");
        boolean any = false;
        for (String key : new String[]{"sharpe", "total_return", "max_drawdown", "win_rate"}) {
            Object v = m.get(key);
            if (v instanceof Number n) {
                sb.append(key).append('=').append(String.format("%.3f", n.doubleValue())).append(' ');
                any = true;
            }
        }
        if (any) {
            sb.append('\n');
        }
    }

    @SuppressWarnings("unchecked")
    private void appendPbo(StringBuilder sb, Object pboObj) {
        if (!(pboObj instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> pbo = (Map<String, Object>) raw;
        Object v = pbo.get("pbo");
        if (!(v instanceof Number n)) {
            return;
        }
        double p = n.doubleValue();
        sb.append(String.format("PBO 过拟合概率：%.0f%%%n", p * 100));
        if (p > 0.5) {
            sb.append("⚠️ PBO > 50%：样本内最优在样本外多半沦为下半区，过拟合风险高，实盘慎用。\n");
        }
    }
}
