package io.leavesfly.alphaforge.domain.service.decision;

/**
 * 行动结论七态 — 三灯经决策矩阵输出，而非单一分数阈值。
 *
 * <p>矩阵是纪律预设值：宁可错过、不可逆势/追高/踩雷。</p>
 */
public enum Verdict {

    /** 势绿+时绿：趋势健康且入场结构有序 */
    TREND_ENTRY("趋势买点"),
    /** 势绿+时绿+价红：只适合短线纪律仓，止损严格 */
    TREND_ONLY("纯趋势仓"),
    /** 势绿+时非绿：不追高不抢跑，给回踩参考位 */
    WAIT_PULLBACK("等回踩"),
    /** 价绿+势弱：进观察名单，不抄底 */
    LEFT_WATCH("左侧观察"),
    /** 价硬伤一票否决，或势弱价无吸引力 */
    AVOID("回避"),
    /** 持仓且势红/价硬伤：减仓纪律，非预测下跌 */
    REDUCE_RISK("持仓需减风险"),
    /** K 线不足，无法评分 */
    UNRATED("无法评分");

    private final String cn;

    Verdict(String cn) {
        this.cn = cn;
    }

    public String getCn() {
        return cn;
    }

    /** 可给出交易计划的行动态（等回踩的回踩参考位也是计划一部分） */
    public boolean isActionable() {
        return this == TREND_ENTRY || this == TREND_ONLY || this == WAIT_PULLBACK;
    }
}
