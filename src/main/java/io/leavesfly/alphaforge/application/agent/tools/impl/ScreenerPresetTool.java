package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.skills.SkillCliBridge;
import io.leavesfly.alphaforge.application.agent.skills.SkillResult;
import io.leavesfly.alphaforge.application.agent.tools.Tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 方法论预设筛选工具 — 桥接 alpha-forge-skill 的 run_screener.py --preset。
 *
 * <p>八套经典投资方法论预设（十倍股/百倍股/猛兽股/打折高质量/超级强势股/费雪/
 * 纳维里尔/红利左侧），补齐 AlphaForge AlphaSift 通用打分之外的"方法论即产品"筛选。</p>
 */
public class ScreenerPresetTool implements Tool {

    /** 转述候选时的最大条数 */
    private static final int MAX_CANDIDATES = 5;

    /** 单候选转述的最大字段数 */
    private static final int MAX_FIELDS_PER_CANDIDATE = 8;

    private final SkillCliBridge bridge;

    public ScreenerPresetTool(SkillCliBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "screener_preset";
    }

    @Override
    public String description() {
        return "按经典投资方法论预设做全市场/指定池筛选：multibagger=十倍股统计特征；"
                + "hundredbagger=百倍股质量成长（迈耶标准）；monster=猛兽股右侧强势（波伊克标准）；"
                + "dhq=打折的高质量股（马哈尼标准）；superstock=超级强势股（斯泰恩标准）；"
                + "fisher=费雪成长质量 15 要点；navellier=纳维里尔八大指标；dividend=红利股左侧。"
                + "用户问\"帮我找十倍股/高股息分批买/费雪式成长股/低估值好公司\"时调用。"
                + "转述要求：① 给出达标候选排名与关键指标；② 声明是统计共性/书中标准而非预测，"
                + "定性项（管理层诚信、机构研报方向）需人工尽调补位；"
                + "③ 建议对候选再调 decision_score 做技术面复核，空结果是纪律特性而非故障；"
                + "④ 全市场扫描较慢（分钟级），提前告知用户耐心等待。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("preset", Map.of("type", "string",
                "enum", List.of("multibagger", "hundredbagger", "monster", "dhq",
                        "superstock", "fisher", "navellier", "dividend"),
                "description", "筛选预设：multibagger/hundredbagger/monster/dhq/superstock/fisher/navellier/dividend"));
        properties.put("universe", Map.of("type", "string",
                "enum", List.of("cn", "us"),
                "description", "扫描范围：cn=A 股全市场（默认）；us=美股全市场（市值阈值单位变亿美元）"));
        properties.put("symbols", Map.of("type", "string",
                "description", "手动标的列表（逗号分隔，可选；指定后不做全市场扫描）"));
        params.put("properties", properties);
        params.put("required", List.of("preset"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String preset = args.get("preset") != null ? String.valueOf(args.get("preset")).trim() : "";
        if (preset.isEmpty()) {
            return "缺少 preset 参数";
        }
        List<String> cliArgs = new ArrayList<>(List.of("--preset", preset));
        if (args.get("universe") instanceof String u && "us".equalsIgnoreCase(u.trim())) {
            cliArgs.addAll(List.of("--universe", "us"));
        }
        if (args.get("symbols") instanceof String s && !s.isBlank()) {
            String converted = java.util.Arrays.stream(s.split("[,，]"))
                    .map(String::trim)
                    .filter(x -> !x.isEmpty())
                    .map(SkillCliBridge::toSkillSymbol)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            if (!converted.isEmpty()) {
                cliArgs.addAll(List.of("--symbols", converted));
            }
        }

        SkillResult result = bridge.run("run_screener.py", cliArgs);

        StringBuilder sb = new StringBuilder();
        if (result.summary() != null) {
            sb.append(result.summary()).append('\n');
        }
        appendCandidates(sb, result.get("candidates"));
        sb.append(result.formatNextSteps());
        sb.append("（预设标准为统计共性/经典著作纪律，非涨跌预测；建议对候选再调 decision_score 复核；"
                + "仅供研究参考，不构成投资建议）");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendCandidates(StringBuilder sb, Object candidatesObj) {
        if (!(candidatesObj instanceof List<?> list) || list.isEmpty()) {
            sb.append("候选：无达标标的（纪律性空结果属正常，不代表功能故障）\n");
            return;
        }
        sb.append(String.format("候选（共 %d 只，展示前 %d）：%n", list.size(),
                Math.min(MAX_CANDIDATES, list.size())));
        int shown = 0;
        for (Object item : list) {
            if (shown++ >= MAX_CANDIDATES || !(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> c = (Map<String, Object>) raw;
            sb.append("  - ");
            int fields = 0;
            for (Map.Entry<String, Object> e : c.entrySet()) {
                if (fields++ >= MAX_FIELDS_PER_CANDIDATE) {
                    sb.append("…");
                    break;
                }
                sb.append(e.getKey()).append('=').append(e.getValue()).append(' ');
            }
            sb.append('\n');
        }
    }
}
