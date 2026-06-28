package io.leavesfly.alphaforge.application.strategy.condition;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.util.List;
import java.util.Map;

/**
 * K 线通用计算工具，供回测/评分引擎共享。
 */
public final class StockBarMath {

    private StockBarMath() {
    }

    public static double avgClose(List<StockDailyData> data, int end, int period) {
        double sum = 0;
        for (int i = end - period + 1; i <= end; i++) {
            sum += data.get(i).getClosePrice();
        }
        return sum / period;
    }

    public static long avgVolume(List<StockDailyData> data, int start, int end) {
        long sum = 0;
        int count = 0;
        for (int i = start; i <= end; i++) {
            sum += data.get(i).getVolume();
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    public static double boxLow(List<StockDailyData> data, int index, int lookback) {
        double low = Double.MAX_VALUE;
        for (int i = index - lookback + 1; i <= index; i++) {
            double barLow = data.get(i).getLowPrice() != null
                    ? data.get(i).getLowPrice() : data.get(i).getClosePrice();
            low = Math.min(low, barLow);
        }
        return low;
    }

    public static double boxHigh(List<StockDailyData> data, int index, int lookback) {
        double high = Double.MIN_VALUE;
        for (int i = index - lookback + 1; i <= index; i++) {
            double barHigh = data.get(i).getHighPrice() != null
                    ? data.get(i).getHighPrice() : data.get(i).getClosePrice();
            high = Math.max(high, barHigh);
        }
        return high;
    }

    public static int maPeriodFromCondition(Map<String, Object> condition) {
        int max = 0;
        Object ma = condition.get("ma");
        if (ma instanceof List<?> list) {
            for (Object item : list) {
                max = Math.max(max, ValueCoercion.intVal(item, 0));
            }
        } else if (ma != null) {
            max = Math.max(max, ValueCoercion.intVal(ma, 0));
        }
        max = Math.max(max, maPeriod(condition.get("fast"), Map.of(), "fast_period", 0));
        max = Math.max(max, maPeriod(condition.get("slow"), Map.of(), "slow_period", 0));
        return max;
    }

    public static int maPeriod(Object maLabel, Map<String, Object> parameters, String paramKey, int defaultValue) {
        if (maLabel != null) {
            String text = String.valueOf(maLabel).toUpperCase();
            if (text.startsWith("MA")) {
                return ValueCoercion.intVal(text.substring(2), defaultValue);
            }
        }
        return ValueCoercion.intParam(parameters, paramKey, defaultValue);
    }
}
