package io.leavesfly.alphaforge.domain.service.decision;

import io.leavesfly.alphaforge.domain.model.entity.user.UserRiskProfile;

/**
 * 风险档位 → 建议仓位乘数映射（纯函数，供三灯交易计划复用）。
 *
 * <p>保守 0.5 / 平衡 1.0 / 激进 1.5；未知或空档位按保守处理（宁可少给仓位）。</p>
 */
public final class PositionMultiplier {

    public static final double CONSERVATIVE = 0.5;
    public static final double BALANCED = 1.0;
    public static final double AGGRESSIVE = 1.5;

    private PositionMultiplier() {
    }

    public static double of(String riskTolerance) {
        if (riskTolerance == null) {
            return CONSERVATIVE;
        }
        return switch (riskTolerance) {
            case UserRiskProfile.CONSERVATIVE -> CONSERVATIVE;
            case UserRiskProfile.BALANCED -> BALANCED;
            case UserRiskProfile.AGGRESSIVE -> AGGRESSIVE;
            default -> CONSERVATIVE;
        };
    }
}
