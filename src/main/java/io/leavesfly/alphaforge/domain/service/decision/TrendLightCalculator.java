package io.leavesfly.alphaforge.domain.service.decision;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 势灯（市场是否认同）— 趋势分 + MA60/MA200/周线结构 + 大盘环境。
 *
 * <p>红：收盘 &lt; MA200 或 趋势分 &lt; 45；黄：趋势分 &lt; 60、收盘 &lt; MA60、
 * 周线破位或大盘 risk-off；其余绿。大盘 risk-off 时势灯封顶黄。</p>
 */
public final class TrendLightCalculator {

    public static final double TREND_GREEN = 60.0;
    public static final double TREND_RED = 45.0;
    /** 动量 55 / 相对强度 35 / 趋势效率 10（无基准时相对强度权重并入动量） */
    public static final double W_MOMENTUM = 0.55;
    public static final double W_REL_STRENGTH = 0.35;
    public static final double W_EFFICIENCY = 0.10;

    private TrendLightCalculator() {
    }

    /**
     * 势灯评估。
     *
     * @param history     个股日 K（升序，≥ MIN_BARS 由引擎保证）
     * @param benchmark   基准日 K（可空）
     */
    public static Assessment calculate(List<StockDailyData> history,
                                       List<StockDailyData> benchmark) {
        double trendScore = trendScore(history, benchmark);
        double last = IndicatorMath.lastClose(history);
        double ma20 = IndicatorMath.sma(history, 20);
        double ma60 = IndicatorMath.sma(history, 60);
        double ma200 = IndicatorMath.sma(history, 200);

        LightResult light = new LightResult(LightColor.GREEN);
        boolean red = false;
        boolean yellow = false;

        boolean belowMa200 = !Double.isNaN(ma200) && last < ma200;
        if (belowMa200) {
            red = true;
            light.addReason(String.format("收盘 %.2f 低于 MA200 %.2f，长期趋势逆势（红灯）", last, ma200));
        }

        if (trendScore < TREND_RED) {
            red = true;
            light.addReason(String.format("趋势分 %.1f 低于 %.0f，动能不足（红灯）", trendScore, TREND_RED));
        } else if (trendScore < TREND_GREEN) {
            yellow = true;
            light.addReason(String.format("趋势分 %.1f 未达 %.0f，动能一般", trendScore, TREND_GREEN));
        }

        boolean belowMa60 = !Double.isNaN(ma60) && last < ma60;
        if (belowMa60 && !belowMa200) {
            yellow = true;
            light.addReason(String.format("收盘 %.2f 低于 MA60 %.2f，中期趋势走弱", last, ma60));
        }

        // 周线结构：周收盘 < 周 MA30（30 周样本）
        boolean weeklyBroken = false;
        List<Double> weekly = IndicatorMath.weeklyCloses(history);
        if (weekly.size() >= 30) {
            double wma30 = 0;
            for (int i = weekly.size() - 30; i < weekly.size(); i++) {
                wma30 += weekly.get(i);
            }
            wma30 /= 30;
            double wlast = weekly.get(weekly.size() - 1);
            weeklyBroken = wlast < wma30;
            if (weeklyBroken) {
                yellow = true;
                light.addReason(String.format("周线收盘 %.2f 低于周线 MA30 %.2f，周线结构走坏", wlast, wma30));
            }
        } else {
            light.addReason("周线样本不足 30 根，跳过周线结构检查");
        }

        // 大盘环境：None = 基准缺失或样本不足（诚实标注，与 False「已检查且非 risk-off」区分）
        Boolean benchRiskOff = null;
        if (benchmark != null && !benchmark.isEmpty()) {
            if (benchmark.size() >= 200) {
                double benchLast = IndicatorMath.lastClose(benchmark);
                double benchMa200 = IndicatorMath.sma(benchmark, 200);
                benchRiskOff = benchLast < benchMa200;
                if (benchRiskOff) {
                    yellow = true;
                    light.addReason("基准收盘低于其 MA200（大盘 risk-off），势灯封顶黄");
                }
            } else {
                light.addReason("基准样本不足 200 根，跳过大盘环境检查");
            }
        }

        light.setColor(red ? LightColor.RED : (yellow ? LightColor.YELLOW : LightColor.GREEN));
        // 大盘 risk-off 时绿灯封顶黄（红保持红）
        if (Boolean.TRUE.equals(benchRiskOff)) {
            light.capAt(LightColor.YELLOW);
        }
        if (light.getColor() == LightColor.GREEN) {
            light.addReason("价格站上 MA60/MA200、周线与大盘环境完好，趋势结构健康");
        }
        light.putDetail("trendScore", round1(trendScore));
        light.putDetail("belowMa200", belowMa200);
        light.putDetail("belowMa60", belowMa60);
        light.putDetail("weeklyBroken", weeklyBroken);
        light.putDetail("benchRiskOff", benchRiskOff);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ma20", ma20);
        snapshot.put("ma60", ma60);
        snapshot.put("ma200", ma200);

        return new Assessment(light, trendScore, snapshot);
    }

    /**
     * 趋势分（0~100）：风险调整动量 55% + 相对基准强度 35% + Kaufman 趋势效率 10%。
     * 无基准时相对强度权重并入动量并标注降级。
     */
    public static double trendScore(List<StockDailyData> history, List<StockDailyData> benchmark) {
        double ret60 = IndicatorMath.periodReturn(history, 60);
        double vol60 = IndicatorMath.annualizedVol(history, 60);
        double ram;
        if (vol60 > 1e-12) {
            ram = ret60 / vol60;
        } else {
            ram = ret60 > 0 ? 4.0 : (ret60 < 0 ? -4.0 : 0.0);
        }
        double momScore = IndicatorMath.tanhScore(ram);

        double er = IndicatorMath.efficiencyRatio(history, 20);
        er = Double.isNaN(er) ? 0.0 : er;
        double erScore = er * 100.0;

        if (benchmark != null && benchmark.size() >= 61) {
            double benchRet60 = IndicatorMath.periodReturn(benchmark, 60);
            double excess60 = ret60 - benchRet60;
            double rsScore = IndicatorMath.tanhScore(5.0 * excess60);
            return W_MOMENTUM * momScore + W_REL_STRENGTH * rsScore + W_EFFICIENCY * erScore;
        }
        return (W_MOMENTUM + W_REL_STRENGTH) * momScore + W_EFFICIENCY * erScore;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** 势灯评估产出：灯 + 趋势分 + 快照（ma20/ma60/ma200） */
    public record Assessment(LightResult light, double trendScore, Map<String, Object> snapshot) {
    }
}
