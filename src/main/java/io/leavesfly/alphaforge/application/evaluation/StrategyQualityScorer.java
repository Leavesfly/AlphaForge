package io.leavesfly.alphaforge.application.evaluation;

import io.leavesfly.alphaforge.application.backtest.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.strategy.engine.StrategyPerformanceTracker;
import io.leavesfly.alphaforge.application.strategy.model.WalkForwardResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 策略质量评分器 — 将回测多维度指标统一为质量评分
 *
 * 对应论文 AlphaForgeBench 的评估框架设计：
 * 不仅看收益，还评估风险调整收益、胜率质量、稳健性、成本效率、一致性。
 *
 * 评分公式（加权平均）：
 * overall = 0.25 × 收益 + 0.25 × 风控 + 0.15 × 胜率 + 0.15 × 稳健 + 0.10 × 成本 + 0.10 × 一致
 *
 * 与现有系统的关系：
 * - 输入：BacktestSimulationResult（现有回测结果）
 * - 输入：WalkForwardResult（现有 Walk-Forward 验证结果，可选）
 * - 输入：StrategyPerformanceTracker（现有策略追踪数据，可选）
 * - 输出：StrategyQualityScore（统一质量评分）
 */
@Component
public class StrategyQualityScorer {

    private static final Logger log = LoggerFactory.getLogger(StrategyQualityScorer.class);

    // ===== 评分权重 =====
    private static final double W_RETURN = 0.25;
    private static final double W_RISK = 0.25;
    private static final double W_WIN_RATE = 0.15;
    private static final double W_ROBUSTNESS = 0.15;
    private static final double W_COST = 0.10;
    private static final double W_CONSISTENCY = 0.10;

    private final StrategyPerformanceTracker performanceTracker;

    public StrategyQualityScorer(StrategyPerformanceTracker performanceTracker) {
        this.performanceTracker = performanceTracker;
    }

    /**
     * 评估策略质量
     *
     * @param backtestResult 回测结果
     * @param walkForward    Walk-Forward 验证结果（可为 null）
     * @param strategyId     策略 ID（用于查询信号准确率，可为 null）
     * @return 质量评分
     */
    public StrategyQualityScore score(BacktestSimulationResult backtestResult,
                                       WalkForwardResult walkForward,
                                       String strategyId) {
        Map<String, StrategyQualityScore.DimensionScore> dims = new LinkedHashMap<>();
        List<String> suggestions = new ArrayList<>();

        // 1. 收益能力
        StrategyQualityScore.DimensionScore returnDim = scoreReturn(backtestResult);
        dims.put("return", returnDim);
        if (returnDim.score() < 50) suggestions.add("收益能力不足，建议优化入场条件或调整持仓周期");

        // 2. 风险控制
        StrategyQualityScore.DimensionScore riskDim = scoreRisk(backtestResult);
        dims.put("risk_control", riskDim);
        if (riskDim.score() < 50) suggestions.add("风险控制薄弱，最大回撤过大或夏普比率偏低");

        // 3. 胜率质量
        StrategyQualityScore.DimensionScore winDim = scoreWinRate(backtestResult);
        dims.put("win_quality", winDim);
        if (winDim.score() < 50) suggestions.add("胜率或盈亏比偏低，建议增加止损机制");

        // 4. 稳健性
        StrategyQualityScore.DimensionScore robustDim = scoreRobustness(backtestResult, walkForward);
        dims.put("robustness", robustDim);
        if (robustDim.score() < 50) suggestions.add("策略稳健性不足，可能存在过拟合，建议 Walk-Forward 验证");

        // 5. 成本效率
        StrategyQualityScore.DimensionScore costDim = scoreCostEfficiency(backtestResult);
        dims.put("cost_efficiency", costDim);
        if (costDim.score() < 50) suggestions.add("交易成本效率低，换手率过高或交易频率不合理");

        // 6. 一致性
        StrategyQualityScore.DimensionScore consistencyDim = scoreConsistency(backtestResult, strategyId);
        dims.put("consistency", consistencyDim);
        if (consistencyDim.score() < 50) suggestions.add("信号一致性不足，命中率和准确率波动较大");

        // 加权综合
        double overall = W_RETURN * returnDim.score()
                + W_RISK * riskDim.score()
                + W_WIN_RATE * winDim.score()
                + W_ROBUSTNESS * robustDim.score()
                + W_COST * costDim.score()
                + W_CONSISTENCY * consistencyDim.score();

        overall = Math.max(0, Math.min(100, overall));

        String summary = buildSummary(backtestResult, overall);
        log.info("策略质量评分: {} (等级:{}) — 收益:{:.0f} 风控:{:.0f} 胜率:{:.0f} 稳健:{:.0f} 成本:{:.0f} 一致:{:.0f}",
                strategyId, StrategyQualityScore.QualityGrade.fromScore(overall),
                returnDim.score(), riskDim.score(), winDim.score(),
                robustDim.score(), costDim.score(), consistencyDim.score());

        return new StrategyQualityScore(overall, dims, summary, suggestions);
    }

    /** 简化版（无 Walk-Forward） */
    public StrategyQualityScore score(BacktestSimulationResult backtestResult) {
        return score(backtestResult, null, null);
    }

    // ===== 各维度评分 =====

    /**
     * 收益能力评分
     * - 年化收益 >30%: 90+; 10-30%: 60-90; 0-10%: 30-60; <0%: 0-30
     * - 超额收益（vs 基准）加权
     */
    private StrategyQualityScore.DimensionScore scoreReturn(BacktestSimulationResult result) {
        double annual = result.getAnnualReturnPct();
        double excess = annual - result.getBenchmarkReturnPct();

        double score = 0;
        List<String> issues = new ArrayList<>();

        // 年化收益评分（60%权重）
        double annualScore;
        if (annual >= 30) annualScore = 90 + Math.min(10, (annual - 30) / 5);
        else if (annual >= 10) annualScore = 60 + (annual - 10) / 20 * 30;
        else if (annual >= 0) annualScore = 30 + annual / 10 * 30;
        else annualScore = Math.max(0, 30 + annual / 2);

        // 超额收益评分（40%权重）
        double excessScore;
        if (excess >= 20) excessScore = 95;
        else if (excess >= 0) excessScore = 50 + excess / 20 * 45;
        else excessScore = Math.max(0, 50 + excess * 2);

        score = 0.6 * annualScore + 0.4 * excessScore;

        if (annual < 0) issues.add("年化收益为负");
        if (excess < -10) issues.add("大幅跑输基准");

        String detail = String.format("年化=%.1f%% 基准=%.1f%% 超额=%.1f%%",
                annual, result.getBenchmarkReturnPct(), excess);

        return new StrategyQualityScore.DimensionScore("收益能力", score, W_RETURN, detail, issues);
    }

    /**
     * 风险控制评分
     * - 夏普比率 >2: 90+; 1-2: 60-90; 0-1: 30-60; <0: 0-30
     * - 最大回撤惩罚：回撤 >30% 扣 20 分
     */
    private StrategyQualityScore.DimensionScore scoreRisk(BacktestSimulationResult result) {
        double sharpe = result.getSharpeRatio();
        double drawdown = result.getMaxDrawdownPct();
        List<String> issues = new ArrayList<>();

        double sharpeScore;
        if (sharpe >= 2) sharpeScore = 90 + Math.min(10, (sharpe - 2) * 5);
        else if (sharpe >= 1) sharpeScore = 60 + (sharpe - 1) / 1 * 30;
        else if (sharpe >= 0) sharpeScore = 30 + sharpe / 1 * 30;
        else sharpeScore = Math.max(0, 30 + sharpe * 15);

        // 回撤惩罚
        double drawdownPenalty = 0;
        if (drawdown > 30) {
            drawdownPenalty = Math.min(30, (drawdown - 30) * 1.5);
            issues.add(String.format("最大回撤 %.1f%% 过大", drawdown));
        }

        double score = Math.max(0, sharpeScore - drawdownPenalty);

        String detail = String.format("夏普=%.2f 最大回撤=%.1f%%", sharpe, drawdown);
        return new StrategyQualityScore.DimensionScore("风险控制", score, W_RISK, detail, issues);
    }

    /**
     * 胜率质量评分
     * - 胜率 + 盈亏比综合
     */
    private StrategyQualityScore.DimensionScore scoreWinRate(BacktestSimulationResult result) {
        double winRate = result.getWinRatePct();
        double plRatio = result.getProfitLossRatio();
        List<String> issues = new ArrayList<>();

        // 胜率评分
        double winScore = winRate >= 60 ? 90 + Math.min(10, (winRate - 60) / 4)
                : winRate >= 40 ? 50 + (winRate - 40) / 20 * 40
                : Math.max(0, winRate / 40 * 50);

        // 盈亏比评分
        double plScore = plRatio >= 2 ? 90 + Math.min(10, (plRatio - 2) * 5)
                : plRatio >= 1 ? 50 + (plRatio - 1) / 1 * 40
                : Math.max(0, plRatio * 50);

        double score = 0.5 * winScore + 0.5 * plScore;

        if (winRate < 40) issues.add(String.format("胜率 %.1f%% 偏低", winRate));
        if (plRatio < 1) issues.add(String.format("盈亏比 %.2f < 1，亏损大于盈利", plRatio));

        String detail = String.format("胜率=%.1f%% 盈亏比=%.2f 交易=%d", winRate, plRatio, result.getTotalTrades());
        return new StrategyQualityScore.DimensionScore("胜率质量", score, W_WIN_RATE, detail, issues);
    }

    /**
     * 稳健性评分（基于 Walk-Forward 结果）
     */
    private StrategyQualityScore.DimensionScore scoreRobustness(BacktestSimulationResult result,
                                                                  WalkForwardResult walkForward) {
        List<String> issues = new ArrayList<>();

        if (walkForward == null) {
            // 无 Walk-Forward 数据时，基于交易次数评估
            int trades = result.getTotalTrades();
            double score = trades >= 10 ? 60 : trades >= 5 ? 45 : 30;
            if (trades < 5) issues.add("交易次数过少，统计意义不足");
            return new StrategyQualityScore.DimensionScore("稳健性", score, W_ROBUSTNESS,
                    String.format("交易次数=%d（无Walk-Forward数据）", trades), issues);
        }

        // 基于 Walk-Forward 结果评分
        double overfitRatio = walkForward.getOverfitRatio();
        double oosReturn = walkForward.getAvgOutOfSampleReturnPct();

        // 过拟合比率（样本外/样本内）越接近 1 越好
        double overfitScore;
        if (overfitRatio >= 0.8) overfitScore = 90;
        else if (overfitRatio >= 0.5) overfitScore = 60 + (overfitRatio - 0.5) / 0.3 * 30;
        else if (overfitRatio >= 0) overfitScore = overfitRatio / 0.5 * 60;
        else overfitScore = 30;

        // 样本外收益
        double oosScore = oosReturn >= 10 ? 90 : oosReturn >= 0 ? 50 + oosReturn / 10 * 40
                : Math.max(0, 50 + oosReturn * 2);

        double score = 0.6 * overfitScore + 0.4 * oosScore;

        if (overfitRatio < 0.3) issues.add(String.format("过拟合比率 %.2f 过低，策略可能过拟合", overfitRatio));
        if (oosReturn < 0) issues.add(String.format("样本外收益 %.1f%% 为负", oosReturn));

        String detail = String.format("过拟合比率=%.2f 样本外收益=%.1f%% 窗口数=%d",
                overfitRatio, oosReturn, walkForward.getWindowCount());
        return new StrategyQualityScore.DimensionScore("稳健性", score, W_ROBUSTNESS, detail, issues);
    }

    /**
     * 成本效率评分
     */
    private StrategyQualityScore.DimensionScore scoreCostEfficiency(BacktestSimulationResult result) {
        int trades = result.getTotalTrades();
        double avgHoldingDays = result.getAvgHoldingDays();
        List<String> issues = new ArrayList<>();

        // 交易频率评分（基于平均持仓天数）
        double freqScore;
        if (avgHoldingDays >= 5 && avgHoldingDays <= 30) {
            freqScore = 90; // 合理持仓周期
        } else if (avgHoldingDays > 30) {
            freqScore = 70; // 持仓偏长
        } else if (avgHoldingDays >= 1) {
            freqScore = 50; // 持仓偏短
        } else {
            freqScore = 30;
        }

        // 交易成本占比（从 diagnostics 获取）
        double totalCost = 0;
        Map<String, Object> diag = result.getDiagnostics();
        if (diag.containsKey("total_commission")) totalCost += (Double) diag.get("total_commission");
        if (diag.containsKey("total_stamp_tax")) totalCost += (Double) diag.get("total_stamp_tax");
        if (diag.containsKey("total_slippage_cost")) totalCost += (Double) diag.get("total_slippage_cost");

        double costRatio = result.getFinalCapital() > 0
                ? totalCost / (result.getFinalCapital() + totalCost) * 100 : 0;
        double costScore = costRatio < 0.5 ? 90 : costRatio < 1 ? 70 : costRatio < 2 ? 50 : 30;

        if (costRatio > 1) issues.add(String.format("交易成本占比 %.2f%% 过高", costRatio));

        double score = 0.5 * freqScore + 0.5 * costScore;
        String detail = String.format("交易次数=%d 持仓=%.1f天 成本占比≈%.2f%%",
                trades, avgHoldingDays, costRatio);
        return new StrategyQualityScore.DimensionScore("成本效率", score, W_COST, detail, issues);
    }

    /**
     * 一致性评分（基于信号准确率）
     */
    private StrategyQualityScore.DimensionScore scoreConsistency(BacktestSimulationResult result,
                                                                   String strategyId) {
        List<String> issues = new ArrayList<>();

        if (strategyId == null || performanceTracker == null) {
            return new StrategyQualityScore.DimensionScore("一致性", 50, W_CONSISTENCY,
                    "无策略追踪数据", List.of());
        }

        double matchRate = performanceTracker.getMatchRate(strategyId);
        double accuracy = performanceTracker.getOutcomeAccuracy(strategyId);

        if (matchRate < 0 && accuracy < 0) {
            return new StrategyQualityScore.DimensionScore("一致性", 50, W_CONSISTENCY,
                    "无信号反馈数据", List.of());
        }

        // 命中率评分（30-70% 区间为最佳）
        double matchScore;
        if (matchRate < 0) matchScore = 50;
        else if (matchRate >= 0.3 && matchRate <= 0.7) matchScore = 90;
        else if (matchRate < 0.3) matchScore = 50 + matchRate / 0.3 * 40;
        else matchScore = Math.max(40, 90 - (matchRate - 0.7) / 0.3 * 50);

        // 准确率评分
        double accuracyScore;
        if (accuracy < 0) accuracyScore = 50;
        else if (accuracy >= 70) accuracyScore = 90 + Math.min(10, (accuracy - 70) / 3);
        else if (accuracy >= 50) accuracyScore = 50 + (accuracy - 50) / 20 * 40;
        else accuracyScore = Math.max(0, accuracy / 50 * 50);

        double score = 0.4 * matchScore + 0.6 * accuracyScore;

        if (accuracy >= 0 && accuracy < 50) issues.add(String.format("信号准确率 %.1f%% 偏低", accuracy));

        String detail = String.format("命中率=%.1f%% 准确率=%.1f%%",
                matchRate >= 0 ? matchRate * 100 : -1,
                accuracy >= 0 ? accuracy : -1);
        return new StrategyQualityScore.DimensionScore("一致性", score, W_CONSISTENCY, detail, issues);
    }

    private String buildSummary(BacktestSimulationResult result, double overall) {
        return String.format("综合评分 %.1f（%s）| 年化收益 %.1f%% 夏普 %.2f 回撤 %.1f%% 胜率 %.1f%%",
                overall, StrategyQualityScore.QualityGrade.fromScore(overall).label,
                result.getAnnualReturnPct(), result.getSharpeRatio(),
                result.getMaxDrawdownPct(), result.getWinRatePct());
    }
}
