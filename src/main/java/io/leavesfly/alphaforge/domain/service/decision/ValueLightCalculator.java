package io.leavesfly.alphaforge.domain.service.decision;

import java.util.List;

/**
 * 价灯（值不值得拥有）— 基本面硬伤一票红灯 + 估值分位定绿/黄/红；无数据亮灰。
 *
 * <p>硬伤规则：ST/*ST、每股净资产 &lt; 0（资不抵债）、最近 4 季 EPS 均 &lt; 0（连续亏损）。
 * 由盈转亏不算硬伤，但价灯封顶黄。估值分位：≤0.4 绿、0.4~0.7 黄、&gt;0.7 红。</p>
 */
public final class ValueLightCalculator {

    public static final double VAL_GREEN = 0.4;
    public static final double VAL_RED = 0.7;
    /** 价灯「深绿」阈值：分位 ≤0.25 才够便宜，左侧观察附分批计划 */
    public static final double VAL_DEEP = 0.25;

    private ValueLightCalculator() {
    }

    /**
     * @param isSt                是否 ST（null = 未知，跳过）
     * @param netAssetPerShare    每股净资产（null = 未知）
     * @param epsRecent           最近季 EPS 序列（null 或不足 4 个 = 跳过连续亏损检查）
     * @param valuationPercentile 估值分位均值 0~1（null = 价灯灰，诚实降级）
     * @param valuationNote       估值口径说明（可空）
     */
    public static LightResult calculate(Boolean isSt, Double netAssetPerShare,
                                        List<Double> epsRecent, Double valuationPercentile,
                                        String valuationNote) {
        LightResult light = new LightResult(LightColor.GRAY);
        boolean hardFlaw = false;
        boolean profitToLoss = false;

        boolean fundamentalsKnown = isSt != null || netAssetPerShare != null
                || (epsRecent != null && epsRecent.size() >= 4);

        if (Boolean.TRUE.equals(isSt)) {
            light.addReason("ST/*ST 标的，退市风险（硬伤）");
            hardFlaw = true;
        }
        if (netAssetPerShare != null && netAssetPerShare < 0) {
            light.addReason(String.format("每股净资产 %.2f < 0，资不抵债（硬伤）", netAssetPerShare));
            hardFlaw = true;
        }
        if (!hardFlaw && epsRecent != null && epsRecent.size() >= 4) {
            List<Double> last4 = epsRecent.subList(epsRecent.size() - 4, epsRecent.size());
            boolean allNegative = last4.stream().allMatch(e -> e != null && e < 0);
            if (allNegative) {
                light.addReason(String.format("最近 4 季 EPS 均为负（%.3f/%.3f/%.3f/%.3f），连续亏损（硬伤）",
                        last4.get(0), last4.get(1), last4.get(2), last4.get(3)));
                hardFlaw = true;
            } else if (last4.get(2) != null && last4.get(2) > 0
                    && last4.get(3) != null && last4.get(3) < 0) {
                profitToLoss = true;
                light.addReason(String.format("由盈转亏：第 3 季 EPS %.3f > 0，最近季 %.3f < 0，价灯封顶黄",
                        last4.get(2), last4.get(3)));
            }
        }
        if (fundamentalsKnown && !hardFlaw && !profitToLoss) {
            light.addReason("基本面未见硬伤");
        }

        if (hardFlaw) {
            light.setColor(LightColor.RED);
        } else if (valuationPercentile != null) {
            double val = valuationPercentile;
            if (val > VAL_RED) {
                light.setColor(LightColor.RED);
                light.addReason(String.format("估值分位 %.0f%% > %.0f%%，相对自身历史高估", val * 100, VAL_RED * 100));
            } else if (val > VAL_GREEN) {
                light.setColor(profitToLoss ? LightColor.YELLOW : LightColor.YELLOW);
                light.addReason(String.format("估值分位 %.0f%%，处于自身历史中枢区间", val * 100));
            } else {
                light.setColor(profitToLoss ? LightColor.YELLOW : LightColor.GREEN);
                light.addReason(String.format("估值分位 %.0f%% ≤ %.0f%%，相对自身历史偏低", val * 100, VAL_GREEN * 100));
            }
            if (valuationNote != null && !valuationNote.isBlank()) {
                light.addReason(valuationNote);
            }
        } else {
            light.setColor(profitToLoss ? LightColor.YELLOW : LightColor.GRAY);
            light.addReason("无估值分位数据：价维度无法判断，结论仅基于势/时（诚实降级，不猜测）");
        }

        light.putDetail("hardFlaw", hardFlaw);
        light.putDetail("profitToLoss", profitToLoss);
        light.putDetail("valuationPercentile", valuationPercentile);
        light.putDetail("deepGreen", valuationPercentile != null && valuationPercentile <= VAL_DEEP
                && !hardFlaw && !profitToLoss);
        return light;
    }
}
