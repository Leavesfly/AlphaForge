package io.leavesfly.alphaforge.domain.service.decision;

/**
 * 交易计划构建 — 仅行动态（trend_entry/trend_only/wait_pullback）生成。
 *
 * <p>建议仓位采用风险预算法：股数 = 资金 × 风险比例 / R（按 lot_size 向下取整），
 * 市值不超过可用资金；风险比例 = 基础 1% × 用户风险画像乘数。</p>
 */
public final class TradePlanBuilder {

    /** 止损 = 入场 − 2×ATR(14) */
    public static final double STOP_ATR_MULTIPLE = 2.0;
    /** 追价上限 = 入场 + 0.5×ATR(14) */
    public static final double CHASE_ATR_MULTIPLE = 0.5;
    /** 单笔风险预算基础比例（1%），实际 × 风险画像乘数 */
    public static final double BASE_RISK_PCT = 0.01;

    private TradePlanBuilder() {
    }

    /**
     * 由最新收盘/MA20/ATR14 生成交易计划价位。
     * ATR 或收盘价无效（NaN/非正）时返回 null，表示无法给出计划（诚实降级）。
     */
    public static TradePlan build(double close, double ma20, double atr14) {
        if (!valid(close) || !valid(atr14)) {
            return null;
        }
        double entry = close;
        double stop = entry - STOP_ATR_MULTIPLE * atr14;
        double r = entry - stop;
        if (r <= 0) {
            return null;
        }
        Double pullbackRef = valid(ma20) ? round2(ma20) : null;
        return new TradePlan(round2(entry), pullbackRef, round2(stop), round2(r),
                round2(entry + 2.0 * r), round2(entry + 3.0 * r),
                round2(entry + CHASE_ATR_MULTIPLE * atr14), round2(atr14));
    }

    /**
     * 在交易计划上附加风险预算法建议仓位。
     * 资金缺失/非正时不附加（plan 原样返回），不猜测仓位。
     *
     * @param maxPositionPct 单票仓位市值占资金上限（0~1，非法值回退 1.0）
     */
    public static TradePlan attachSizing(TradePlan plan, Double capitalYuan,
                                         double riskMultiplier, int lotSize, double maxPositionPct) {
        if (plan == null || capitalYuan == null || capitalYuan <= 0 || lotSize <= 0) {
            return plan;
        }
        double riskPct = BASE_RISK_PCT * riskMultiplier;
        if (riskPct <= 0) {
            return plan;
        }
        double r = plan.getR();
        double entry = plan.getEntry();
        if (!valid(r) || !valid(entry)) {
            return plan;
        }
        double cap = maxPositionPct > 0 && maxPositionPct <= 1.0 ? maxPositionPct : 1.0;
        double riskAmount = capitalYuan * riskPct;
        double rawShares = riskAmount / r;
        // 市值不超过可用资金与单票上限（低波动标的 R 小时风险预算法会算出超额仓位）
        rawShares = Math.min(rawShares, Math.min(capitalYuan, capitalYuan * cap) / entry);
        int shares = (int) Math.floor(rawShares / lotSize) * lotSize;
        plan.setSizing(new TradePlan.Sizing(round2(capitalYuan), round4(riskPct), round2(riskAmount),
                shares, round2(shares * entry), round4(shares * entry / capitalYuan), lotSize));
        return plan;
    }

    private static boolean valid(double x) {
        return Double.isFinite(x) && x > 0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
