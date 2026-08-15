package io.leavesfly.alphaforge.domain.service.decision;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 买点三灯引擎 — 门面编排：波动率缩放 → 价/势/时三灯 → 决策矩阵 → 交易计划/左侧计划
 * → 持仓联动 → 证据链。纯 Java 无 Spring 依赖，输出一次成型的 {@link LightsResult}。
 *
 * <p>K 线不足 {@link IndicatorMath#MIN_BARS} 时输出 unrated（三灰灯），不用猜测补齐；
 * 评估仅使用已完成的 K 线，无前视。</p>
 */
public final class ThreeLightsEngine {

    /** 波动率缩放窗口（日） */
    public static final int VOL_WINDOW = 20;
    public static final double VOL_K_MIN = 0.8;
    public static final double VOL_K_MAX = 1.4;
    private static final List<String> LIGHT_NAMES = List.of("value", "trend", "timing");

    private ThreeLightsEngine() {
    }

    public static LightsResult evaluate(ThreeLightsInput input) {
        List<StockDailyData> history = input.getHistory() != null ? input.getHistory() : List.of();
        int nBars = history.size();
        LocalDate asofDate = !history.isEmpty() ? history.get(history.size() - 1).getTradeDate() : null;
        String asof = asofDate != null ? asofDate.toString() : "";

        if (nBars < IndicatorMath.MIN_BARS) {
            return unrated(input, nBars, asof, history);
        }

        double volK = volRegime(history);
        LightResult value = ValueLightCalculator.calculate(input.getSt(), input.getNetAssetPerShare(),
                input.getEpsRecent(), input.getValuationPercentile(), input.getValuationNote());
        TrendLightCalculator.Assessment trend = TrendLightCalculator.calculate(history, input.getBenchmarkHistory());
        TimingLightCalculator.Assessment timing = TimingLightCalculator.calculate(history, volK,
                input.getRiskEvents(), asofDate);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.putAll(trend.snapshot());
        snapshot.putAll(timing.snapshot());
        snapshot.put("close", IndicatorMath.lastClose(history));
        snapshot.put("volK", Math.round(volK * 1000.0) / 1000.0);

        VerdictMatrix.Decision decision = VerdictMatrix.decide(value, trend.light(), timing.light(),
                trend.trendScore(), snapshot);
        Verdict verdict = decision.verdict();

        // 交易计划：仅行动态生成（trend_entry/trend_only/wait_pullback）
        TradePlan plan = null;
        if (verdict.isActionable()) {
            double close = IndicatorMath.lastClose(history);
            double ma20 = IndicatorMath.sma(history, 20);
            double atr14 = IndicatorMath.atr(history, 14);
            plan = TradePlanBuilder.attachSizing(TradePlanBuilder.build(close, ma20, atr14),
                    input.getCapitalYuan(), input.getRiskMultiplier(), input.getLotSize(),
                    input.getMaxPositionPct());
        }

        // 左侧分批计划：仅左侧观察且价深绿 + 无硬伤
        Map<String, Object> leftPlan = verdict == Verdict.LEFT_WATCH
                ? VerdictMatrix.buildLeftPlan(value, decision) : null;

        // 持仓联动：只改操作建议，不改灯色
        Map<String, Object> position = null;
        if (input.getPositionCost() != null && input.getPositionCost() > 0) {
            double atr14 = IndicatorMath.atr(history, 14);
            Overlay overlay = positionOverlay(input, history, atr14, verdict, value, trend.light());
            verdict = overlay.verdict();
            position = overlay.detail();
        }

        List<LightsResult.Evidence> evidence = buildEvidence(value, trend, timing, volK, snapshot);

        Map<String, LightResult> lights = new LinkedHashMap<>();
        lights.put("value", value);
        lights.put("trend", trend.light());
        lights.put("timing", timing.light());

        return LightsResult.builder(input.getStockCode())
                .stockName(input.getStockName())
                .verdict(verdict)
                .lights(lights)
                .trendScore(Math.round(trend.trendScore() * 10.0) / 10.0)
                .snapshot(snapshot)
                .decision(decision)
                .plan(plan)
                .leftPlan(leftPlan)
                .position(position)
                .marketContext(input.getMarketContext())
                .evidence(evidence)
                .asof(asof)
                .nBars(nBars)
                .build();
    }

    /** K 线不足：unrated + 三灰灯，诚实标注不猜测 */
    private static LightsResult unrated(ThreeLightsInput input, int nBars, String asof,
                                        List<StockDailyData> history) {
        String reason = String.format("有效 K 线仅 %d 根，低于评估所需 %d 根（MA200/动量窗口无法形成），不用猜测补齐",
                nBars, IndicatorMath.MIN_BARS);
        Map<String, LightResult> lights = new LinkedHashMap<>();
        for (String name : LIGHT_NAMES) {
            LightResult gray = new LightResult(LightColor.GRAY);
            gray.addReason(reason);
            lights.put(name, gray);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (!history.isEmpty()) {
            snapshot.put("close", IndicatorMath.lastClose(history));
        }
        snapshot.put("nBars", nBars);
        return LightsResult.builder(input.getStockCode())
                .stockName(input.getStockName())
                .verdict(Verdict.UNRATED)
                .lights(lights)
                .trendScore(null)
                .snapshot(snapshot)
                .decision(new VerdictMatrix.Decision(Verdict.UNRATED,
                        "数据不足，无法评估",
                        List.of(String.format("补足历史至 %d 根以上", IndicatorMath.MIN_BARS))))
                .marketContext(input.getMarketContext())
                .evidence(List.of())
                .asof(asof)
                .nBars(nBars)
                .build();
    }

    /**
     * 波动率缩放因子：当前 20 日年化波动率 / 历史中位波动率，clamp 到 [0.8, 1.4]。
     * 数据不足时返回 1.0（退化为固定阈值）。
     */
    static double volRegime(List<StockDailyData> history) {
        int n = history.size();
        if (n < VOL_WINDOW * 3) {
            return 1.0;
        }
        double[] rets = new double[n - 1];
        for (int i = 1; i < n; i++) {
            rets[i - 1] = history.get(i).getClosePrice() / history.get(i - 1).getClosePrice() - 1.0;
        }
        List<Double> rollVols = new ArrayList<>();
        for (int k = VOL_WINDOW - 1; k < rets.length; k++) {
            double mean = 0;
            for (int i = k - VOL_WINDOW + 1; i <= k; i++) {
                mean += rets[i];
            }
            mean /= VOL_WINDOW;
            double variance = 0;
            for (int i = k - VOL_WINDOW + 1; i <= k; i++) {
                variance += (rets[i] - mean) * (rets[i] - mean);
            }
            variance /= VOL_WINDOW;
            rollVols.add(Math.sqrt(variance) * Math.sqrt(252.0));
        }
        if (rollVols.size() < VOL_WINDOW) {
            return 1.0;
        }
        double current = rollVols.get(rollVols.size() - 1);
        double median = median(rollVols);
        if (median <= 0 || Double.isNaN(current)) {
            return 1.0;
        }
        return Math.max(VOL_K_MIN, Math.min(VOL_K_MAX, current / median));
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    /** 持仓联动 overlay：势红或价硬伤 → reduce_risk（不等待回本） */
    private static Overlay positionOverlay(ThreeLightsInput input, List<StockDailyData> history,
                                           double atr14, Verdict verdict, LightResult value,
                                           LightResult trend) {
        double last = IndicatorMath.lastClose(history);
        double cost = input.getPositionCost();
        Double shares = input.getPositionShares();
        double pnlPct = cost > 0 ? last / cost - 1.0 : Double.NaN;
        Double stopRef = !Double.isNaN(atr14) && atr14 > 0 ? round2(last - 2.0 * atr14) : null;

        String advice;
        if (trend.getColor() == LightColor.RED
                || Boolean.TRUE.equals(value.getDetail().get("hardFlaw"))) {
            verdict = Verdict.REDUCE_RISK;
            advice = "趋势结构已破坏（或基本面硬伤），按纪律应减仓或离场，不等待回本";
        } else if (verdict == Verdict.TREND_ENTRY || verdict == Verdict.TREND_ONLY) {
            advice = "继续持有；回踩 MA20 可按交易计划加仓";
            if (verdict == Verdict.TREND_ONLY) {
                advice += "（估值偏高，加仓宜谨慎）";
            }
        } else {
            advice = "继续持有观察，不加仓；跌破止损参考位应离场";
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("cost", round4(cost));
        detail.put("shares", shares);
        detail.put("marketValue", shares != null ? round2(shares * last) : null);
        detail.put("pnlPct", Double.isNaN(pnlPct) ? null : round4(pnlPct));
        detail.put("stopRef", stopRef);
        detail.put("stopDistancePct", stopRef != null && stopRef > 0 ? round4(last / stopRef - 1.0) : null);
        detail.put("advice", advice);
        detail.put("source", input.getPositionSource());
        return new Overlay(verdict, detail);
    }

    private record Overlay(Verdict verdict, Map<String, Object> detail) {
    }

    /** 结构化证据链（编号 E01…，Agent 转述可引用） */
    private static List<LightsResult.Evidence> buildEvidence(LightResult value,
                                                             TrendLightCalculator.Assessment trend,
                                                             TimingLightCalculator.Assessment timing,
                                                             double volK, Map<String, Object> snapshot) {
        List<LightsResult.Evidence> evidence = new ArrayList<>();
        int seq = 0;

        // 价灯
        if (Boolean.TRUE.equals(value.getDetail().get("hardFlaw"))) {
            evidence.add(new LightsResult.Evidence("E" + String.format("%02d", ++seq), "value",
                    "fundamental_hard_flaw", true, null, true, "red",
                    "价灯硬伤：" + value.getReasons().get(0)));
        }
        Object valObj = value.getDetail().get("valuationPercentile");
        if (valObj instanceof Double val) {
            boolean expensive = val > ValueLightCalculator.VAL_RED;
            boolean cheap = val <= ValueLightCalculator.VAL_GREEN;
            evidence.add(new LightsResult.Evidence("E" + String.format("%02d", ++seq), "value",
                    "valuation_percentile", round2(val), round2(ValueLightCalculator.VAL_RED), expensive,
                    expensive ? "red" : "none",
                    String.format("估值分位均值 %.0f%%，%s", val * 100,
                            expensive ? "相对自身历史高估" : cheap ? "相对自身历史偏低" : "处于历史中枢")));
        }

        // 势灯
        double trendScore = trend.trendScore();
        evidence.add(new LightsResult.Evidence("E" + String.format("%02d", ++seq), "trend",
                "trend_score", round1(trendScore), TrendLightCalculator.TREND_GREEN,
                trendScore < TrendLightCalculator.TREND_GREEN,
                trendScore < TrendLightCalculator.TREND_RED ? "red"
                        : trendScore < TrendLightCalculator.TREND_GREEN ? "yellow" : "none",
                String.format("趋势分 %.1f，%s", trendScore,
                        trendScore >= TrendLightCalculator.TREND_GREEN ? "≥60 动能达标" : "<60 动能不足")));
        Double close = asDouble(snapshot.get("close"));
        Double ma200 = asDouble(snapshot.get("ma200"));
        Double ma60 = asDouble(snapshot.get("ma60"));
        if (close != null && ma200 != null && !ma200.isNaN()) {
            boolean below = close < ma200;
            evidence.add(new LightsResult.Evidence("E" + String.format("%02d", ++seq), "trend",
                    "close_vs_ma200", round2(close), round2(ma200), below, below ? "red" : "none",
                    String.format("收盘 %.2f %s MA200(%.2f)，%s", close, below ? "<" : "≥", ma200,
                            below ? "长期趋势逆势，势灯红" : "长期趋势未破坏")));
        }
        if (close != null && ma60 != null && !ma60.isNaN()) {
            boolean below = close < ma60;
            evidence.add(new LightsResult.Evidence("E" + String.format("%02d", ++seq), "trend",
                    "close_vs_ma60", round2(close), round2(ma60), below, below ? "yellow" : "none",
                    String.format("收盘 %.2f %s MA60(%.2f)，%s", close, below ? "<" : "≥", ma60,
                            below ? "中期走弱" : "中期趋势健康")));
        }

        // 时灯
        Double dev20 = asDouble(snapshot.get("dev20"));
        if (dev20 != null && !dev20.isNaN()) {
            double devThreshold = TimingLightCalculator.DEV_BASE * volK;
            boolean hot = dev20 > devThreshold;
            evidence.add(new LightsResult.Evidence("E" + String.format("%02d", ++seq), "timing",
                    "ma20_deviation", round4(dev20), round4(devThreshold), hot, hot ? "red" : "none",
                    String.format("偏离 MA20 达 %+.1f%%%s", dev20 * 100,
                            hot ? String.format(" > %.0f%%，过热追高，等回踩", devThreshold * 100) : "，入场偏离度正常")));
        }
        Double rsi14 = asDouble(snapshot.get("rsi14"));
        if (rsi14 != null && !rsi14.isNaN()) {
            double rsiThreshold = TimingLightCalculator.RSI_BASE * volK;
            boolean hot = rsi14 > rsiThreshold;
            evidence.add(new LightsResult.Evidence("E" + String.format("%02d", ++seq), "timing",
                    "rsi14", round1(rsi14), round1(rsiThreshold), hot, hot ? "yellow" : "none",
                    String.format("RSI14=%.1f%s", rsi14, hot ? String.format(" > %.0f，短期过热", rsiThreshold) : "，未过热")));
        }
        for (String reason : timing.light().getReasons()) {
            if (reason.contains("风险事件")) {
                evidence.add(new LightsResult.Evidence("E" + String.format("%02d", ++seq), "timing",
                        "event_risk", "triggered", null, true,
                        reason.contains("高风险") ? "red" : "yellow", reason));
                break;
            }
        }
        return evidence;
    }

    private static Double asDouble(Object obj) {
        return obj instanceof Double d ? d : null;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
