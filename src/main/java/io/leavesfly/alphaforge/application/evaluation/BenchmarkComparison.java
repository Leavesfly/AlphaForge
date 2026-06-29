package io.leavesfly.alphaforge.application.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略对比报告 — 多策略横向评估结果
 */
public class BenchmarkComparison {

    private final String stockCode;
    private final int backtestDays;
    private final List<BenchmarkReport> reports;
    private final long totalDurationMs;

    public BenchmarkComparison(String stockCode, int backtestDays,
                                List<BenchmarkReport> reports, long totalDurationMs) {
        this.stockCode = stockCode;
        this.backtestDays = backtestDays;
        this.reports = reports != null ? reports : List.of();
        this.totalDurationMs = totalDurationMs;
    }

    public String getStockCode() { return stockCode; }
    public int getBacktestDays() { return backtestDays; }
    public List<BenchmarkReport> getReports() { return reports; }
    public long getTotalDurationMs() { return totalDurationMs; }

    /** 获取最优策略 */
    public BenchmarkReport getBestStrategy() {
        return reports.stream()
                .filter(BenchmarkReport::isSuccess)
                .max((a, b) -> Double.compare(
                        a.getQualityScore().getOverallScore(),
                        b.getQualityScore().getOverallScore()))
                .orElse(null);
    }

    /** 获取成功率 */
    public double getSuccessRate() {
        if (reports.isEmpty()) return 0;
        long success = reports.stream().filter(BenchmarkReport::isSuccess).count();
        return (double) success / reports.size() * 100;
    }

    /** 获取平均质量评分 */
    public double getAverageScore() {
        return reports.stream()
                .filter(BenchmarkReport::isSuccess)
                .mapToDouble(r -> r.getQualityScore().getOverallScore())
                .average()
                .orElse(0);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stock_code", stockCode);
        map.put("backtest_days", backtestDays);
        map.put("total_strategies", reports.size());
        map.put("success_rate", String.format("%.1f%%", getSuccessRate()));
        map.put("average_score", String.format("%.1f", getAverageScore()));

        BenchmarkReport best = getBestStrategy();
        if (best != null) {
            Map<String, Object> bestMap = new LinkedHashMap<>();
            bestMap.put("strategy_id", best.getStrategyId());
            bestMap.put("score", String.format("%.1f", best.getQualityScore().getOverallScore()));
            bestMap.put("grade", best.getQualityScore().getGrade().name());
            map.put("best_strategy", bestMap);
        }

        List<Map<String, Object>> reportList = new ArrayList<>();
        for (BenchmarkReport r : reports) {
            reportList.add(r.toMap());
        }
        map.put("reports", reportList);
        map.put("total_duration_ms", totalDurationMs);
        return map;
    }
}
