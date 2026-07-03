package io.leavesfly.alphaforge.domain.service.portfolio;

/**
 * 组合优化目标。
 */
public enum OptimizationObjective {

    /** 等权重（基准） */
    EQUAL_WEIGHT,

    /** 逆波动率加权（波动越低权重越高） */
    INVERSE_VOLATILITY,

    /** 风险平价（各资产风险贡献相等，Equal Risk Contribution） */
    RISK_PARITY,

    /** 最小方差组合 */
    MIN_VARIANCE,

    /** 最大夏普比率组合（切点组合） */
    MAX_SHARPE,

    /** 均值-方差效用最大化（需风险厌恶系数 λ） */
    MEAN_VARIANCE;

    public static OptimizationObjective fromString(String value, OptimizationObjective fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
