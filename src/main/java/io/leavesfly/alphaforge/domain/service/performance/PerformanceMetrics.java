package io.leavesfly.alphaforge.domain.service.performance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一绩效指标（收益类字段为小数比例，如 0.15 表示 15%）。
 *
 * @param totalReturn        累计收益率
 * @param annualizedReturn   年化收益率
 * @param annualizedVolatility 年化波动率
 * @param sharpeRatio        夏普比率
 * @param sortinoRatio       索提诺比率（仅惩罚下行波动）
 * @param calmarRatio        卡尔玛比率（年化收益 / 最大回撤）
 * @param maxDrawdown        最大回撤（正数，如 0.2 表示 -20%）
 * @param winRate            日胜率（收益为正的交易日占比）
 * @param alpha              相对基准的年化 alpha（无基准时为 0）
 * @param beta               相对基准的 beta（无基准时为 0）
 * @param informationRatio   信息比率（超额收益 / 跟踪误差，无基准时为 0）
 * @param periods            样本期数
 */
public record PerformanceMetrics(
        double totalReturn,
        double annualizedReturn,
        double annualizedVolatility,
        double sharpeRatio,
        double sortinoRatio,
        double calmarRatio,
        double maxDrawdown,
        double winRate,
        double alpha,
        double beta,
        double informationRatio,
        int periods
) {

    /** 转为可直接序列化的 Map（收益类字段转百分数，保留 4 位） */
    public Map<String, Object> toPercentMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_return_pct", pct(totalReturn));
        m.put("annualized_return_pct", pct(annualizedReturn));
        m.put("annualized_volatility_pct", pct(annualizedVolatility));
        m.put("sharpe_ratio", round(sharpeRatio, 4));
        m.put("sortino_ratio", round(sortinoRatio, 4));
        m.put("calmar_ratio", round(calmarRatio, 4));
        m.put("max_drawdown_pct", pct(maxDrawdown));
        m.put("win_rate_pct", pct(winRate));
        m.put("alpha_pct", pct(alpha));
        m.put("beta", round(beta, 4));
        m.put("information_ratio", round(informationRatio, 4));
        m.put("periods", periods);
        return m;
    }

    private static double pct(double v) {
        return round(v * 100.0, 4);
    }

    private static double round(double v, int scale) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        double f = Math.pow(10, scale);
        return Math.round(v * f) / f;
    }
}
