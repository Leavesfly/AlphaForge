package io.leavesfly.alphaforge.application.strategy.lifecycle;

/**
 * 策略生命周期状态枚举
 *
 * 状态流转：
 * DRAFT → TESTING → PUBLISHED → DEPRECATED
 *                ↘ ARCHIVED
 */
public enum StrategyLifecycleState {

    /** 草稿：刚创建，尚未校验或校验未通过 */
    DRAFT,

    /** 测试中：已通过校验，正在回测验证 */
    TESTING,

    /** 已发布：校验通过且已部署到策略目录，可被引擎调用 */
    PUBLISHED,

    /** 已废弃：不再使用，保留历史记录 */
    DEPRECATED,

    /** 已归档：已删除但保留历史快照 */
    ARCHIVED;

    /**
     * 判断状态转换是否合法
     */
    public boolean canTransitionTo(StrategyLifecycleState target) {
        if (target == null) return false;
        return switch (this) {
            case DRAFT -> target == TESTING || target == ARCHIVED;
            case TESTING -> target == PUBLISHED || target == DRAFT || target == ARCHIVED;
            case PUBLISHED -> target == DEPRECATED || target == TESTING;
            case DEPRECATED -> target == PUBLISHED || target == ARCHIVED;
            case ARCHIVED -> false; // 终态，不可再转换
        };
    }

    /**
     * 解析字符串为枚举，不区分大小写
     */
    public static StrategyLifecycleState fromString(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return DRAFT;
        }
    }
}
