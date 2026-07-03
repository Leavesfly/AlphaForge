package io.leavesfly.alphaforge.domain.service.factor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 因子分层回测结果。
 *
 * @param quantiles          分层数（如 5）
 * @param layerMeanReturns   各层平均前瞻收益（第 0 层为因子值最低组，收益单位与输入一致）
 * @param longShortReturn    多空组合平均收益（最高层 - 最低层）
 * @param monotonicity       单调性（层序号与层收益的 Spearman 秩相关，[-1,1]，越接近 ±1 越单调）
 * @param icMean             IC 均值（每期因子值与前瞻收益的 Spearman 相关的均值）
 * @param icStd              IC 标准差
 * @param icIR               IC 信息比率（icMean / icStd）
 * @param icWinRate          IC 为正的期数占比
 * @param periods            有效期数
 */
public record FactorLayerResult(
        int quantiles,
        double[] layerMeanReturns,
        double longShortReturn,
        double monotonicity,
        double icMean,
        double icStd,
        double icIR,
        double icWinRate,
        int periods
) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quantiles", quantiles);
        m.put("layer_mean_returns", layerMeanReturns);
        m.put("long_short_return", round(longShortReturn));
        m.put("monotonicity", round(monotonicity));
        m.put("ic_mean", round(icMean));
        m.put("ic_std", round(icStd));
        m.put("ic_ir", round(icIR));
        m.put("ic_win_rate", round(icWinRate));
        m.put("periods", periods);
        return m;
    }

    private static double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        return Math.round(v * 1e6) / 1e6;
    }
}
