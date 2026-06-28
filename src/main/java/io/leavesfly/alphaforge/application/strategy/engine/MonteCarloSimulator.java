package io.leavesfly.alphaforge.application.strategy.engine;

import io.leavesfly.alphaforge.application.backtest.BacktestDailySnapshot;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 蒙特卡洛模拟器 — 通过随机重排日收益率评估策略稳健性。
 *
 * 从回测结果中提取每日收益率序列，进行 N 次随机重排，
 * 每次重排后计算累计收益和最大回撤，最终输出收益分布统计：
 * - 中位数收益、5% / 95% 分位数
 * - 亏损概率（收益 < 0 的比例）
 * - 平均最大回撤
 *
 * 用于回答："策略的收益是因为选股能力还是运气？"
 * 如果重排后收益分布与原始接近，说明策略不依赖交易时序。
 */
@Component
public class MonteCarloSimulator {

    private static final int DEFAULT_ITERATIONS = 1000;

    /**
     * 执行蒙特卡洛模拟。
     *
     * @param backtestResult 原始回测结果（需含权益曲线）
     * @param iterations     模拟次数，0 表示使用默认 1000 次
     * @return 分布统计
     */
    public Map<String, Object> simulate(BacktestSimulationResult backtestResult, int iterations) {
        if (iterations <= 0) {
            iterations = DEFAULT_ITERATIONS;
        }

        List<BacktestDailySnapshot> equityCurve = backtestResult.getEquityCurve();
        if (equityCurve == null || equityCurve.size() < 2) {
            return Map.of("error", "权益曲线数据不足，无法执行蒙特卡洛模拟");
        }

        // 提取日收益率
        List<Double> dailyReturns = extractDailyReturns(equityCurve);
        if (dailyReturns.isEmpty()) {
            return Map.of("error", "无法计算日收益率");
        }

        double originalReturn = backtestResult.getTotalReturnPct();
        double originalDrawdown = backtestResult.getMaxDrawdownPct();

        Random random = new Random(42); // 固定种子确保可复现
        List<Double> simulatedReturns = new ArrayList<>(iterations);
        List<Double> simulatedDrawdowns = new ArrayList<>(iterations);
        int lossCount = 0;

        for (int i = 0; i < iterations; i++) {
            List<Double> shuffled = new ArrayList<>(dailyReturns);
            Collections.shuffle(shuffled, random);

            double cumulativeReturn = computeCumulativeReturn(shuffled);
            double maxDrawdown = computeMaxDrawdown(shuffled);

            simulatedReturns.add(cumulativeReturn);
            simulatedDrawdowns.add(maxDrawdown);
            if (cumulativeReturn < 0) {
                lossCount++;
            }
        }

        Collections.sort(simulatedReturns);
        Collections.sort(simulatedDrawdowns);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("iterations", iterations);
        result.put("original_return_pct", String.format("%.2f", originalReturn));
        result.put("original_max_drawdown_pct", String.format("%.2f", originalDrawdown));
        result.put("median_return_pct", String.format("%.2f", percentile(simulatedReturns, 0.5)));
        result.put("p5_return_pct", String.format("%.2f", percentile(simulatedReturns, 0.05)));
        result.put("p95_return_pct", String.format("%.2f", percentile(simulatedReturns, 0.95)));
        result.put("loss_probability", String.format("%.1f%%", (double) lossCount / iterations * 100));
        result.put("median_max_drawdown_pct", String.format("%.2f", percentile(simulatedDrawdowns, 0.5)));
        result.put("p95_max_drawdown_pct", String.format("%.2f", percentile(simulatedDrawdowns, 0.95)));
        return result;
    }

    /** 从权益曲线提取日收益率 */
    private List<Double> extractDailyReturns(List<BacktestDailySnapshot> equityCurve) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < equityCurve.size(); i++) {
            double prev = equityCurve.get(i - 1).getPortfolioValue();
            double curr = equityCurve.get(i).getPortfolioValue();
            if (prev > 0) {
                returns.add((curr - prev) / prev);
            }
        }
        return returns;
    }

    /** 计算重排后的累计收益率（%） */
    private double computeCumulativeReturn(List<Double> dailyReturns) {
        double cumulative = 1.0;
        for (double r : dailyReturns) {
            cumulative *= (1 + r);
        }
        return (cumulative - 1) * 100;
    }

    /** 计算重排后的最大回撤（%） */
    private double computeMaxDrawdown(List<Double> dailyReturns) {
        double peak = 1.0;
        double value = 1.0;
        double maxDrawdown = 0;
        for (double r : dailyReturns) {
            value *= (1 + r);
            if (value > peak) {
                peak = value;
            }
            double drawdown = (peak - value) / peak * 100;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }

    /** 计算排序后列表的指定分位数 */
    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.round(p * (sorted.size() - 1));
        return sorted.get(index);
    }
}
