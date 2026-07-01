package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.application.backtest.BacktestSimulationConfig;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulator;
import io.leavesfly.alphaforge.application.factor.evolution.model.FactorCandidate;
import io.leavesfly.alphaforge.application.factor.evolution.model.FactorEvaluation;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 因子评估器 — IC/IR 计算 + 回测验证
 *
 * 评估流程：
 * 1. 在评估股票池上计算每只股票每日的因子值
 * 2. 计算因子值与未来收益的 Spearman Rank Correlation（IC）
 * 3. 计算 IR = IC 均值 / IC 标准差
 * 4. 构建因子多空组合，计算夏普比率、最大回撤
 * 5. 加权综合评分
 */
@Component
public class FactorEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FactorEvaluator.class);

    private final FactorExpressionExecutor expressionExecutor;
    private final BacktestSimulator backtestSimulator;

    public FactorEvaluator(FactorExpressionExecutor expressionExecutor,
                             BacktestSimulator backtestSimulator) {
        this.expressionExecutor = expressionExecutor;
        this.backtestSimulator = backtestSimulator;
    }

    public FactorEvaluation evaluate(FactorCandidate candidate,
                                       Map<String, List<StockDailyData>> universe) {
        if (universe == null || universe.isEmpty()) {
            return buildEmptyEvaluation(candidate.getFactorId());
        }

        log.debug("评估因子: {} expr={}", candidate.getFactorName(), candidate.getFactorExpression());

        // 1. 计算每日 IC 序列
        int forwardDays = 5; // 默认前瞻 5 天
        List<Double> icSeries = computeICSeries(candidate.getFactorExpression(), universe, forwardDays);

        if (icSeries.isEmpty()) {
            log.warn("因子 {} 的 IC 序列为空", candidate.getFactorName());
            return buildEmptyEvaluation(candidate.getFactorId());
        }

        // 2. 统计 IC
        double icMean = icSeries.stream().mapToDouble(d -> d).average().orElse(0);
        double icStd = Math.sqrt(icSeries.stream()
                .mapToDouble(d -> Math.pow(d - icMean, 2))
                .average().orElse(0));
        double ic = icMean; // IC 取均值
        double ir = icStd > 0 ? icMean / icStd : 0;

        // 3. 计算多空组合收益
        List<Double> longShortReturns = computeLongShortReturns(
                candidate.getFactorExpression(), universe, forwardDays);

        double sharpeRatio = computeSharpe(longShortReturns);
        double maxDrawdown = computeMaxDrawdown(longShortReturns);
        double winRate = computeWinRate(longShortReturns);
        double totalReturn = computeTotalReturn(longShortReturns);

        // 4. 覆盖率与换手率
        double coverageRate = computeCoverage(candidate.getFactorExpression(), universe);
        double turnoverRate = computeTurnover(candidate.getFactorExpression(), universe);

        // 5. IC 衰减天数
        int icDecayDays = computeICDecay(icSeries);

        // 6. 综合评分
        FactorEvaluation partialEval = new FactorEvaluation.Builder()
                .factorId(candidate.getFactorId())
                .ic(ic).icMean(icMean).icStd(icStd).ir(ir)
                .sharpeRatio(sharpeRatio)
                .maxDrawdownPct(maxDrawdown)
                .winRatePct(winRate)
                .totalReturnPct(totalReturn)
                .coverageRate(coverageRate)
                .turnoverRate(turnoverRate)
                .icDecayDays(icDecayDays)
                .build();

        double overallScore = computeOverallScore(partialEval);

        log.info("因子评估完成: {} IC={:.4f} IR={:.4f} Sharpe={:.2f} Score={:.1f}",
                candidate.getFactorName(), ic, ir, sharpeRatio, overallScore);

        return new FactorEvaluation.Builder()
                .factorId(candidate.getFactorId())
                .ic(ic).icMean(icMean).icStd(icStd).ir(ir)
                .sharpeRatio(sharpeRatio)
                .maxDrawdownPct(maxDrawdown)
                .winRatePct(winRate)
                .totalReturnPct(totalReturn)
                .coverageRate(coverageRate)
                .turnoverRate(turnoverRate)
                .icDecayDays(icDecayDays)
                .overallScore(overallScore)
                .diagnostics(Map.of(
                        "icSeriesSize", icSeries.size(),
                        "universeSize", universe.size(),
                        "longShortReturnsSize", longShortReturns.size()
                ))
                .build();
    }

    public List<FactorEvaluation> evaluateBatch(List<FactorCandidate> candidates,
                                                  Map<String, List<StockDailyData>> universe) {
        List<FactorEvaluation> results = new ArrayList<>();
        for (FactorCandidate candidate : candidates) {
            try {
                results.add(evaluate(candidate, universe));
            } catch (Exception e) {
                log.warn("评估因子 {} 失败: {}", candidate.getFactorName(), e.getMessage());
                results.add(buildEmptyEvaluation(candidate.getFactorId()));
            }
        }
        return results;
    }

    public List<Double> computeICSeries(String factorExpression,
                                         Map<String, List<StockDailyData>> universe,
                                         int forwardDays) {
        Map<String, List<double[]>> dailyFactorReturnPairs = new LinkedHashMap<>();

        for (Map.Entry<String, List<StockDailyData>> entry : universe.entrySet()) {
            List<StockDailyData> history = entry.getValue();
            if (history == null || history.size() < forwardDays + 1) continue;

            for (int i = 0; i < history.size() - forwardDays; i++) {
                List<StockDailyData> subHistory = history.subList(0, i + 1);
                double factorValue = expressionExecutor.execute(factorExpression, subHistory);

                Double currentClose = history.get(i).getClosePrice();
                Double futureClose = history.get(i + forwardDays).getClosePrice();
                if (currentClose == null || futureClose == null || currentClose <= 0) continue;
                double forwardReturn = (futureClose - currentClose) / currentClose * 100;

                String date = history.get(i).getTradeDate() != null
                        ? history.get(i).getTradeDate().toString() : "day_" + i;
                dailyFactorReturnPairs
                        .computeIfAbsent(date, k -> new ArrayList<>())
                        .add(new double[]{factorValue, forwardReturn});
            }
        }

        List<Double> icSeries = new ArrayList<>();
        for (Map.Entry<String, List<double[]>> entry : dailyFactorReturnPairs.entrySet()) {
            List<double[]> pairs = entry.getValue();
            if (pairs.size() < 3) continue;

            double ic = spearmanCorrelation(pairs);
            if (!Double.isNaN(ic)) {
                icSeries.add(ic);
            }
        }

        return icSeries;
    }

    public double computeOverallScore(FactorEvaluation evaluation) {
        double normalizedIC = Math.max(0, Math.min(100, (evaluation.getIc() + 0.1) / 0.2 * 100));
        double normalizedIR = Math.max(0, Math.min(100, (evaluation.getIr() + 1) / 3 * 100));
        double normalizedSharpe = Math.max(0, Math.min(100, (evaluation.getSharpeRatio() + 1) / 4 * 100));
        double normalizedDrawdown = Math.max(0, 100 - evaluation.getMaxDrawdownPct() * 2);
        double normalizedTurnover = Math.max(0, 100 - evaluation.getTurnoverRate() * 100);

        double score = 0.35 * normalizedIC
                + 0.25 * normalizedIR
                + 0.20 * normalizedSharpe
                + 0.10 * normalizedDrawdown
                + 0.10 * normalizedTurnover;

        return Math.max(0, Math.min(100, score));
    }

    // ===== 辅助计算方法 =====

    private List<Double> computeLongShortReturns(String expression,
                                                   Map<String, List<StockDailyData>> universe,
                                                   int forwardDays) {
        Map<String, List<double[]>> dailyFactorReturnPairs = new LinkedHashMap<>();

        for (Map.Entry<String, List<StockDailyData>> entry : universe.entrySet()) {
            List<StockDailyData> history = entry.getValue();
            if (history == null || history.size() < forwardDays + 1) continue;

            for (int i = 0; i < history.size() - forwardDays; i++) {
                List<StockDailyData> subHistory = history.subList(0, i + 1);
                double factorValue = expressionExecutor.execute(expression, subHistory);

                Double currentClose = history.get(i).getClosePrice();
                Double futureClose = history.get(i + forwardDays).getClosePrice();
                if (currentClose == null || futureClose == null || currentClose <= 0) continue;
                double forwardReturn = (futureClose - currentClose) / currentClose * 100;

                String date = history.get(i).getTradeDate() != null
                        ? history.get(i).getTradeDate().toString() : "day_" + i;
                dailyFactorReturnPairs
                        .computeIfAbsent(date, k -> new ArrayList<>())
                        .add(new double[]{factorValue, forwardReturn});
            }
        }

        List<Double> longShortReturns = new ArrayList<>();
        for (List<double[]> pairs : dailyFactorReturnPairs.values()) {
            if (pairs.size() < 3) continue;

            pairs.sort((a, b) -> Double.compare(a[0], b[0]));

            int third = pairs.size() / 3;
            if (third == 0) continue;

            double longReturn = 0, shortReturn = 0;
            for (int i = 0; i < third; i++) {
                shortReturn += pairs.get(i)[1];
            }
            for (int i = pairs.size() - third; i < pairs.size(); i++) {
                longReturn += pairs.get(i)[1];
            }
            longReturn /= third;
            shortReturn /= third;

            longShortReturns.add(longReturn - shortReturn);
        }

        return longShortReturns;
    }

    private double spearmanCorrelation(List<double[]> pairs) {
        int n = pairs.size();
        if (n < 3) return Double.NaN;

        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) { x[i] = pairs.get(i)[0]; y[i] = pairs.get(i)[1]; }
        double[] rx = rank(x), ry = rank(y);

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += rx[i]; sumY += ry[i];
            sumXY += rx[i] * ry[i];
            sumX2 += rx[i] * rx[i];
            sumY2 += ry[i] * ry[i];
        }
        double denom = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        return denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
    }

    private double[] rank(double[] values) {
        int n = values.length;
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(values[a], values[b]));

        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n - 1 && values[indices[j + 1]] == values[indices[i]]) j++;
            double avgRank = (i + j + 2.0) / 2;
            for (int k = i; k <= j; k++) ranks[indices[k]] = avgRank;
            i = j + 1;
        }
        return ranks;
    }

    private double computeSharpe(List<Double> returns) {
        if (returns.isEmpty()) return 0;
        double avg = returns.stream().mapToDouble(d -> d).average().orElse(0);
        double std = Math.sqrt(returns.stream()
                .mapToDouble(d -> Math.pow(d - avg, 2))
                .average().orElse(0));
        return std > 0 ? avg / std * Math.sqrt(252) : 0;
    }

    private double computeMaxDrawdown(List<Double> returns) {
        double peak = 0, cumulative = 0, maxDD = 0;
        for (double r : returns) {
            cumulative += r;
            if (cumulative > peak) peak = cumulative;
            double dd = peak - cumulative;
            if (dd > maxDD) maxDD = dd;
        }
        return maxDD;
    }

    private double computeWinRate(List<Double> returns) {
        if (returns.isEmpty()) return 0;
        long winCount = returns.stream().filter(r -> r > 0).count();
        return (double) winCount / returns.size() * 100;
    }

    private double computeTotalReturn(List<Double> returns) {
        return returns.stream().mapToDouble(d -> d).sum();
    }

    private double computeCoverage(String expression, Map<String, List<StockDailyData>> universe) {
        if (universe.isEmpty()) return 0;
        long valid = 0;
        for (List<StockDailyData> history : universe.values()) {
            if (history == null || history.isEmpty()) continue;
            double value = expressionExecutor.execute(expression, history);
            if (value != 0 && !Double.isNaN(value)) valid++;
        }
        return (double) valid / universe.size();
    }

    private double computeTurnover(String expression, Map<String, List<StockDailyData>> universe) {
        int signChanges = 0, total = 0;
        for (List<StockDailyData> history : universe.values()) {
            if (history == null || history.size() < 10) continue;
            double prev = 0;
            for (int i = 5; i < history.size(); i += 5) {
                double val = expressionExecutor.execute(expression, history.subList(0, i + 1));
                if (prev != 0 && Math.signum(val) != Math.signum(prev)) signChanges++;
                prev = val;
                total++;
            }
        }
        return total > 0 ? (double) signChanges / total : 0;
    }

    private int computeICDecay(List<Double> icSeries) {
        if (icSeries.size() < 5) return 0;
        double maxIC = icSeries.stream().mapToDouble(d -> d).max().orElse(0);
        if (maxIC <= 0) return icSeries.size();
        double halfIC = maxIC / 2;
        int maxIdx = icSeries.indexOf(maxIC);
        for (int i = maxIdx; i < icSeries.size(); i++) {
            if (Math.abs(icSeries.get(i)) < halfIC) return i - maxIdx;
        }
        return icSeries.size() - maxIdx;
    }

    private FactorEvaluation buildEmptyEvaluation(String factorId) {
        return new FactorEvaluation.Builder()
                .factorId(factorId)
                .build();
    }
}
