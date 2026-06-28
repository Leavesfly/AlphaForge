package io.leavesfly.alphaforge.application.strategy.condition;

import java.util.Set;

/**
 * 综合评分条件 key 注册表，用于启动校验。
 */
public final class ScoringConditionRegistry {

    public static final Set<String> SUPPORTED_KEYS = Set.of(
            "ma_alignment", "min_days", "pullback_max_pct", "volume_ratio_min", "breakout_volume_ratio",
            "breakout_resistance", "close_above_resistance", "trend_up", "volume_shrink_ratio",
            "pullback_days_max", "range_days", "amplitude_max_pct", "price_near_low", "consecutive_days",
            "yang_covers_yin_count", "volume_amplify", "revenue_growth_min", "profit_growth_min", "roe_min",
            "limit_up", "is_sector_leader", "turnover_ratio_min", "wave_position", "fibonacci_support",
            "cycle_phase", "sentiment_score_min", "has_major_event", "event_freshness_days",
            "divergence", "center_break", "eps_revision_pct", "analyst_upgrade", "theme_heat_rank", "inflow_positive"
    );

    private ScoringConditionRegistry() {
    }
}
