package io.leavesfly.alphaforge.application.strategy.condition;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.factor.ClassicFactorLibrary;

import java.util.List;
import java.util.Map;

/**
 * 经典因子条件求值（回测 / 综合评分共用）。
 *
 * <p>委托 {@link ClassicFactorLibrary} 计算点时因子值，再与 YAML 中的
 * {@code min}/{@code max} 阈值比较。因子名须在 {@link ClassicFactorLibrary#FACTOR_NAMES} 内。</p>
 */
public final class FactorConditions {

    private static final ClassicFactorLibrary LIBRARY = new ClassicFactorLibrary();

    private FactorConditions() {
    }

    public static boolean supports(String factorName) {
        return factorName != null && LIBRARY.supports(factorName);
    }

    /**
     * 计算截至 {@code index} 的因子值。
     *
     * @return 因子值；数据不足或不支持返回 {@link Double#NaN}
     */
    public static double compute(String factorName, List<StockDailyData> data, int index) {
        if (data == null || index < 0 || index >= data.size() || !supports(factorName)) {
            return Double.NaN;
        }
        return LIBRARY.compute(factorName, data.subList(0, index + 1));
    }

    /**
     * 因子值是否落在 [{@code min}, {@code max}]（缺省端不约束）。
     */
    public static boolean matches(String factorName, List<StockDailyData> data, int index,
                                  Double min, Double max) {
        double value = compute(factorName, data, index);
        if (Double.isNaN(value)) {
            return false;
        }
        if (min != null && !Double.isNaN(min) && value < min) {
            return false;
        }
        if (max != null && !Double.isNaN(max) && value > max) {
            return false;
        }
        return true;
    }

    /**
     * 从回测条件 Map 求值：{@code name} + 可选 {@code min}/{@code max}。
     */
    public static boolean evaluate(Map<String, Object> condition, List<StockDailyData> data, int index) {
        String name = ValueCoercion.stringVal(condition.get("name"));
        if (name.isEmpty()) {
            name = ValueCoercion.stringVal(condition.get("factor"));
        }
        if (!supports(name)) {
            return false;
        }
        Double min = condition.containsKey("min")
                ? ValueCoercion.doubleVal(condition.get("min"), Double.NaN) : null;
        Double max = condition.containsKey("max")
                ? ValueCoercion.doubleVal(condition.get("max"), Double.NaN) : null;
        if (min != null && Double.isNaN(min)) {
            min = null;
        }
        if (max != null && Double.isNaN(max)) {
            max = null;
        }
        if (min == null && max == null) {
            return !Double.isNaN(compute(name, data, index));
        }
        return matches(name, data, index, min, max);
    }

    /** 因子计算通常需要的最少历史天数（用于 warmup 估算）。 */
    public static int warmupDays(String factorName) {
        if (factorName == null) {
            return 0;
        }
        return switch (factorName) {
            case "momentum_60" -> 60;
            case "momentum_20", "volatility_20", "turnover_mean_20",
                 "ma_gap_20", "amihud_illiquidity_20", "max_return_20" -> 20;
            case "reversal_5" -> 5;
            case "rsi_14" -> 14;
            case "volume_ratio" -> 6;
            default -> 20;
        };
    }
}
