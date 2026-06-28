package io.leavesfly.stock.application.strategy.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walk-Forward 验证结果。
 *
 * 记录滚动窗口的样本外表现：每个窗口的训练参数、测试收益，
 * 以及汇总后的样本外综合指标，用于评估策略过拟合风险。
 */
public class WalkForwardResult {

    /** 滚动窗口数量 */
    private int windowCount;
    /** 样本外平均收益率（%） */
    private double avgOutOfSampleReturnPct;
    /** 样本外平均最大回撤（%） */
    private double avgOutOfSampleDrawdownPct;
    /** 样本外平均胜率（%） */
    private double avgOutOfSampleWinRatePct;
    /** 样本内平均收益率（%），用于对比是否过拟合 */
    private double avgInSampleReturnPct;
    /** 过拟合比率：样本外收益 / 样本内收益，越接近1越稳健 */
    private double overfitRatio;
    /** 各窗口明细 */
    private List<WindowResult> windows = Collections.emptyList();

    public int getWindowCount() { return windowCount; }
    public void setWindowCount(int windowCount) { this.windowCount = windowCount; }
    public double getAvgOutOfSampleReturnPct() { return avgOutOfSampleReturnPct; }
    public void setAvgOutOfSampleReturnPct(double v) { this.avgOutOfSampleReturnPct = v; }
    public double getAvgOutOfSampleDrawdownPct() { return avgOutOfSampleDrawdownPct; }
    public void setAvgOutOfSampleDrawdownPct(double v) { this.avgOutOfSampleDrawdownPct = v; }
    public double getAvgOutOfSampleWinRatePct() { return avgOutOfSampleWinRatePct; }
    public void setAvgOutOfSampleWinRatePct(double v) { this.avgOutOfSampleWinRatePct = v; }
    public double getAvgInSampleReturnPct() { return avgInSampleReturnPct; }
    public void setAvgInSampleReturnPct(double v) { this.avgInSampleReturnPct = v; }
    public double getOverfitRatio() { return overfitRatio; }
    public void setOverfitRatio(double overfitRatio) { this.overfitRatio = overfitRatio; }
    public List<WindowResult> getWindows() { return windows; }
    public void setWindows(List<WindowResult> windows) { this.windows = windows; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("window_count", windowCount);
        map.put("avg_out_of_sample_return_pct", String.format("%.2f", avgOutOfSampleReturnPct));
        map.put("avg_out_of_sample_drawdown_pct", String.format("%.2f", avgOutOfSampleDrawdownPct));
        map.put("avg_out_of_sample_win_rate_pct", String.format("%.1f", avgOutOfSampleWinRatePct));
        map.put("avg_in_sample_return_pct", String.format("%.2f", avgInSampleReturnPct));
        map.put("overfit_ratio", String.format("%.2f", overfitRatio));
        return map;
    }

    /** 单个滚动窗口的结果 */
    public record WindowResult(
            int windowIndex,
            Map<String, Object> trainedParams,
            double inSampleReturnPct,
            double outOfSampleReturnPct,
            double outOfSampleDrawdownPct,
            double outOfSampleWinRatePct
    ) {}
}
