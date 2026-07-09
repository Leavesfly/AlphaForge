package io.leavesfly.alphaforge.application.backtest.pairs;

/**
 * 配对交易参数配置。
 *
 * <p>价差以滚动 z-score 度量偏离程度：|z| 越大偏离越远。只做多头腿策略在价差偏离时
 * 买入相对便宜的一腿，价差回归（|z| 缩小）时平仓。</p>
 */
public class PairTradingConfig {

    /** z-score 滚动窗口（交易日） */
    private int lookbackWindow = 60;
    /** 开仓阈值：|z| >= entryZ 时买入便宜腿 */
    private double entryZ = 2.0;
    /** 平仓阈值：|z| <= exitZ 时价差已回归，平仓离场 */
    private double exitZ = 0.5;
    /** 止损阈值：|z| >= stopZ 时价差进一步发散，止损离场 */
    private double stopZ = 3.5;
    /** 前置筛选最小相关系数，低于则判定不适合配对 */
    private double minCorrelation = 0.7;
    /** 单腿买入仓位比例（占可用资金） */
    private double positionSize = 0.9;
    /** 对冲比率模式：ols=OLS 回归斜率；ratio=固定为 1.0（等额价差） */
    private String hedgeRatioMode = "ols";

    /** 默认配置。 */
    public static PairTradingConfig defaults() {
        return new PairTradingConfig();
    }

    public int getLookbackWindow() {
        return lookbackWindow;
    }

    public void setLookbackWindow(int lookbackWindow) {
        this.lookbackWindow = lookbackWindow;
    }

    public double getEntryZ() {
        return entryZ;
    }

    public void setEntryZ(double entryZ) {
        this.entryZ = entryZ;
    }

    public double getExitZ() {
        return exitZ;
    }

    public void setExitZ(double exitZ) {
        this.exitZ = exitZ;
    }

    public double getStopZ() {
        return stopZ;
    }

    public void setStopZ(double stopZ) {
        this.stopZ = stopZ;
    }

    public double getMinCorrelation() {
        return minCorrelation;
    }

    public void setMinCorrelation(double minCorrelation) {
        this.minCorrelation = minCorrelation;
    }

    public double getPositionSize() {
        return positionSize;
    }

    public void setPositionSize(double positionSize) {
        this.positionSize = positionSize;
    }

    public String getHedgeRatioMode() {
        return hedgeRatioMode;
    }

    public void setHedgeRatioMode(String hedgeRatioMode) {
        this.hedgeRatioMode = hedgeRatioMode;
    }
}
