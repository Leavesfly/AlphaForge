package io.leavesfly.alphaforge.domain.service.decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策矩阵 — 三灯 → 行动结论七态。纯函数，纪律预设：宁可错过、不可逆势/追高/踩雷。
 *
 * <p>价硬伤一票否决；势绿+时绿 → 趋势买点（价红降级纯趋势仓）；
 * 势绿+时非绿 → 等回踩；价绿+势弱 → 左侧观察；势弱价无吸引力 → 回避。</p>
 */
public final class VerdictMatrix {

    private VerdictMatrix() {
    }

    /**
     * @param value      价灯结果（detail.hardFlaw 参与一票否决）
     * @param trend      势灯结果
     * @param timing     时灯结果
     * @param trendScore 趋势分（触发条件引用）
     * @param snapshot   指标快照（ma20 等，触发条件引用）
     */
    public static Decision decide(LightResult value, LightResult trend, LightResult timing,
                                  double trendScore, Map<String, Object> snapshot) {
        String label = "价" + value.getColor().getCn() + "+势" + trend.getColor().getCn()
                + "+时" + timing.getColor().getCn();

        if (Boolean.TRUE.equals(value.getDetail().get("hardFlaw"))) {
            return new Decision(Verdict.AVOID,
                    label + " → 回避：价灯硬伤（ST/连续亏损/资不抵债）一票否决，利好不能救",
                    List.of());
        }

        if (trend.getColor() == LightColor.GREEN) {
            if (timing.getColor() == LightColor.GREEN) {
                if (value.getColor() == LightColor.RED) {
                    return new Decision(Verdict.TREND_ONLY,
                            label + " → 纯趋势仓：趋势与时机俱佳但估值过高，"
                                    + "只适合短线纪律仓，止损必须严格，不宜重仓长持",
                            List.of());
                }
                String suffix = value.getColor() == LightColor.GRAY
                        ? "（价维度无数据，仅代表势/时判断）" : "";
                return new Decision(Verdict.TREND_ENTRY,
                        label + " → 趋势买点：趋势健康且入场结构有序" + suffix,
                        List.of());
            }
            return new Decision(Verdict.WAIT_PULLBACK,
                    label + " → 等回踩：趋势健康但入场时机受限，不追高不抢跑",
                    pullbackTriggers(timing, snapshot));
        }

        if (value.getColor() == LightColor.GREEN) {
            return new Decision(Verdict.LEFT_WATCH,
                    label + " → 左侧观察：估值有吸引力但趋势未认同，进观察名单，不抄底",
                    watchTriggers(trend, trendScore));
        }
        return new Decision(Verdict.AVOID,
                label + " → 回避：趋势走弱且价维度无吸引力，没有参与理由",
                watchTriggers(trend, trendScore));
    }

    /** 等回踩的再评估触发条件 */
    static List<String> pullbackTriggers(LightResult timing, Map<String, Object> snapshot) {
        List<String> triggers = new ArrayList<>();
        Object ma20Obj = snapshot.get("ma20");
        String ma20Str = "";
        if (ma20Obj instanceof Double ma20 && !ma20.isNaN()) {
            ma20Str = String.format("（%.2f）", ma20);
        }
        Map<String, Object> detail = timing.getDetail();
        if (Boolean.TRUE.equals(detail.get("overheated"))) {
            triggers.add("回踩 MA20" + ma20Str + "附近企稳后再评估");
        }
        if (Boolean.TRUE.equals(detail.get("belowMa20"))) {
            triggers.add("收复 MA20" + ma20Str + "后再评估");
        }
        if (Boolean.TRUE.equals(detail.get("rsiHot"))) {
            triggers.add("RSI 回落至过热阈值以下");
        }
        if (timing.getReasons().stream().anyMatch(r -> r.contains("风险事件"))) {
            triggers.add("风险事件落地后再评估");
        }
        if (triggers.isEmpty()) {
            triggers.add("回踩 MA20" + ma20Str + "附近企稳后再评估");
        }
        return triggers;
    }

    /** 左侧观察/回避的再评估触发条件（趋势修复信号） */
    static List<String> watchTriggers(LightResult trend, double trendScore) {
        List<String> triggers = new ArrayList<>();
        Map<String, Object> detail = trend.getDetail();
        if (Boolean.TRUE.equals(detail.get("belowMa200"))) {
            triggers.add("收盘站回 MA200 之上");
        } else if (Boolean.TRUE.equals(detail.get("belowMa60"))) {
            triggers.add("收盘站回 MA60 之上");
        }
        if (Boolean.TRUE.equals(detail.get("weeklyBroken"))) {
            triggers.add("周线收盘收复周线 MA30");
        }
        if (trendScore < TrendLightCalculator.TREND_GREEN) {
            triggers.add(String.format("趋势分回升至 %.0f 以上", TrendLightCalculator.TREND_GREEN));
        }
        if (Boolean.TRUE.equals(detail.get("benchRiskOff"))) {
            triggers.add("基准收复其 MA200（大盘转多）");
        }
        return triggers;
    }

    /**
     * 左侧观察的分批计划：价深绿 + 无硬伤时引导 DCA 分批，不改「不抄底」纪律。
     * 不满足深绿/无硬伤条件时返回 null（仍只进观察名单）。
     */
    public static Map<String, Object> buildLeftPlan(LightResult value, Decision decision) {
        Double val = asDouble(value.getDetail().get("valuationPercentile"));
        boolean hardFlaw = Boolean.TRUE.equals(value.getDetail().get("hardFlaw"));
        boolean profitToLoss = Boolean.TRUE.equals(value.getDetail().get("profitToLoss"));
        if (val == null || val > ValueLightCalculator.VAL_DEEP || hardFlaw || profitToLoss) {
            return null;
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("reason", String.format("估值分位均值 %.0f%% ≤ %.0f%%（价灯深绿）且基本面无硬伤",
                val * 100, ValueLightCalculator.VAL_DEEP * 100));
        plan.put("approach", "时间分批（DCA）替代一次性抄底：左侧不预测底部，用纪律分批摊低成本");
        plan.put("positionCap", "左侧累计仓位建议不超过目标仓位的一半，剩余等趋势修复（右侧触发条件满足）再加");
        plan.put("stopConditions", List.of(
                "出现基本面硬伤（ST/连续亏损/资不抵债）或分红大幅削减：立即停止加码并离场",
                String.format("估值分位回升至 %.0f%% 以上（修复到中枢）：停止加码，改按右侧触发条件评估",
                        ValueLightCalculator.VAL_GREEN * 100)));
        plan.put("rightSideTriggers", decision.triggers());
        plan.put("suggestedAction", "dca");
        return plan;
    }

    private static Double asDouble(Object obj) {
        return obj instanceof Double d ? d : null;
    }

    /** 矩阵产出：行动结论 + 命中规则说明 + 再评估触发条件 */
    public record Decision(Verdict verdict, String rule, List<String> triggers) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rule", rule);
            map.put("triggers", List.copyOf(triggers));
            return map;
        }
    }
}
