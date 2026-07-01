package io.leavesfly.alphaforge.domain.model.enums;

/**
 * 复权类型枚举
 *
 * 用于K线数据获取时指定价格复权方式：
 * - NONE: 不复权（原始价格，适合跨除权日自行处理）
 * - FRONT: 前复权（以最新价格为基准向前调整，适合技术分析）
 * - BACK: 后复权（以最早价格为基准向后调整，适合长线回测）
 *
 * 东财 fqt 参数映射: 0/1/2
 * Yahoo 通过 events=div,splits 自动前复权
 */
public enum AdjustType {
    NONE("不复权"),
    FRONT("前复权"),
    BACK("后复权");

    private final String description;

    AdjustType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
