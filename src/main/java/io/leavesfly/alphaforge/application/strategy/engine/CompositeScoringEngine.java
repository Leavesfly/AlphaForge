package io.leavesfly.alphaforge.application.strategy.engine;

import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.model.ScoringProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 综合分析评分引擎。
 *
 * 读取 catalog 中所有含 scoring 段的策略，用 K 线、技术指标、实时行情
 * 逐条评估 conditions；全部满足则累加 score_weight，最终归一化到 0~100。
 *
 * 用于单股分析流水线（AnalysisPostProcessor），与选股、回测引擎相互独立。
 */
@Component
public class CompositeScoringEngine {

    private final StrategyCatalog catalog;
    private final StrategyPerformanceTracker performanceTracker;

    public CompositeScoringEngine(StrategyCatalog catalog, StrategyPerformanceTracker performanceTracker) {
        this.catalog = catalog;
        this.performanceTracker = performanceTracker;
    }

    /**
     * 对单只股票执行多策略加权评分。
     * 命中权重之和除以总权重，映射为 0~100 分；K 线不足时返回默认 50 分。
     */
    public CompositeScoringResult evaluate(ScoringContext ctx) {
        if (ctx.size() < 5) {
            return new CompositeScoringResult(50, 0, 0, List.of());
        }

        int earned = 0;
        int maxWeight = 0;
        List<CompositeScoringResult.StrategyHit> hits = new ArrayList<>();

        for (StrategyDefinition strategy : catalog.listByCapability("scoring")) {
            ScoringProfile profile = strategy.getScoring();
            if (profile == null || profile.getScoreWeight() <= 0) {
                continue;
            }
            int rawWeight = profile.getScoreWeight();
            // 衰减追踪：启用 auto_decay 的策略使用有效权重
            int weight = performanceTracker.getEffectiveWeight(
                    strategy.getId(), rawWeight, profile.isAutoDecay(), profile.getMinWeight());
            maxWeight += weight;
            boolean matched = matchesConditions(profile.getConditions(), ctx);
            if (matched) {
                earned += weight;
            }
            // 记录命中情况，供衰减追踪器更新
            if (profile.isAutoDecay()) {
                performanceTracker.recordMatch(strategy.getId(), matched, profile.getDecayWindow());
            }
            String label = profile.getLabel() != null ? profile.getLabel() : strategy.getLabel();
            hits.add(new CompositeScoringResult.StrategyHit(strategy.getId(), label, weight, matched));
        }

        int totalScore = maxWeight > 0
                ? (int) Math.round(Math.min(100, earned * 100.0 / maxWeight))
                : 50;
        return new CompositeScoringResult(totalScore, earned, maxWeight, hits);
    }

    /** 单条策略的全部 conditions 必须同时满足（AND 逻辑） */
    private boolean matchesConditions(Map<String, Object> conditions, ScoringContext ctx) {
        if (conditions.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            if (!evaluateCondition(entry.getKey(), entry.getValue(), conditions, ctx)) {
                return false;
            }
        }
        return true;
    }

    /** 按条件 key 分发到具体求值逻辑，与 YAML scoring.conditions 字段名对应 */
    private boolean evaluateCondition(String key, Object expected, Map<String, Object> all, ScoringContext ctx) {
        return switch (key) {
            case "ma_alignment" -> maAlignment(ctx).equalsIgnoreCase(String.valueOf(expected))
                    || ("bullish".equals(expected) && isMaBullish(ctx));
            case "min_days" -> countBullishDays(ctx, intVal(expected, 5)) >= intVal(expected, 5);
            case "pullback_max_pct" -> recentPullbackPct(ctx) <= doubleVal(expected, 5);
            case "volume_ratio_min" -> volumeRatio(ctx) >= doubleVal(expected, 2);
            case "breakout_volume_ratio" -> volumeRatio(ctx) >= doubleVal(expected, 1.8);
            case "breakout_resistance" -> bool(expected) && isBreakoutResistance(ctx);
            case "close_above_resistance" -> bool(expected) && closeAboveResistance(ctx);
            case "trend_up" -> bool(expected) && isTrendUp(ctx);
            case "volume_shrink_ratio" -> volumeShrinkRatio(ctx) <= doubleVal(expected, 0.6);
            case "pullback_days_max" -> recentDownDays(ctx) <= intVal(expected, 5);
            case "range_days" -> true;
            case "amplitude_max_pct" -> boxAmplitude(ctx, intVal(all.get("range_days"), 20))
                    <= doubleVal(expected, 15);
            case "price_near_low" -> bool(expected) && priceNearLow(ctx);
            case "consecutive_days" -> consecutiveVolumeDays(ctx, intVal(expected, 2));
            case "yang_covers_yin_count" -> oneYangCoversYin(ctx, intVal(expected, 3));
            case "volume_amplify" -> volumeAmplify(ctx) >= doubleVal(expected, 1.5);
            case "revenue_growth_min" -> quoteMetric(ctx, "revenue_growth") >= doubleVal(expected, 0);
            case "profit_growth_min" -> quoteMetric(ctx, "profit_growth") >= doubleVal(expected, 0);
            case "roe_min" -> quoteMetric(ctx, "roe") >= doubleVal(expected, 0);
            case "limit_up" -> bool(expected) && isLimitUp(ctx);
            case "is_sector_leader" -> bool(expected) && boolFromQuote(ctx, "is_sector_leader");
            case "turnover_ratio_min" -> quoteMetric(ctx, "turnover_rate", "turnover_ratio") >= doubleVal(expected, 0);
            case "wave_position" -> String.valueOf(expected).equalsIgnoreCase("wave3_start") && isWaveThirdStart(ctx);
            case "fibonacci_support" -> bool(expected) && nearFibSupport(ctx);
            case "cycle_phase" -> marketPhase(ctx).equalsIgnoreCase(String.valueOf(expected));
            case "sentiment_score_min" -> marketSentiment(ctx) >= doubleVal(expected, -50);
            case "has_major_event" -> bool(expected) && hasMajorEvent(ctx);
            case "event_freshness_days" -> true;
            case "divergence" -> bool(expected) && hasDivergence(ctx);
            case "center_break" -> bool(expected) && hasCenterBreak(ctx);
            case "eps_revision_pct" -> quoteMetric(ctx, "eps_revision_pct") >= doubleVal(expected, 0);
            case "analyst_upgrade" -> bool(expected) && boolFromQuote(ctx, "analyst_upgrade");
            case "theme_heat_rank" -> quoteMetric(ctx, "theme_heat_rank") <= intVal(expected, 3)
                    && quoteMetric(ctx, "theme_heat_rank") > 0;
            case "inflow_positive" -> bool(expected) && quoteMetric(ctx, "main_inflow", "net_inflow") > 0;
            default -> false;
        };
    }

    // ── 从技术指标派生判断 ──────────────────────────────────────────

    /** 均线是否多头排列（MA5 > MA10 > MA20） */
    private boolean isMaBullish(ScoringContext ctx) {
        Object ma = ctx.getTechnical().get("ma_analysis");
        if (ma instanceof Map<?, ?> map) {
            return "多头排列".equals(map.get("arrangement"));
        }
        return false;
    }

    private String maAlignment(ScoringContext ctx) {
        Object ma = ctx.getTechnical().get("ma_analysis");
        if (ma instanceof Map<?, ?> map && map.get("arrangement") != null) {
            String text = String.valueOf(map.get("arrangement"));
            if (text.contains("多头")) return "bullish";
            if (text.contains("空头")) return "bearish";
        }
        return "neutral";
    }

    private int countBullishDays(ScoringContext ctx, int lookback) {
        int count = 0;
        for (int i = ctx.size() - lookback; i < ctx.size(); i++) {
            if (i < 20) continue;
            double ma5 = avgClose(ctx, i, 5);
            double ma10 = avgClose(ctx, i, 10);
            double ma20 = avgClose(ctx, i, 20);
            if (ma5 > ma10 && ma10 > ma20) count++;
        }
        return count;
    }

    private double recentPullbackPct(ScoringContext ctx) {
        int end = ctx.size() - 1;
        double peak = ctx.close(end);
        for (int i = end - 1; i >= Math.max(0, end - 10); i--) {
            peak = Math.max(peak, ctx.close(i));
        }
        return peak > 0 ? (peak - ctx.close(end)) / peak * 100 : 0;
    }

    private double volumeRatio(ScoringContext ctx) {
        Object vol = ctx.getTechnical().get("volume_analysis");
        if (vol instanceof Map<?, ?> map && map.get("volume_ratio") != null) {
            try {
                return Double.parseDouble(String.valueOf(map.get("volume_ratio")));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        int end = ctx.size() - 1;
        if (end < 20) return 1;
        long today = ctx.volume(end);
        long avg = 0;
        for (int i = end - 20; i < end; i++) avg += ctx.volume(i);
        avg /= 20;
        return avg > 0 ? (double) today / avg : 1;
    }

    private boolean isBreakoutResistance(ScoringContext ctx) {
        return closeAboveResistance(ctx) && volumeRatio(ctx) >= 1.5;
    }

    private boolean closeAboveResistance(ScoringContext ctx) {
        int end = ctx.size() - 1;
        if (end < 20) return false;
        double resistance = 0;
        for (int i = end - 20; i < end; i++) {
            resistance = Math.max(resistance, ctx.close(i));
        }
        return ctx.close(end) > resistance;
    }

    private boolean isTrendUp(ScoringContext ctx) {
        String trend = String.valueOf(ctx.getTechnical().getOrDefault("trend", ""));
        return trend.contains("上涨") || trend.contains("偏多") || isMaBullish(ctx);
    }

    private double volumeShrinkRatio(ScoringContext ctx) {
        int end = ctx.size() - 1;
        if (end < 25) return 1;
        long recent = 0;
        for (int i = end - 4; i <= end; i++) recent += ctx.volume(i);
        recent /= 5;
        long baseline = 0;
        for (int i = end - 24; i <= end - 5; i++) baseline += ctx.volume(i);
        baseline /= 20;
        return baseline > 0 ? (double) recent / baseline : 1;
    }

    private int recentDownDays(ScoringContext ctx) {
        int count = 0;
        for (int i = ctx.size() - 1; i > 0 && count < 10; i--) {
            Double chg = ctx.changePct(i);
            if (chg != null && chg < 0) count++;
            else break;
        }
        return count;
    }

    private double boxAmplitude(ScoringContext ctx, int lookback) {
        int end = ctx.size() - 1;
        if (end < lookback) return 100;
        double high = Double.MIN_VALUE;
        double low = Double.MAX_VALUE;
        for (int i = end - lookback + 1; i <= end; i++) {
            high = Math.max(high, ctx.close(i));
            low = Math.min(low, ctx.close(i));
        }
        double mid = (high + low) / 2;
        return mid > 0 ? (high - low) / mid * 100 : 100;
    }

    private boolean priceNearLow(ScoringContext ctx) {
        int end = ctx.size() - 1;
        int lookback = Math.min(60, ctx.size());
        double low = Double.MAX_VALUE;
        double high = Double.MIN_VALUE;
        for (int i = end - lookback + 1; i <= end; i++) {
            low = Math.min(low, ctx.close(i));
            high = Math.max(high, ctx.close(i));
        }
        if (high <= low) return false;
        double pos = (ctx.close(end) - low) / (high - low);
        return pos <= 0.25;
    }

    private boolean consecutiveVolumeDays(ScoringContext ctx, int days) {
        int end = ctx.size() - 1;
        if (end < 20 + days) return false;
        for (int i = end - days + 1; i <= end; i++) {
            long avg = 0;
            for (int j = i - 20; j < i; j++) avg += ctx.volume(j);
            avg /= 20;
            if (avg <= 0 || ctx.volume(i) < avg * 2) return false;
        }
        return true;
    }

    private boolean oneYangCoversYin(ScoringContext ctx, int yinCount) {
        int end = ctx.size() - 1;
        if (end < yinCount) return false;
        StockDailyData yang = ctx.getHistory().get(end);
        if (yang.getOpenPrice() == null || yang.getClosePrice() == null) {
            Double chg = yang.getChangePct();
            if (chg == null || chg <= 0) return false;
        } else if (yang.getClosePrice() <= yang.getOpenPrice()) {
            return false;
        }
        double yangLow = yang.getLowPrice() != null ? yang.getLowPrice() : yang.getOpenPrice();
        double yangHigh = yang.getHighPrice() != null ? yang.getHighPrice() : yang.getClosePrice();
        for (int i = end - yinCount; i < end; i++) {
            var bar = ctx.getHistory().get(i);
            double open = bar.getOpenPrice() != null ? bar.getOpenPrice() : bar.getClosePrice();
            double close = bar.getClosePrice();
            if (close >= open) return false;
            double yinHigh = bar.getHighPrice() != null ? bar.getHighPrice() : open;
            double yinLow = bar.getLowPrice() != null ? bar.getLowPrice() : close;
            if (yangLow > yinLow || yangHigh < yinHigh) return false;
        }
        return true;
    }

    private double volumeAmplify(ScoringContext ctx) {
        int end = ctx.size() - 1;
        if (end < 1) return 1;
        long prev = ctx.volume(end - 1);
        return prev > 0 ? (double) ctx.volume(end) / prev : 1;
    }

    private boolean isLimitUp(ScoringContext ctx) {
        Double chg = ctx.changePct(ctx.size() - 1);
        if (chg != null && chg >= 9.5) return true;
        return quoteMetric(ctx, "change_pct", "pct_change") >= 9.5;
    }

    private boolean isWaveThirdStart(ScoringContext ctx) {
        return isMaBullish(ctx) && isTrendUp(ctx) && volumeRatio(ctx) >= 1.2;
    }

    private boolean nearFibSupport(ScoringContext ctx) {
        return recentPullbackPct(ctx) <= 8 && priceNearLow(ctx);
    }

    private String marketPhase(ScoringContext ctx) {
        String sentiment = String.valueOf(ctx.getMarketContext().getOrDefault("market_sentiment", ""));
        if (sentiment.contains("回暖") || sentiment.contains("乐观")) return "recovery";
        if (sentiment.contains("谨慎") || sentiment.contains("悲观")) return "decline";
        return "neutral";
    }

    private double marketSentiment(ScoringContext ctx) {
        Object score = ctx.getMarketContext().get("sentiment_score");
        if (score instanceof Number number) return number.doubleValue();
        String sentiment = String.valueOf(ctx.getMarketContext().getOrDefault("market_sentiment", ""));
        if (sentiment.contains("乐观")) return 30;
        if (sentiment.contains("谨慎")) return -30;
        return 0;
    }

    private boolean hasMajorEvent(ScoringContext ctx) {
        return boolFromQuote(ctx, "has_major_event")
                || ctx.getMarketContext().containsKey("major_event");
    }

    private boolean hasDivergence(ScoringContext ctx) {
        Object macd = ctx.getTechnical().get("macd");
        if (macd instanceof Map<?, ?> map) {
            String cross = map.get("cross") != null ? String.valueOf(map.get("cross")) : "";
            return cross.contains("金叉") || "底背离".equals(String.valueOf(map.get("divergence")));
        }
        return false;
    }

    private boolean hasCenterBreak(ScoringContext ctx) {
        Object boll = ctx.getTechnical().get("boll");
        if (boll instanceof Map<?, ?> map) {
            String pos = map.get("position") != null ? String.valueOf(map.get("position")) : "";
            return pos.contains("突破") || pos.contains("上轨");
        }
        return false;
    }

    // ── 行情与工具方法 ──────────────────────────────────────────────

    private double avgClose(ScoringContext ctx, int end, int period) {
        double sum = 0;
        for (int i = end - period + 1; i <= end; i++) sum += ctx.close(i);
        return sum / period;
    }

    private double quoteMetric(ScoringContext ctx, String... keys) {
        for (String key : keys) {
            Object val = ctx.getQuote().get(key);
            if (val instanceof Number number) return number.doubleValue();
        }
        return 0;
    }

    private boolean boolFromQuote(ScoringContext ctx, String key) {
        Object val = ctx.getQuote().get(key);
        if (val instanceof Boolean b) return b;
        return val != null && Boolean.parseBoolean(String.valueOf(val));
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private int intVal(Object value, int defaultValue) {
        if (value instanceof Number number) return number.intValue();
        return defaultValue;
    }

    private double doubleVal(Object value, double defaultValue) {
        if (value instanceof Number number) return number.doubleValue();
        return defaultValue;
    }
}
