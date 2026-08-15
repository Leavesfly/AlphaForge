package io.leavesfly.alphaforge.domain.service.decision;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 时灯（现在是不是好时机）— 过热追高/事件风险亮红；回调进行中/RSI 过热/量价背离亮黄。
 *
 * <p>动态阈值：MA20 偏离（15%×vol_k）、RSI 过热（78×vol_k）、量能背离（0.7×vol_k）
 * 均受波动率缩放因子 vol_k 调整（高波动放宽、低波动收紧）。</p>
 */
public final class TimingLightCalculator {

    /** MA20 偏离红阈值基数（× vol_k） */
    public static final double DEV_BASE = 0.15;
    /** 距 60 日高点回撤黄阈值 */
    public static final double DD60_YELLOW = -0.08;
    /** RSI 过热黄阈值基数（× vol_k） */
    public static final double RSI_BASE = 78.0;
    /** 量价背离：末根量能 / 20 日均量 低于该系数（× vol_k）时触发 */
    public static final double VOL_DIVERGENCE = 0.7;
    /** 事件风险回看窗口（天） */
    public static final int EVENT_WINDOW_DAYS = 30;

    private TimingLightCalculator() {
    }

    /**
     * 时灯评估。
     *
     * @param history     个股日 K（升序，长度已由引擎校验）
     * @param volK        波动率缩放因子（[0.8, 1.4]）
     * @param riskEvents  事件风险（可空）
     * @param asof        评估基准日（末根 K 线日期，可空：不过滤日期）
     */
    public static Assessment calculate(List<StockDailyData> history, double volK,
                                       List<ThreeLightsInput.RiskEvent> riskEvents, LocalDate asof) {
        double last = IndicatorMath.lastClose(history);
        double ma20 = IndicatorMath.sma(history, 20);
        double dev20 = !Double.isNaN(ma20) && ma20 > 0 ? last / ma20 - 1.0 : Double.NaN;
        double high60 = IndicatorMath.highestClose(history, 60);
        double dd60 = high60 > 0 ? last / high60 - 1.0 : Double.NaN;
        double rsi14 = IndicatorMath.rsi(history, 14);

        LightResult light = new LightResult(LightColor.GREEN);
        List<String> redReasons = new ArrayList<>();
        List<String> yellowReasons = new ArrayList<>();
        boolean red = false;
        boolean yellow = false;

        // 过热追高（红）
        double devThreshold = DEV_BASE * volK;
        boolean overheated = !Double.isNaN(dev20) && dev20 > devThreshold;
        if (overheated) {
            red = true;
            String reason = String.format("收盘偏离 MA20 达 %+.1f%%（>%.0f%%），过热追高，等回踩（红灯）",
                    dev20 * 100, devThreshold * 100);
            light.addReason(reason);
            redReasons.add(reason);
        }

        // 回调状态（黄）
        boolean belowMa20 = !Double.isNaN(ma20) && last < ma20;
        if (belowMa20) {
            yellow = true;
            String reason = String.format("收盘 %.2f 低于 MA20 %.2f，回调进行中，等企稳", last, ma20);
            light.addReason(reason);
            yellowReasons.add(reason);
        }
        if (!Double.isNaN(dd60) && dd60 <= DD60_YELLOW) {
            yellow = true;
            String reason = String.format("距 60 日高点回撤 %.1f%%（超 8%%），短期结构未修复", dd60 * 100);
            light.addReason(reason);
            yellowReasons.add(reason);
        }

        // RSI 过热（黄）
        double rsiThreshold = RSI_BASE * volK;
        boolean rsiHot = !Double.isNaN(rsi14) && rsi14 > rsiThreshold;
        if (rsiHot) {
            yellow = true;
            String reason = String.format("RSI14 = %.1f > %.0f，短期过热", rsi14, rsiThreshold);
            light.addReason(reason);
            yellowReasons.add(reason);
        }

        // 量价背离（黄）：价创 20 日新高但量能萎缩（近 20 根成交量完整才检查）
        double volThreshold = VOL_DIVERGENCE * volK;
        Double volMa20 = volumeMa20(history, 20);
        if (volMa20 != null && volMa20 > 0) {
            double high20 = IndicatorMath.highestClose(history, 20);
            Long lastVolume = history.get(history.size() - 1).getVolume();
            if (last >= high20 - 1e-9 && lastVolume != null && lastVolume < volThreshold * volMa20) {
                yellow = true;
                String reason = String.format("价创 20 日新高但量能低于 20 日均量 %.0f%%（量价背离）",
                        volThreshold * 100);
                light.addReason(reason);
                yellowReasons.add(reason);
            }
        }

        // 事件风险：近 30 天 high 红灯、medium 黄灯（利好不加分）
        List<ThreeLightsInput.RiskEvent> triggeredEvents = recentEvents(riskEvents, asof);
        for (ThreeLightsInput.RiskEvent ev : triggeredEvents) {
            if ("high".equals(ev.getLevel())) {
                red = true;
                String note = ev.getNote() == null || ev.getNote().isBlank() ? "（未注明）" : ev.getNote();
                String reason = String.format("高风险事件 %s：%s（红灯，等事件落地）", ev.getDate(), note);
                light.addReason(reason);
                redReasons.add(reason);
            } else {
                yellow = true;
                String note = ev.getNote() == null || ev.getNote().isBlank() ? "（未注明）" : ev.getNote();
                String reason = String.format("中风险事件 %s：%s（黄灯提示）", ev.getDate(), note);
                light.addReason(reason);
                yellowReasons.add(reason);
            }
        }

        light.setColor(red ? LightColor.RED : (yellow ? LightColor.YELLOW : LightColor.GREEN));
        if (light.getColor() == LightColor.GREEN) {
            String ddPart = Double.isNaN(dd60) ? "" : String.format(Locale.ROOT, "%.1f", dd60 * 100);
            light.addReason("收在 MA20 上方、距 60 日高点回撤 " + ddPart + "%（<8%）且无过热/事件风险，入场结构有序");
        }
        light.putDetail("overheated", overheated);
        light.putDetail("belowMa20", belowMa20);
        light.putDetail("rsiHot", rsiHot);
        light.putDetail("redReasons", List.copyOf(redReasons));
        light.putDetail("yellowReasons", List.copyOf(yellowReasons));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("rsi14", rsi14);
        snapshot.put("dev20", dev20);
        snapshot.put("dd60", dd60);

        return new Assessment(light, snapshot, triggeredEvents);
    }

    /** 筛出近 30 天内的 high/medium 风险事件（asof 为空时不过滤日期） */
    static List<ThreeLightsInput.RiskEvent> recentEvents(List<ThreeLightsInput.RiskEvent> events,
                                                         LocalDate asof) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<ThreeLightsInput.RiskEvent> out = new ArrayList<>();
        for (ThreeLightsInput.RiskEvent ev : events) {
            String level = ev.getLevel() == null ? "" : ev.getLevel().trim().toLowerCase(Locale.ROOT);
            if (!"high".equals(level) && !"medium".equals(level)) {
                continue;
            }
            if (asof != null && ev.getDate() != null) {
                if (ev.getDate().isBefore(asof.minusDays(EVENT_WINDOW_DAYS)) || ev.getDate().isAfter(asof)) {
                    continue;
                }
            }
            out.add(ev);
        }
        return out;
    }

    /** 近 window 根成交量均值（任一根缺失返回 null，诚实跳过量价背离检查） */
    private static Double volumeMa20(List<StockDailyData> history, int window) {
        if (history.size() < window) {
            return null;
        }
        double sum = 0;
        for (int i = history.size() - window; i < history.size(); i++) {
            Long volume = history.get(i).getVolume();
            if (volume == null) {
                return null;
            }
            sum += volume;
        }
        return sum / window;
    }

    /** 时灯评估产出：灯 + 快照（rsi14/dev20/dd60）+ 触发的事件风险 */
    public record Assessment(LightResult light, Map<String, Object> snapshot,
                             List<ThreeLightsInput.RiskEvent> triggeredEvents) {
    }
}
