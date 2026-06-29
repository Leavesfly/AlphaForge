package io.leavesfly.alphaforge.application.factor.evolution.model;

import io.leavesfly.alphaforge.application.backtest.BacktestSimulationResult;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 因子评估结果 — 对一个因子候选的全面量化评估
 *
 * 评估维度（参考 AlphaAgentEvo 的 RL 奖励信号设计）：
 * 1. 预测能力 — IC（信息系数）和 IR（信息比率）
 * 2. 风险调整收益 — 夏普比率、最大回撤
 * 3. 实用性 — 覆盖率、换手率、IC 衰减
 * 4. 综合评分 — 加权综合，用于进化选择
 */
public class FactorEvaluation {

    private final String factorId;

    // ===== 预测能力 =====

    /** 信息系数（因子值与未来收益的 Rank Correlation，范围 [-1, 1]） */
    private final double ic;

    /** IC 均值（回测窗口内的平均 IC） */
    private final double icMean;

    /** IC 标准差（衡量 IC 稳定性） */
    private final double icStd;

    /** 信息比率（IR = IC 均值 / IC 标准差，越高越好） */
    private final double ir;

    // ===== 风险调整收益 =====

    /** 夏普比率（基于因子多空组合的日收益序列） */
    private final double sharpeRatio;

    /** 最大回撤（%） */
    private final double maxDrawdownPct;

    /** 胜率（%） */
    private final double winRatePct;

    /** 总收益率（%） */
    private final double totalReturnPct;

    // ===== 实用性 =====

    /** 覆盖率（因子有效计算的股票占比，0-1） */
    private final double coverageRate;

    /** 换手率（因子信号日变更频率，0-1，过高意味着交易成本高） */
    private final double turnoverRate;

    /** IC 衰减天数（IC 从峰值降至一半所需的天数，衡量因子持续性） */
    private final int icDecayDays;

    // ===== 综合评分 =====

    /** 综合评分（加权组合，用于进化选择排序） */
    private final double overallScore;

    /** 评估日期 */
    private final LocalDate evaluationDate;

    /** 附加诊断信息 */
    private final Map<String, Object> diagnostics;

    /** 完整回测结果（可选，深度评估时填充） */
    private final BacktestSimulationResult backtestResult;

    private FactorEvaluation(Builder builder) {
        this.factorId = builder.factorId;
        this.ic = builder.ic;
        this.icMean = builder.icMean;
        this.icStd = builder.icStd;
        this.ir = builder.ir;
        this.sharpeRatio = builder.sharpeRatio;
        this.maxDrawdownPct = builder.maxDrawdownPct;
        this.winRatePct = builder.winRatePct;
        this.totalReturnPct = builder.totalReturnPct;
        this.coverageRate = builder.coverageRate;
        this.turnoverRate = builder.turnoverRate;
        this.icDecayDays = builder.icDecayDays;
        this.overallScore = builder.overallScore;
        this.evaluationDate = LocalDate.now();
        this.diagnostics = builder.diagnostics;
        this.backtestResult = builder.backtestResult;
    }

    // ===== Getters =====

    public String getFactorId() { return factorId; }
    public double getIc() { return ic; }
    public double getIcMean() { return icMean; }
    public double getIcStd() { return icStd; }
    public double getIr() { return ir; }
    public double getSharpeRatio() { return sharpeRatio; }
    public double getMaxDrawdownPct() { return maxDrawdownPct; }
    public double getWinRatePct() { return winRatePct; }
    public double getTotalReturnPct() { return totalReturnPct; }
    public double getCoverageRate() { return coverageRate; }
    public double getTurnoverRate() { return turnoverRate; }
    public int getIcDecayDays() { return icDecayDays; }
    public double getOverallScore() { return overallScore; }
    public LocalDate getEvaluationDate() { return evaluationDate; }
    public Map<String, Object> getDiagnostics() { return diagnostics; }
    public BacktestSimulationResult getBacktestResult() { return backtestResult; }

    /** 是否通过最低评估门槛 */
    public boolean isPassing(double minIC, double minIR, double minSharpe) {
        return ic >= minIC && ir >= minIR && sharpeRatio >= minSharpe;
    }

    // ===== Builder =====

    public static class Builder {
        private String factorId;
        private double ic;
        private double icMean;
        private double icStd;
        private double ir;
        private double sharpeRatio;
        private double maxDrawdownPct;
        private double winRatePct;
        private double totalReturnPct;
        private double coverageRate = 1.0;
        private double turnoverRate;
        private int icDecayDays;
        private double overallScore;
        private Map<String, Object> diagnostics = new LinkedHashMap<>();
        private BacktestSimulationResult backtestResult;

        public Builder factorId(String factorId) { this.factorId = factorId; return this; }
        public Builder ic(double ic) { this.ic = ic; return this; }
        public Builder icMean(double icMean) { this.icMean = icMean; return this; }
        public Builder icStd(double icStd) { this.icStd = icStd; return this; }
        public Builder ir(double ir) { this.ir = ir; return this; }
        public Builder sharpeRatio(double sharpeRatio) { this.sharpeRatio = sharpeRatio; return this; }
        public Builder maxDrawdownPct(double maxDrawdownPct) { this.maxDrawdownPct = maxDrawdownPct; return this; }
        public Builder winRatePct(double winRatePct) { this.winRatePct = winRatePct; return this; }
        public Builder totalReturnPct(double totalReturnPct) { this.totalReturnPct = totalReturnPct; return this; }
        public Builder coverageRate(double coverageRate) { this.coverageRate = coverageRate; return this; }
        public Builder turnoverRate(double turnoverRate) { this.turnoverRate = turnoverRate; return this; }
        public Builder icDecayDays(int icDecayDays) { this.icDecayDays = icDecayDays; return this; }
        public Builder overallScore(double overallScore) { this.overallScore = overallScore; return this; }
        public Builder diagnostics(Map<String, Object> diagnostics) { this.diagnostics = diagnostics; return this; }
        public Builder backtestResult(BacktestSimulationResult backtestResult) { this.backtestResult = backtestResult; return this; }

        public FactorEvaluation build() {
            if (factorId == null || factorId.isBlank()) {
                throw new IllegalArgumentException("factorId 不能为空");
            }
            return new FactorEvaluation(this);
        }
    }
}
