package io.leavesfly.alphaforge.application.service.market;

import io.leavesfly.alphaforge.domain.model.enums.MarketType;

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

    // ==================== 行情简览：多市场指数定义 ====================

    /** A股指数（代码 → 名称） */
    public static final Map<String, String> A_SHARE_INDICES = Map.of(
            "000001", "上证指数",
            "399001", "深证成指",
            "399006", "创业板指",
            "000300", "沪深300"
    );

    /** 港股指数（Yahoo Finance 代码 → 名称） */
    public static final Map<String, String> HK_INDICES = Map.of(
            "^HSI", "恒生指数",
            "^HSCE", "国企指数"
    );

    /** 美股指数（Yahoo Finance 代码 → 名称） */
    public static final Map<String, String> US_INDICES = Map.of(
            "^DJI", "道琼斯工业",
            "^IXIC", "纳斯达克",
            "^GSPC", "标普500"
    );

    /** 获取指定市场的指数列表 */
    public static Map<String, String> getIndices(MarketType market) {
        return switch (market) {
            case A -> A_SHARE_INDICES;
            case HK -> HK_INDICES;
            case US -> US_INDICES;
            default -> Map.of();
        };
    }

    // ==================== 行情简览：热门股票定义 ====================

    /** A股热门股票（代码 → 名称） */
    public static final Map<String, String> A_SHARE_HOT_STOCKS = Map.of(
            "600519", "贵州茅台",
            "300750", "宁德时代",
            "601318", "中国平安",
            "002594", "比亚迪",
            "000858", "五粮液"
    );

    /** 港股热门股票（代码 → 名称） */
    public static final Map<String, String> HK_HOT_STOCKS = Map.of(
            "hk00700", "腾讯控股",
            "hk09988", "阿里巴巴",
            "hk03690", "美团",
            "hk01810", "小米集团"
    );

    /** 美股热门股票（代码 → 名称） */
    public static final Map<String, String> US_HOT_STOCKS = Map.of(
            "AAPL", "苹果",
            "MSFT", "微软",
            "NVDA", "英伟达",
            "TSLA", "特斯拉",
            "AMZN", "亚马逊"
    );

    /** 获取指定市场的热门股票列表 */
    public static Map<String, String> getHotStocks(MarketType market) {
        return switch (market) {
            case A -> A_SHARE_HOT_STOCKS;
            case HK -> HK_HOT_STOCKS;
            case US -> US_HOT_STOCKS;
            default -> Map.of();
        };
    }

    /** 获取指定市场的新闻搜索关键词 */
    public static String getNewsKeyword(MarketType market) {
        return switch (market) {
            case A -> "A股 市场";
            case HK -> "港股 市场";
            case US -> "美股 市场";
            default -> "股票市场";
        };
    }

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
