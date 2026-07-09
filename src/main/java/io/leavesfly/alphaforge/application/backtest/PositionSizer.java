package io.leavesfly.alphaforge.application.backtest;

import io.leavesfly.alphaforge.application.strategy.condition.BarPatternConditions;
import io.leavesfly.alphaforge.application.strategy.condition.ValueCoercion;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.util.List;
import java.util.Map;

/**
 * 仓位 sizing：固定比例，或按 ATR 风险预算动态缩放。
 *
 * <p>ATR 模式（海龟风格）：用 {@code risk_fraction × 现金} 作为单笔风险预算，
 * 除以 {@code ATR × multiplier} 得到可买股数，再换算为仓位比例，并受
 * {@code max_position}（即 YAML {@code position_size}）上限约束。</p>
 */
public final class PositionSizer {

    private PositionSizer() {
    }

    /**
     * @param basePositionSize YAML position_size，作为固定仓位或 ATR 模式上限
     * @param sizingConfig     position_sizing 段；null/空则固定仓位
     * @param data             历史 K 线
     * @param index            决策时点（通常为信号日）
     * @return (0, 1] 仓位比例
     */
    public static double resolve(double basePositionSize,
                                 Map<String, Object> sizingConfig,
                                 List<StockDailyData> data,
                                 int index) {
        double cap = clamp(basePositionSize, 0.01, 1.0);
        if (sizingConfig == null || sizingConfig.isEmpty()) {
            return cap;
        }
        String mode = ValueCoercion.stringVal(sizingConfig.get("mode"));
        if (!"atr".equalsIgnoreCase(mode)) {
            return cap;
        }
        int period = ValueCoercion.intVal(sizingConfig.get("atr_period"), 20);
        double multiplier = ValueCoercion.doubleVal(sizingConfig.get("atr_multiplier"), 2.0);
        double riskFraction = ValueCoercion.doubleVal(sizingConfig.get("risk_fraction"), 0.01);
        riskFraction = clamp(riskFraction, 0.001, 0.1);
        multiplier = Math.max(0.5, multiplier);

        double atr = BarPatternConditions.atr(data, index, period);
        if (Double.isNaN(atr) || atr <= 0 || data == null || index < 0 || index >= data.size()) {
            return cap;
        }
        Double closeObj = data.get(index).getClosePrice();
        if (closeObj == null || closeObj <= 0) {
            return cap;
        }
        double price = closeObj;
        // 风险预算 / 止损距离 ≈ 仓位比例；止损距离 = ATR × multiplier
        double stopDistance = atr * multiplier;
        double atrPosition = riskFraction * price / stopDistance;
        return clamp(Math.min(cap, atrPosition), 0.01, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
