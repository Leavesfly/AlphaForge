package io.leavesfly.stock.application.strategy.condition;

import io.leavesfly.stock.domain.model.entity.StockDailyData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.leavesfly.stock.application.strategy.condition.StockBarMath.*;
import static io.leavesfly.stock.application.strategy.condition.ValueCoercion.*;

/**
 * 回测条件求值器：统一 backtest YAML 中 type 字段的条件实现。
 */
@Component
public class BacktestConditionEvaluator {

    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "ma_cross", "volume_breakout", "change_below", "stop_loss", "take_profit",
            "price_above_ma", "price_below_ma", "ma_arrangement", "trend_above", "pullback_to",
            "volume_shrink", "near_box_low", "near_box_high", "box_break_down",
            "consecutive_up", "first_pullback", "break_below_ma", "holding_days", "wave_position"
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
            default -> false;
        };
    }

    /** 简化波浪第三浪启动：均线多头 + 趋势向上 + 放量 */
    private boolean evaluateWavePosition(Map<String, Object> condition, List<StockDailyData> data,
                                         int index, Map<String, Object> parameters) {
        if (!"wave3_start".equalsIgnoreCase(stringVal(condition.get("position")))) {
            return false;
        }
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
}
