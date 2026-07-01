package io.leavesfly.alphaforge.domain.model.enums;

/**
 * K线频率枚举
 *
 * 支持分钟/日/周/月多周期K线数据获取。
 * 各数据源适配器需将此枚举映射到各自的频率参数：
 * - 东财 klt: 1/5/15/30/60/101/102/103
 * - Yahoo interval: 1m/5m/15m/1h/1d/1wk/1mo
 */
public enum KLineFrequency {
    MINUTE_1("1分钟"),
    MINUTE_5("5分钟"),
    MINUTE_15("15分钟"),
    MINUTE_30("30分钟"),
    MINUTE_60("60分钟"),
    DAILY("日线"),
    WEEKLY("周线"),
    MONTHLY("月线");

    private final String description;

    KLineFrequency(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** 是否为分钟级别 */
    public boolean isMinuteLevel() {
        return this != DAILY && this != WEEKLY && this != MONTHLY;
    }
}
