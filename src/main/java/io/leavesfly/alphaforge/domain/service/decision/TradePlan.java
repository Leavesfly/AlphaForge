package io.leavesfly.alphaforge.domain.service.decision;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ATR 交易计划 — 价位来自 ATR 与均线结构，用于风险管理参考，不是订单指令。
 *
 * <p>入场参考 = 最新收盘价；回踩参考 = MA20；止损 = 入场 − 2×ATR(14)；
 * R = 入场 − 止损（单位风险）；止盈 = 入场 + 2R / 3R；
 * 追价上限 = 入场 + 0.5×ATR(14)（超过视为追高，等回踩）。</p>
 */
public class TradePlan {

    private final double entry;
    private final Double pullbackRef;
    private final double stop;
    private final double r;
    private final double target2R;
    private final double target3R;
    private final double chaseLimit;
    private final double atr;
    private Sizing sizing;

    public TradePlan(double entry, Double pullbackRef, double stop, double r,
                     double target2R, double target3R, double chaseLimit, double atr) {
        this.entry = entry;
        this.pullbackRef = pullbackRef;
        this.stop = stop;
        this.r = r;
        this.target2R = target2R;
        this.target3R = target3R;
        this.chaseLimit = chaseLimit;
        this.atr = atr;
    }

    public double getEntry() { return entry; }
    public Double getPullbackRef() { return pullbackRef; }
    public double getStop() { return stop; }
    public double getR() { return r; }
    public double getTarget2R() { return target2R; }
    public double getTarget3R() { return target3R; }
    public double getChaseLimit() { return chaseLimit; }
    public double getAtr() { return atr; }
    public Sizing getSizing() { return sizing; }

    void setSizing(Sizing sizing) {
        this.sizing = sizing;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entry", entry);
        map.put("pullbackRef", pullbackRef);
        map.put("stop", stop);
        map.put("r", r);
        map.put("target2R", target2R);
        map.put("target3R", target3R);
        map.put("chaseLimit", chaseLimit);
        map.put("atr", atr);
        map.put("sizing", sizing != null ? sizing.toMap() : null);
        return map;
    }

    /**
     * 建议仓位（风险预算法）：单笔最大亏损 = 资金 × 风险比例，股数 = 风险额 / R。
     */
    public static class Sizing {
        private final double capital;
        private final double riskPct;
        private final double riskAmount;
        private final int suggestedShares;
        private final double positionValue;
        private final double positionPct;
        private final int lotSize;

        public Sizing(double capital, double riskPct, double riskAmount, int suggestedShares,
                      double positionValue, double positionPct, int lotSize) {
            this.capital = capital;
            this.riskPct = riskPct;
            this.riskAmount = riskAmount;
            this.suggestedShares = suggestedShares;
            this.positionValue = positionValue;
            this.positionPct = positionPct;
            this.lotSize = lotSize;
        }

        public double getCapital() { return capital; }
        public double getRiskPct() { return riskPct; }
        public double getRiskAmount() { return riskAmount; }
        public int getSuggestedShares() { return suggestedShares; }
        public double getPositionValue() { return positionValue; }
        public double getPositionPct() { return positionPct; }
        public int getLotSize() { return lotSize; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("capital", capital);
            map.put("riskPct", riskPct);
            map.put("riskAmount", riskAmount);
            map.put("suggestedShares", suggestedShares);
            map.put("positionValue", positionValue);
            map.put("positionPct", positionPct);
            map.put("lotSize", lotSize);
            return map;
        }
    }
}
