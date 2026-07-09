package io.leavesfly.alphaforge.application.strategy.condition;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.leavesfly.alphaforge.application.strategy.condition.StockBarMath.*;
import static io.leavesfly.alphaforge.application.strategy.condition.ValueCoercion.*;

/**
 * 回测条件求值器：统一 backtest YAML 中 type 字段的条件实现。
 */
@Component
public class BacktestConditionEvaluator {

    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "ma_cross", "volume_breakout", "change_below", "stop_loss", "take_profit",
            "price_above_ma", "price_below_ma", "ma_arrangement", "trend_above", "pullback_to",
            "volume_shrink", "near_box_low", "near_box_high", "box_break_down",
            "consecutive_up", "first_pullback", "break_below_ma", "holding_days", "wave_position",
            "sentiment_extreme", "event_trigger", "fundamental_filter",
            "price_near_support", "fundamental_deterioration",
            // 与综合评分共享的形态条件（见 BarPatternConditions）
            "price_near_low", "consecutive_volume_days", "yang_covers_yin",
            "volume_amplify", "momentum_up",
            "macd_golden_cross", "macd_death_cross", "boll_upper_break", "amplitude_below",
            // 经典因子库条件（见 FactorConditions / ClassicFactorLibrary）
            "factor",
            // 通道突破 / ATR（趋势跟踪）
            "channel_breakout", "channel_breakdown", "atr_stop",
            // 布林带均值回归
            "boll_lower_touch", "boll_upper_touch", "boll_mid_reclaim"
    );

    public boolean evaluate(Map<String, Object> condition, List<StockDailyData> data, int index,
                          Map<String, Object> parameters, boolean holding, double entryPrice, int entryDay) {
        String type = stringVal(condition.get("type"));
        return switch (type) {
            case "ma_cross" -> evaluateMaCross(condition, data, index, parameters);
            case "volume_breakout" -> evaluateVolumeBreakout(condition, data, index, parameters);
            case "change_below" -> evaluateChangeBelow(condition, data, index);
            case "stop_loss" -> holding && evaluateStopLoss(condition, data, index, entryPrice);
            case "take_profit" -> holding && evaluateTakeProfit(condition, data, index, entryPrice);
            case "price_above_ma" -> evaluatePriceAboveMa(condition, data, index);
            case "price_below_ma", "break_below_ma" -> evaluatePriceBelowMa(condition, data, index);
            case "ma_arrangement" -> evaluateMaArrangement(condition, data, index, parameters);
            case "trend_above" -> evaluateTrendAbove(condition, data, index);
            case "pullback_to" -> evaluatePullbackTo(condition, data, index);
            case "volume_shrink" -> evaluateVolumeShrink(condition, data, index, parameters);
            case "near_box_low" -> evaluateNearBoxLow(condition, data, index, parameters);
            case "near_box_high" -> evaluateNearBoxHigh(condition, data, index, parameters);
            case "box_break_down" -> evaluateBoxBreakDown(condition, data, index, parameters);
            case "consecutive_up" -> evaluateConsecutiveUp(condition, data, index);
            case "first_pullback" -> evaluateFirstPullback(condition, data, index);
            case "holding_days" -> holding && evaluateHoldingDays(condition, index, entryDay);
            case "wave_position" -> evaluateWavePosition(condition, data, index, parameters);
            case "sentiment_extreme" -> evaluateSentimentExtreme(condition, data, index, parameters);
            case "event_trigger" -> evaluateEventTrigger(condition, data, index, parameters);
            case "fundamental_filter" -> evaluateFundamentalFilter(condition, parameters);
            case "price_near_support" -> evaluatePriceNearSupport(condition, data, index, parameters);
            case "fundamental_deterioration" -> evaluateFundamentalDeterioration(condition, parameters);
            case "price_near_low" -> evaluatePriceNearLow(condition, data, index, parameters);
            case "consecutive_volume_days" -> evaluateConsecutiveVolumeDays(condition, data, index, parameters);
            case "yang_covers_yin" -> evaluateYangCoversYin(condition, data, index, parameters);
            case "volume_amplify" -> evaluateVolumeAmplify(condition, data, index, parameters);
            case "momentum_up" -> evaluateMomentumUp(condition, data, index, parameters);
            case "macd_golden_cross" -> BarPatternConditions.macdGoldenCross(data, index);
            case "macd_death_cross" -> BarPatternConditions.macdDeathCross(data, index);
            case "boll_upper_break" -> evaluateBollUpperBreak(condition, data, index, parameters);
            case "amplitude_below" -> evaluateAmplitudeBelow(condition, data, index, parameters);
            case "factor" -> FactorConditions.evaluate(condition, data, index);
            case "channel_breakout" -> evaluateChannelBreakout(condition, data, index, parameters);
            case "channel_breakdown" -> evaluateChannelBreakdown(condition, data, index, parameters);
            case "atr_stop" -> holding && evaluateAtrStop(condition, data, index, parameters, entryPrice);
            case "boll_lower_touch" -> evaluateBollLowerTouch(condition, data, index, parameters);
            case "boll_upper_touch" -> evaluateBollUpperTouch(condition, data, index, parameters);
            case "boll_mid_reclaim" -> evaluateBollMidReclaim(condition, data, index, parameters);
            default -> false;
        };
    }

    /**
     * 波浪位置判断：支持 wave3_start（第三浪起点）和 wave5_end（第五浪终点）
     * 支持字段名 position 和 target（YAML 中两种写法都兼容）
     */
    private boolean evaluateWavePosition(Map<String, Object> condition, List<StockDailyData> data,
                                         int index, Map<String, Object> parameters) {
        // 兼容 position 和 target 两种字段名
        String position = stringVal(condition.get("position"));
        if (position == null || position.isEmpty()) {
            position = stringVal(condition.get("target"));
        }
        if (position == null || position.isEmpty()) {
            return false;
        }

        if ("wave3_start".equalsIgnoreCase(position)) {
            return evaluateWave3Start(data, index, parameters);
        }
        if ("wave5_end".equalsIgnoreCase(position)) {
            return evaluateWave5End(data, index, parameters);
        }
        return false;
    }

    /** 第三浪起点：均线多头 + 趋势向上 + 放量 */
    private boolean evaluateWave3Start(List<StockDailyData> data, int index, Map<String, Object> parameters) {
        int shortMa = intParam(parameters, "short_ma", 10);
        int longMa = intParam(parameters, "long_ma", 30);
        if (index < longMa) {
            return false;
        }
        double maShort = avgClose(data, index, shortMa);
        double maLong = avgClose(data, index, longMa);
        if (maShort <= maLong) {
            return false;
        }
        int trendMa = intParam(parameters, "trend_ma", 60);
        if (index >= trendMa && data.get(index).getClosePrice() <= avgClose(data, index, trendMa)) {
            return false;
        }
        long avgVol = avgVolume(data, index - 20, index - 1);
        return avgVol > 0 && data.get(index).getVolume() >= avgVol * 1.2;
    }

    /** 第五浪终点：价格大幅偏离均线 + 量能背离（价格新高但成交量低于前高） */
    private boolean evaluateWave5End(List<StockDailyData> data, int index, Map<String, Object> parameters) {
        int longMa = intParam(parameters, "long_ma", 30);
        if (index < longMa + 20) {
            return false;
        }
        double maLong = avgClose(data, index, longMa);
        double price = data.get(index).getClosePrice();
        // 价格大幅偏离均线（偏离度 > 20%）
        if (maLong <= 0 || (price - maLong) / maLong * 100 < 20) {
            return false;
        }
        // 量能背离：价格创 20 日新高，但成交量低于 20 日均量
        double high20 = 0;
        for (int i = index - 20; i < index; i++) {
            high20 = Math.max(high20, data.get(i).getClosePrice());
        }
        if (price < high20) {
            return false;
        }
        long avgVol = avgVolume(data, index - 20, index - 1);
        return avgVol > 0 && data.get(index).getVolume() < avgVol * 0.8;
    }

    private boolean evaluateMaCross(Map<String, Object> condition, List<StockDailyData> data, int index,
                                    Map<String, Object> parameters) {
        int fast = maPeriod(condition.get("fast"), parameters, "fast_period", 5);
        int slow = maPeriod(condition.get("slow"), parameters, "slow_period", 20);
        if (index < slow) {
            return false;
        }
        double fastMa = avgClose(data, index, fast);
        double slowMa = avgClose(data, index, slow);
        double prevFastMa = avgClose(data, index - 1, fast);
        double prevSlowMa = avgClose(data, index - 1, slow);
        String direction = stringVal(condition.get("direction"));
        if ("golden".equals(direction)) {
            return prevFastMa <= prevSlowMa && fastMa > slowMa;
        }
        if ("death".equals(direction)) {
            return prevFastMa >= prevSlowMa && fastMa < slowMa;
        }
        return false;
    }

    private boolean evaluateVolumeBreakout(Map<String, Object> condition, List<StockDailyData> data, int index,
                                           Map<String, Object> parameters) {
        int period = intParam(parameters, "ma_period", 20);
        if (index < period) {
            return false;
        }
        double multiple = doubleVal(condition.get("multiple"), doubleParam(parameters, "volume_multiple", 2.0));
        double minChange = doubleVal(condition.get("min_change"), doubleParam(parameters, "min_change_pct", 3.0));
        long avgVol = avgVolume(data, index - period, index - 1);
        StockDailyData today = data.get(index);
        Double changePct = today.getChangePct();
        return today.getVolume() > avgVol * multiple && changePct != null && changePct > minChange;
    }

    private boolean evaluateChangeBelow(Map<String, Object> condition, List<StockDailyData> data, int index) {
        Double changePct = data.get(index).getChangePct();
        double threshold = doubleVal(condition.get("pct"), -5);
        return changePct != null && changePct < threshold;
    }

    private boolean evaluateStopLoss(Map<String, Object> condition, List<StockDailyData> data,
                                     int index, double entryPrice) {
        if (entryPrice <= 0) {
            return false;
        }
        double pct = doubleVal(condition.get("pct"), -8);
        double current = data.get(index).getClosePrice();
        return (current - entryPrice) / entryPrice * 100 <= pct;
    }

    private boolean evaluateTakeProfit(Map<String, Object> condition, List<StockDailyData> data,
                                       int index, double entryPrice) {
        if (entryPrice <= 0) {
            return false;
        }
        double pct = doubleVal(condition.get("pct"), 15);
        double current = data.get(index).getClosePrice();
        return (current - entryPrice) / entryPrice * 100 >= pct;
    }

    private boolean evaluatePriceAboveMa(Map<String, Object> condition, List<StockDailyData> data, int index) {
        double price = data.get(index).getClosePrice();
        Object maValue = condition.get("ma");
        if (maValue instanceof List<?> list) {
            for (Object item : list) {
                int period = intVal(item, 0);
                if (period <= 0 || index < period || price <= avgClose(data, index, period)) {
                    return false;
                }
            }
            return true;
        }
        int period = intVal(maValue, 20);
        return index >= period && price > avgClose(data, index, period);
    }

    private boolean evaluatePriceBelowMa(Map<String, Object> condition, List<StockDailyData> data, int index) {
        int period = intVal(condition.get("ma"), 30);
        if (index < period) {
            return false;
        }
        return data.get(index).getClosePrice() < avgClose(data, index, period);
    }

    private boolean evaluateMaArrangement(Map<String, Object> condition, List<StockDailyData> data,
                                          int index, Map<String, Object> parameters) {
        if (!"bullish".equals(stringVal(condition.get("direction")))) {
            return false;
        }
        int shortMa = intParam(parameters, "short_ma", 10);
        int longMa = intParam(parameters, "long_ma", 30);
        if (index < longMa) {
            return false;
        }
        return avgClose(data, index, shortMa) > avgClose(data, index, longMa);
    }

    private boolean evaluateTrendAbove(Map<String, Object> condition, List<StockDailyData> data, int index) {
        int period = intVal(condition.get("ma"), 60);
        if (index < period) {
            return false;
        }
        return data.get(index).getClosePrice() > avgClose(data, index, period);
    }

    private boolean evaluatePullbackTo(Map<String, Object> condition, List<StockDailyData> data, int index) {
        int period = intVal(condition.get("ma"), 20);
        if (index < period) {
            return false;
        }
        double ma = avgClose(data, index, period);
        double price = data.get(index).getClosePrice();
        double tolerance = doubleVal(condition.get("tolerance_pct"), 2.0);
        return Math.abs(price - ma) / ma * 100 <= tolerance;
    }

    private boolean evaluateVolumeShrink(Map<String, Object> condition, List<StockDailyData> data, int index,
                                         Map<String, Object> parameters) {
        if (index < 25) {
            return false;
        }
        double ratio = doubleVal(condition.get("ratio"), doubleParam(parameters, "volume_shrink_ratio", 0.5));
        long recent = avgVolume(data, index - 4, index);
        long baseline = avgVolume(data, index - 24, index - 5);
        return baseline > 0 && recent <= baseline * ratio;
    }

    private boolean evaluateNearBoxLow(Map<String, Object> condition, List<StockDailyData> data, int index,
                                       Map<String, Object> parameters) {
        int lookback = intParam(parameters, "lookback_days", 20);
        if (index < lookback) {
            return false;
        }
        double low = boxLow(data, index, lookback);
        double price = data.get(index).getClosePrice();
        double tolerance = doubleVal(condition.get("tolerance_pct"), 2.0);
        return low > 0 && (price - low) / low * 100 <= tolerance;
    }

    private boolean evaluateNearBoxHigh(Map<String, Object> condition, List<StockDailyData> data, int index,
                                        Map<String, Object> parameters) {
        int lookback = intParam(parameters, "lookback_days", 20);
        if (index < lookback) {
            return false;
        }
        double high = boxHigh(data, index, lookback);
        double price = data.get(index).getClosePrice();
        double tolerance = doubleVal(condition.get("tolerance_pct"), 2.0);
        return high > 0 && (high - price) / high * 100 <= tolerance;
    }

    private boolean evaluateBoxBreakDown(Map<String, Object> condition, List<StockDailyData> data, int index,
                                         Map<String, Object> parameters) {
        int lookback = intParam(parameters, "lookback_days", 20);
        if (index < lookback) {
            return false;
        }
        double low = boxLow(data, index - 1, lookback);
        double price = data.get(index).getClosePrice();
        double threshold = doubleVal(condition.get("pct"), -3);
        return low > 0 && (price - low) / low * 100 <= threshold;
    }

    private boolean evaluateConsecutiveUp(Map<String, Object> condition, List<StockDailyData> data, int index) {
        int days = intVal(condition.get("days"), 3);
        if (index < days) {
            return false;
        }
        for (int i = index - days + 1; i <= index; i++) {
            Double change = data.get(i).getChangePct();
            if (change == null || change <= 0) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateFirstPullback(Map<String, Object> condition, List<StockDailyData> data, int index) {
        Double change = data.get(index).getChangePct();
        if (change == null) {
            return false;
        }
        double maxPct = doubleVal(condition.get("max_pct"), -5);
        return change < 0 && change >= maxPct;
    }

    private boolean evaluateHoldingDays(Map<String, Object> condition, int index, int entryDay) {
        if (entryDay < 0) {
            return false;
        }
        int maxDays = intVal(condition.get("max"), 5);
        return index - entryDay >= maxDays;
    }

    // ==================== 新增条件类型实现 ====================

    /**
     * 情绪极值判断 — 使用 RSI 指标作为情绪代理
     * RSI < oversold_threshold (默认20) = oversold（情绪极度低迷，买入信号）
     * RSI > overbought_threshold (默认80) = overbought（情绪极度亢奋，卖出信号）
     */
    private boolean evaluateSentimentExtreme(Map<String, Object> condition, List<StockDailyData> data,
                                             int index, Map<String, Object> parameters) {
        int rsiPeriod = intParam(parameters, "sentiment_period", 14);
        if (index < rsiPeriod + 1) {
            return false;
        }
        double rsi = calculateRsi(data, index, rsiPeriod);
        String level = stringVal(condition.get("level"));
        if ("oversold".equalsIgnoreCase(level)) {
            double threshold = intParam(parameters, "oversold_threshold", 20);
            return rsi <= threshold;
        }
        if ("overbought".equalsIgnoreCase(level)) {
            double threshold = intParam(parameters, "overbought_threshold", 80);
            return rsi >= threshold;
        }
        return false;
    }

    /**
     * 事件触发判断 — 基于当日涨跌幅模拟事件触发
     * category: positive → 当日涨幅 ≥ 阈值（默认 5%）
     * category: negative → 当日跌幅 ≥ 阈值（默认 -5%）
     */
    private boolean evaluateEventTrigger(Map<String, Object> condition, List<StockDailyData> data,
                                         int index, Map<String, Object> parameters) {
        Double changePct = data.get(index).getChangePct();
        if (changePct == null) {
            return false;
        }
        String category = stringVal(condition.get("category"));
        if ("positive".equalsIgnoreCase(category)) {
            double threshold = doubleVal(condition.get("min_change"), 5.0);
            return changePct >= threshold;
        }
        if ("negative".equalsIgnoreCase(category)) {
            double threshold = doubleVal(condition.get("max_change"), -5.0);
            return changePct <= threshold;
        }
        return false;
    }

    /**
     * 基本面过滤 — 从 condition/parameters 中读取基本面阈值并校验
     * 支持 revenue_growth_min, roe_min, max_pe 等指标
     * 注：backtest 环境下基本面数据从 parameters 注入
     */
    private boolean evaluateFundamentalFilter(Map<String, Object> condition, Map<String, Object> parameters) {
        // 点时基本面明确不可用时，入场过滤失败（避免无数据却放行）
        if (Boolean.FALSE.equals(parameters.get("fundamentals_available"))) {
            return false;
        }
        // 营收增速校验
        double revenueMin = doubleVal(condition.get("revenue_growth_min"),
                doubleParam(parameters, "min_revenue_growth", 0));
        if (revenueMin > 0 && !parameters.containsKey("actual_revenue_growth")) {
            // 要求增速但未注入实际值：有点时框架时失败；无框架（单测）仍用旧默认放行
            if (parameters.containsKey("fundamentals_available")) {
                return false;
            }
        }
        double revenueActual = doubleParam(parameters, "actual_revenue_growth", Double.MAX_VALUE);
        if (revenueActual < revenueMin) {
            return false;
        }
        // ROE 校验
        double roeMin = doubleVal(condition.get("roe_min"),
                doubleParam(parameters, "min_roe", 0));
        if (roeMin > 0 && !parameters.containsKey("actual_roe")
                && parameters.containsKey("fundamentals_available")) {
            return false;
        }
        double roeActual = doubleParam(parameters, "actual_roe", Double.MAX_VALUE);
        if (roeActual < roeMin) {
            return false;
        }
        // PE 上限校验
        double maxPe = doubleVal(condition.get("max_pe"),
                doubleParam(parameters, "max_pe", Double.MAX_VALUE));
        if (maxPe < Double.MAX_VALUE && parameters.containsKey("fundamentals_available")
                && !parameters.containsKey("actual_pe")) {
            return false;
        }
        double peActual = doubleParam(parameters, "actual_pe", 0);
        if (maxPe < Double.MAX_VALUE && peActual > maxPe) {
            return false;
        }
        return true;
    }

    /**
     * 价格接近支撑位 — 检查价格是否接近均线或箱体低点
     * 支撑位 = 均线 或 箱体低点，取更近者
     */
    private boolean evaluatePriceNearSupport(Map<String, Object> condition, List<StockDailyData> data,
                                             int index, Map<String, Object> parameters) {
        int maPeriod = intParam(parameters, "support_ma", 60);
        int lookback = intParam(parameters, "lookback_days", 20);
        if (index < Math.max(maPeriod, lookback)) {
            return false;
        }
        double price = data.get(index).getClosePrice();
        double tolerance = doubleVal(condition.get("tolerance_pct"), 3.0);

        // 检查均线支撑
        double ma = avgClose(data, index, maPeriod);
        if (ma > 0 && Math.abs(price - ma) / ma * 100 <= tolerance) {
            return true;
        }
        // 检查箱体低点支撑
        double low = boxLow(data, index, lookback);
        return low > 0 && (price - low) / low * 100 <= tolerance;
    }

    /**
     * 基本面恶化 — 从 parameters 中获取恶化信号
     * 检查 revenue_decline 或 profit_decline 标志
     */
    private boolean evaluateFundamentalDeterioration(Map<String, Object> condition, Map<String, Object> parameters) {
        if (Boolean.FALSE.equals(parameters.get("fundamentals_available"))) {
            return false;
        }
        // 检查营收下滑
        boolean revenueDecline = Boolean.TRUE.equals(parameters.get("revenue_decline"));
        // 检查利润下滑
        boolean profitDecline = Boolean.TRUE.equals(parameters.get("profit_decline"));
        // 检查 ROE 下降
        boolean roeDecline = Boolean.TRUE.equals(parameters.get("roe_decline"));
        // 任一恶化信号触发即返回 true
        return revenueDecline || profitDecline || roeDecline;
    }

    // ==================== 共享形态条件（BarPatternConditions） ====================

    private boolean evaluatePriceNearLow(Map<String, Object> condition, List<StockDailyData> data,
                                         int index, Map<String, Object> parameters) {
        int lookback = intVal(condition.get("lookback"), intParam(parameters, "lookback_days", 60));
        double maxPosition = doubleVal(condition.get("max_position"),
                doubleParam(parameters, "max_position", 0.25));
        return BarPatternConditions.priceNearLow(data, index, lookback, maxPosition);
    }

    private boolean evaluateConsecutiveVolumeDays(Map<String, Object> condition, List<StockDailyData> data,
                                                  int index, Map<String, Object> parameters) {
        int days = intVal(condition.get("days"), intParam(parameters, "consecutive_days", 2));
        double multiple = doubleVal(condition.get("multiple"),
                doubleParam(parameters, "volume_multiple", 2.0));
        int avgPeriod = intVal(condition.get("avg_period"), intParam(parameters, "ma_period", 20));
        return BarPatternConditions.consecutiveVolumeDays(data, index, days, multiple, avgPeriod);
    }

    private boolean evaluateYangCoversYin(Map<String, Object> condition, List<StockDailyData> data,
                                          int index, Map<String, Object> parameters) {
        int yinCount = intVal(condition.get("yin_count"), intParam(parameters, "yin_count", 3));
        return BarPatternConditions.oneYangCoversYin(data, index, yinCount);
    }

    private boolean evaluateVolumeAmplify(Map<String, Object> condition, List<StockDailyData> data,
                                          int index, Map<String, Object> parameters) {
        double minRatio = doubleVal(condition.get("min_ratio"),
                doubleParam(parameters, "volume_amplify", 1.5));
        return BarPatternConditions.volumeAmplify(data, index) >= minRatio;
    }

    private boolean evaluateMomentumUp(Map<String, Object> condition, List<StockDailyData> data,
                                       int index, Map<String, Object> parameters) {
        double minChange = doubleVal(condition.get("min_change"),
                doubleParam(parameters, "strong_change_pct", 1.5));
        return BarPatternConditions.momentumUp(data, index, minChange);
    }

    private boolean evaluateBollUpperBreak(Map<String, Object> condition, List<StockDailyData> data,
                                           int index, Map<String, Object> parameters) {
        int period = intVal(condition.get("period"), intParam(parameters, "boll_period", 20));
        double stdMult = doubleVal(condition.get("std_mult"),
                doubleParam(parameters, "boll_std", 2.0));
        return BarPatternConditions.bollUpperBreak(data, index, period, stdMult);
    }

    private boolean evaluateAmplitudeBelow(Map<String, Object> condition, List<StockDailyData> data,
                                           int index, Map<String, Object> parameters) {
        int lookback = intVal(condition.get("lookback"), intParam(parameters, "lookback_days", 20));
        double maxPct = doubleVal(condition.get("max_pct"),
                doubleParam(parameters, "max_amplitude_pct", 25));
        return BarPatternConditions.amplitudeBelow(data, index, lookback, maxPct);
    }

    private boolean evaluateChannelBreakout(Map<String, Object> condition, List<StockDailyData> data,
                                            int index, Map<String, Object> parameters) {
        int lookback = intVal(condition.get("lookback"), intParam(parameters, "entry_channel", 20));
        return BarPatternConditions.channelBreakout(data, index, lookback);
    }

    private boolean evaluateChannelBreakdown(Map<String, Object> condition, List<StockDailyData> data,
                                             int index, Map<String, Object> parameters) {
        int lookback = intVal(condition.get("lookback"), intParam(parameters, "exit_channel", 10));
        return BarPatternConditions.channelBreakdown(data, index, lookback);
    }

    private boolean evaluateAtrStop(Map<String, Object> condition, List<StockDailyData> data,
                                    int index, Map<String, Object> parameters, double entryPrice) {
        int period = intVal(condition.get("period"), intParam(parameters, "atr_period", 20));
        double multiplier = doubleVal(condition.get("multiplier"),
                doubleParam(parameters, "atr_multiplier", 2.0));
        return BarPatternConditions.atrStop(data, index, period, multiplier, entryPrice);
    }

    private boolean evaluateBollLowerTouch(Map<String, Object> condition, List<StockDailyData> data,
                                           int index, Map<String, Object> parameters) {
        int period = intVal(condition.get("period"), intParam(parameters, "boll_period", 20));
        double stdMult = doubleVal(condition.get("std_mult"),
                doubleParam(parameters, "boll_std", 2.0));
        return BarPatternConditions.bollLowerTouch(data, index, period, stdMult);
    }

    private boolean evaluateBollUpperTouch(Map<String, Object> condition, List<StockDailyData> data,
                                           int index, Map<String, Object> parameters) {
        int period = intVal(condition.get("period"), intParam(parameters, "boll_period", 20));
        double stdMult = doubleVal(condition.get("std_mult"),
                doubleParam(parameters, "boll_std", 2.0));
        return BarPatternConditions.bollUpperTouch(data, index, period, stdMult);
    }

    private boolean evaluateBollMidReclaim(Map<String, Object> condition, List<StockDailyData> data,
                                           int index, Map<String, Object> parameters) {
        int period = intVal(condition.get("period"), intParam(parameters, "boll_period", 20));
        return BarPatternConditions.bollMidReclaim(data, index, period);
    }

    // ==================== 辅助计算方法 ====================

    /**
     * 计算 RSI 指标（Relative Strength Index）。
     *
     * <p>统一委托 {@link StockBarMath#rsi(List, int, int)}（基于收盘价差），
     * 与技术分析/告警链路口径保持一致。</p>
     */
    private double calculateRsi(List<StockDailyData> data, int index, int period) {
        return rsi(data, index, period);
    }
}
