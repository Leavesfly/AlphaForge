package io.leavesfly.stock.application.strategy.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数优化结果。
 *
 * 记录网格搜索过程中的最优参数组合、对应回测指标，以及全部候选结果摘要。
 */
public class OptimizationResult {

    /** 最优参数组合，如 {fast_period: 5, slow_period: 20} */
    private Map<String, Object> bestParams = Collections.emptyMap();
    /** 最优参数下的总收益率（%） */
    private double bestReturnPct;
    /** 最优参数下的最大回撤（%） */
    private double bestMaxDrawdownPct;
    /** 最优参数下的胜率（%） */
    private double bestWinRatePct;
    /** 最优参数下的夏普比率 */
    private double bestSharpeRatio;
    /** 搜索的参数组合总数 */
    private int totalCandidates;
    /** 全部候选结果（按收益率降序，最多保留 Top 10） */
    private List<CandidateResult> topCandidates = Collections.emptyList();

    public Map<String, Object> getBestParams() { return bestParams; }
    public void setBestParams(Map<String, Object> bestParams) { this.bestParams = bestParams; }
    public double getBestReturnPct() { return bestReturnPct; }
    public void setBestReturnPct(double bestReturnPct) { this.bestReturnPct = bestReturnPct; }
    public double getBestMaxDrawdownPct() { return bestMaxDrawdownPct; }
    public void setBestMaxDrawdownPct(double bestMaxDrawdownPct) { this.bestMaxDrawdownPct = bestMaxDrawdownPct; }
    public double getBestWinRatePct() { return bestWinRatePct; }
    public void setBestWinRatePct(double bestWinRatePct) { this.bestWinRatePct = bestWinRatePct; }
    public double getBestSharpeRatio() { return bestSharpeRatio; }
    public void setBestSharpeRatio(double bestSharpeRatio) { this.bestSharpeRatio = bestSharpeRatio; }
    public int getTotalCandidates() { return totalCandidates; }
    public void setTotalCandidates(int totalCandidates) { this.totalCandidates = totalCandidates; }
    public List<CandidateResult> getTopCandidates() { return topCandidates; }
    public void setTopCandidates(List<CandidateResult> topCandidates) { this.topCandidates = topCandidates; }

    /** 转为 Map 供 API/日志输出 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("best_params", bestParams);
        map.put("best_return_pct", String.format("%.2f", bestReturnPct));
        map.put("best_max_drawdown_pct", String.format("%.2f", bestMaxDrawdownPct));
        map.put("best_win_rate_pct", String.format("%.1f", bestWinRatePct));
        map.put("best_sharpe_ratio", String.format("%.3f", bestSharpeRatio));
        map.put("total_candidates", totalCandidates);
        return map;
    }

    /** 单个参数组合的回测结果 */
    public record CandidateResult(
            Map<String, Object> params,
            double returnPct,
            double maxDrawdownPct,
            double winRatePct,
            double sharpeRatio
    ) {}
}
