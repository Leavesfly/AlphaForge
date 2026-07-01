package io.leavesfly.alphaforge.application.service.market;

import java.util.List;
import java.util.Map;

/**
 * 市场共享常量与工具 — 统一指数列表和情绪计算逻辑
 *
 * 统一指数列表和情绪计算逻辑，避免多处硬编码。
 */
public final class MarketConstants {

    private MarketConstants() {}

    /** 主要市场指数（代码 → 名称），统一定义避免多处硬编码 */
    public static final Map<String, String> MARKET_INDICES = Map.of(
            "000001", "上证指数",
            "399001", "深证成指",
            "399006", "创业板指",
            "000300", "沪深300",
            "000016", "上证50",
            "000905", "中证500"
    );

    /** 核心指数子集（用于轻量级上下文） */
    public static final Map<String, String> CORE_INDICES = Map.of(
            "000001", "上证指数",
            "399001", "深证成指",
            "399006", "创业板指",
            "000300", "沪深300"
    );

    /**
     * 评估市场情绪 — 基于上涨指数占比
     *
     * @param bullishCount 上涨指数数量
     * @param totalCount   总指数数量
     * @return "乐观" / "中性" / "谨慎"
     */
    public static String assessSentiment(long bullishCount, int totalCount) {
        if (totalCount == 0) return "中性";
        double ratio = (double) bullishCount / totalCount;
        if (ratio >= 0.7) return "乐观";
        if (ratio >= 0.4) return "中性";
        return "谨慎";
    }

    /**
     * 评估市场情绪 — 基于指数分析结果列表
     *
     * @param indices 指数分析结果列表，每个 Map 需包含 "trend" 字段
     * @return "乐观" / "中性" / "谨慎"
     */
    public static String assessSentimentFromTrends(List<Map<String, Object>> indices) {
        if (indices.isEmpty()) return "中性";
        long bullish = indices.stream()
                .filter(i -> {
                    Object trend = i.get("trend");
                    return "强势上涨".equals(trend) || "震荡偏多".equals(trend);
                }).count();
        return assessSentiment(bullish, indices.size());
    }
}
