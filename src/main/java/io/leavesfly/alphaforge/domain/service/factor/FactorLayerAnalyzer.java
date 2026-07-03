package io.leavesfly.alphaforge.domain.service.factor;

import java.util.ArrayList;
import java.util.List;

/**
 * 因子分层回测分析器（纯领域算法）。
 *
 * <p>输入为按期组织的横截面样本：每期一组 {@code (因子值, 前瞻收益)} 对。
 * 对每期按因子值分成 N 层，统计各层平均前瞻收益，跨期平均得到分层收益曲线，
 * 并计算多空收益、单调性、IC 序列及 IC-IR。这是评估因子有效性的行业标准工具。</p>
 */
public class FactorLayerAnalyzer {

    /**
     * 执行分层回测。
     *
     * @param periods   每期样本；period[t] 为该期的 (factorValue, forwardReturn) 列表
     * @param quantiles 分层数（≥2，如 5）
     */
    public FactorLayerResult analyze(List<List<double[]>> periods, int quantiles) {
        int q = Math.max(quantiles, 2);
        double[] layerSum = new double[q];
        int[] layerCount = new int[q];
        List<Double> icSeries = new ArrayList<>();

        for (List<double[]> period : periods) {
            if (period == null || period.size() < q) continue;

            // 排序（按因子值升序）
            List<double[]> sorted = new ArrayList<>(period);
            sorted.sort((a, b) -> Double.compare(a[0], b[0]));
            int n = sorted.size();

            // 分层累计各层收益
            for (int layer = 0; layer < q; layer++) {
                int from = (int) Math.floor((long) layer * n / q);
                int to = (int) Math.floor((long) (layer + 1) * n / q);
                if (to <= from) continue;
                double sum = 0;
                for (int i = from; i < to; i++) sum += sorted.get(i)[1];
                layerSum[layer] += sum / (to - from);
                layerCount[layer]++;
            }

            // 该期 IC（因子值 vs 前瞻收益 Spearman）
            double[] fv = new double[n];
            double[] fr = new double[n];
            for (int i = 0; i < n; i++) {
                fv[i] = sorted.get(i)[0];
                fr[i] = sorted.get(i)[1];
            }
            double ic = CrossSectionalOps.spearman(fv, fr);
            if (!Double.isNaN(ic)) icSeries.add(ic);
        }

        double[] layerMean = new double[q];
        for (int layer = 0; layer < q; layer++) {
            layerMean[layer] = layerCount[layer] > 0 ? layerSum[layer] / layerCount[layer] : 0;
        }
        double longShort = layerMean[q - 1] - layerMean[0];

        // 单调性：层序号 vs 层收益 的 Spearman
        double[] layerIdx = new double[q];
        for (int i = 0; i < q; i++) layerIdx[i] = i;
        double monotonicity = CrossSectionalOps.spearman(layerIdx, layerMean);
        if (Double.isNaN(monotonicity)) monotonicity = 0;

        // IC 统计
        double icMean = 0;
        for (double ic : icSeries) icMean += ic;
        icMean = icSeries.isEmpty() ? 0 : icMean / icSeries.size();
        double icVar = 0;
        for (double ic : icSeries) icVar += (ic - icMean) * (ic - icMean);
        double icStd = icSeries.size() > 1 ? Math.sqrt(icVar / (icSeries.size() - 1)) : 0;
        double icIR = icStd > 1e-12 ? icMean / icStd : 0;
        long icWins = icSeries.stream().filter(v -> v > 0).count();
        double icWinRate = icSeries.isEmpty() ? 0 : (double) icWins / icSeries.size();

        return new FactorLayerResult(q, layerMean, longShort, monotonicity,
                icMean, icStd, icIR, icWinRate, icSeries.size());
    }
}
