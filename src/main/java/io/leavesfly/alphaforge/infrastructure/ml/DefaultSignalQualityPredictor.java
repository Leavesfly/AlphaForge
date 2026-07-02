package io.leavesfly.alphaforge.infrastructure.ml;

import io.leavesfly.alphaforge.domain.service.port.SignalQualityPredictor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认信号质量预测器 — 规则引擎 + 自适应历史统计（双层预测）
 *
 * Layer 1 - 技术共识规则引擎：
 *   充分利用 technicalContext（RSI/MACD/均线/KDJ）与 marketContext（情绪/波动率）
 *   计算技术指标方向共识度，量化信号内部一致性与市场环境适配性。
 *
 * Layer 2 - 自适应历史统计：
 *   按 (信号类型, 评分区间, 置信度) 三元组维护滑动窗口准确率。
 *   样本充足时（≥10条）动态混合历史准确率，使预测随时间自我校准。
 *   通过 recordOutcome() 接收实际信号结果反馈，驱动持续学习。
 *
 * 生产环境可替换为 ML 模型实现（如 XGBoost/LightGBM）。
 */
@Component
@ConditionalOnMissingBean(SignalQualityPredictor.class)
public class DefaultSignalQualityPredictor implements SignalQualityPredictor {

    private static final Logger log = LoggerFactory.getLogger(DefaultSignalQualityPredictor.class);

    /** 历史统计最小样本量：低于此值时仅使用规则引擎 */
    private static final int MIN_SAMPLES = 10;
    /** 历史统计最大混合权重（样本量充足时上限 60%，随样本量线性增长） */
    private static final double MAX_HISTORY_WEIGHT = 0.60;

    /** key: "signal|scoreRange|confidence"，value: 滑动窗口准确率统计 */
    private final ConcurrentHashMap<String, AccuracyStats> historyStats = new ConcurrentHashMap<>();

    // ===== 公开预测接口 =====

    @Override
    public QualityPrediction predict(String signal, int score, String confidence,
                                      Map<String, Object> technicalContext,
                                      Map<String, Object> marketContext) {

        // Layer 1: 规则引擎评分
        double ruleAccuracy = computeRuleScore(signal, score, confidence, technicalContext, marketContext);

        // Layer 2: 历史统计混合校准
        String statsKey = buildStatsKey(signal, score, confidence);
        AccuracyStats stats = historyStats.get(statsKey);
        double finalAccuracy;
        if (stats != null && stats.total() >= MIN_SAMPLES) {
            double historyAccuracy = stats.accuracy();
            // 样本越多，历史权重越高，最高不超过 MAX_HISTORY_WEIGHT
            double historyWeight = Math.min(MAX_HISTORY_WEIGHT, (double) stats.total() / 100.0 * MAX_HISTORY_WEIGHT);
            finalAccuracy = historyAccuracy * historyWeight + ruleAccuracy * (1.0 - historyWeight);
            log.debug("[质量预测] 历史校准: key={} history={:.2f}(n={}) rule={:.2f} final={:.2f}",
                    statsKey, historyAccuracy, stats.total(), ruleAccuracy, finalAccuracy);
        } else {
            finalAccuracy = ruleAccuracy;
        }

        finalAccuracy = Math.max(0.10, Math.min(0.90, finalAccuracy));

        // 生成预测结论
        String adjustedConfidence;
        boolean suggestDowngrade;
        String reason;

        if (finalAccuracy < 0.35) {
            adjustedConfidence = "低";
            suggestDowngrade = score >= 80 || score <= 20;
            reason = "综合可靠性偏低(" + fmt(finalAccuracy) + ")";
        } else if (finalAccuracy < 0.50) {
            adjustedConfidence = "低".equals(confidence) ? "低" : "中等";
            suggestDowngrade = false;
            reason = "技术共识度不足(" + fmt(finalAccuracy) + ")";
        } else if (finalAccuracy >= 0.70) {
            adjustedConfidence = "高";
            suggestDowngrade = false;
            reason = "多维指标高度一致(" + fmt(finalAccuracy) + ")";
        } else {
            adjustedConfidence = confidence;
            suggestDowngrade = false;
            reason = "规则+统计综合预测(" + fmt(finalAccuracy) + ")";
        }

        return new QualityPrediction(finalAccuracy, adjustedConfidence, suggestDowngrade, reason);
    }

    /**
     * 记录信号实际结果，驱动历史准确率自适应更新
     *
     * @param signal      原始交易信号
     * @param score       综合评分 (0-100)
     * @param confidence  置信度描述
     * @param wasAccurate 信号是否准确（如：买入后价格上涨超阈值则为 true）
     */
    public void recordOutcome(String signal, int score, String confidence, boolean wasAccurate) {
        String key = buildStatsKey(signal, score, confidence);
        historyStats.computeIfAbsent(key, k -> new AccuracyStats()).record(wasAccurate);
        log.debug("[质量预测] 结果反馈: key={} accurate={}", key, wasAccurate);
    }

    /** 返回当前历史统计样本总量（用于监控/调试） */
    public int getTotalRecordedSamples() {
        return historyStats.values().stream().mapToInt(AccuracyStats::total).sum();
    }

    @Override
    public boolean isReady() {
        return true; // 规则式预测始终可用，历史统计为增量增强
    }

    // ===== Layer 1: 规则引擎 =====

    private double computeRuleScore(String signal, int score, String confidence,
                                     Map<String, Object> technicalContext,
                                     Map<String, Object> marketContext) {
        double base = 0.55;

        // 1. 置信度先验
        if ("高".equals(confidence))      base += 0.08;
        else if ("低".equals(confidence)) base -= 0.10;

        // 2. 技术指标方向共识度（核心增强）
        base += computeTechnicalConsensus(signal, technicalContext);

        // 3. 信号强度与评分内部一致性
        base += computeSignalScoreConsistency(signal, score);

        // 4. 市场环境适配性
        base += computeMarketContextAdjustment(signal, marketContext);

        // 5. 极端评分可靠性惩罚（分梯度）
        if      (score >= 95 || score <= 5)  base -= 0.12;
        else if (score >= 90 || score <= 10) base -= 0.06;

        // 6. 中性信号天然可靠性更高
        if ("neutral".equals(signal)) base += 0.08;

        return base;
    }

    /**
     * 技术指标方向共识度打分
     * 统计 RSI / MACD / 均线 / KDJ 中与信号方向一致的数量，计算加权共识奖惩
     */
    private double computeTechnicalConsensus(String signal, Map<String, Object> technicalContext) {
        if (technicalContext == null || technicalContext.isEmpty()) return 0.0;

        boolean isBuy  = signal != null && signal.contains("buy");
        boolean isSell = signal != null && signal.contains("sell");
        int agrees = 0, disagrees = 0, total = 0;

        // RSI
        Object rsiObj = technicalContext.get("rsi");
        if (rsiObj instanceof Map<?, ?> rsiMap) {
            total++;
            double rsi6 = parseDouble(rsiMap.get("RSI6"), 50.0);
            String rsiStatus = (String) rsiMap.get("status");
            if      (isBuy  && (rsi6 < 40 || "超卖区间".equals(rsiStatus))) agrees++;
            else if (isSell && (rsi6 > 65 || "超买".equals(rsiStatus)))    agrees++;
            else if (isBuy  && rsi6 > 75) disagrees++;
            else if (isSell && rsi6 < 30) disagrees++;
            else agrees++; // 中性区间，视为无冲突
        }

        // MACD 金叉/死叉
        Object macdObj = technicalContext.get("macd");
        if (macdObj instanceof Map<?, ?> macdMap) {
            total++;
            String cross = (String) macdMap.get("cross");
            if      ("金叉".equals(cross) && isBuy)  agrees++;
            else if ("死叉".equals(cross) && isSell) agrees++;
            else if ("死叉".equals(cross) && isBuy)  disagrees++;
            else if ("金叉".equals(cross) && isSell) disagrees++;
            else agrees++;
        }

        // 均线排列
        Object maObj = technicalContext.get("ma_analysis");
        if (maObj instanceof Map<?, ?> maMap) {
            total++;
            String arrangement = (String) maMap.get("arrangement");
            if      ("多头排列".equals(arrangement) && isBuy)  agrees++;
            else if ("空头排列".equals(arrangement) && isSell) agrees++;
            else if ("空头排列".equals(arrangement) && isBuy)  disagrees++;
            else if ("多头排列".equals(arrangement) && isSell) disagrees++;
            else agrees++;
        }

        // KDJ
        Object kdjObj = technicalContext.get("kdj");
        if (kdjObj instanceof Map<?, ?> kdjMap) {
            total++;
            String kdjCross = (String) kdjMap.get("cross");
            if      ("金叉".equals(kdjCross) && isBuy)  agrees++;
            else if ("死叉".equals(kdjCross) && isSell) agrees++;
            else if ("死叉".equals(kdjCross) && isBuy)  disagrees++;
            else if ("金叉".equals(kdjCross) && isSell) disagrees++;
            else agrees++;
        }

        if (total == 0) return 0.0;
        double agreeRatio    = (double) agrees    / total;
        double disagreeRatio = (double) disagrees / total;
        // 高共识奖励 +0.18，高冲突惩罚 -0.15
        return agreeRatio * 0.18 - disagreeRatio * 0.15;
    }

    /**
     * 信号强度与评分内部一致性：strong_buy 但评分只有 55 → 内在矛盾，轻微惩罚
     */
    private double computeSignalScoreConsistency(String signal, int score) {
        if (signal == null) return 0.0;
        if ("strong_buy".equals(signal))  return score >= 85 ? 0.06 : (score < 75 ? -0.08 : 0.0);
        if ("buy".equals(signal))         return (score >= 65 && score < 85) ? 0.04 : 0.0;
        if ("strong_sell".equals(signal)) return score <= 15 ? 0.06 : (score > 25 ? -0.08 : 0.0);
        if ("sell".equals(signal))        return (score <= 35 && score > 15) ? 0.04 : 0.0;
        return 0.0;
    }

    /**
     * 市场环境适配性：极端情绪或高波动环境下方向性信号可靠性下降
     */
    private double computeMarketContextAdjustment(String signal, Map<String, Object> marketContext) {
        if (marketContext == null || signal == null) return 0.0;
        double adj = 0.0;
        boolean isBuy  = signal.contains("buy");
        boolean isSell = signal.contains("sell");

        String sentiment = (String) marketContext.get("market_sentiment");
        if      ("极度恐慌".equals(sentiment) && isBuy)  adj -= 0.10;
        else if ("极度贪婪".equals(sentiment) && isSell) adj -= 0.08;
        else if ("恐慌".equals(sentiment)     && isBuy)  adj -= 0.05;
        else if ("贪婪".equals(sentiment)     && isSell) adj -= 0.05;

        Object volObj = marketContext.get("volatility");
        if (volObj instanceof Number) {
            double vol = ((Number) volObj).doubleValue();
            if      (vol > 0.050) adj -= 0.06; // 高波动，信号噪音大
            else if (vol < 0.015) adj += 0.04; // 低波动，趋势信号更稳
        }

        return adj;
    }

    // ===== 工具方法 =====

    private String buildStatsKey(String signal, int score, String confidence) {
        return signal + "|" + scoreToRange(score) + "|" + confidence;
    }

    private static String scoreToRange(int score) {
        if (score <= 20) return "0-20";
        if (score <= 40) return "21-40";
        if (score <= 60) return "41-60";
        if (score <= 80) return "61-80";
        return "81-100";
    }

    private static double parseDouble(Object val, double defaultVal) {
        if (val == null) return defaultVal;
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private static String fmt(double v) {
        return String.format("%.0f%%", v * 100);
    }

    // ===== Layer 2: 滑动窗口准确率统计 =====

    /**
     * 基于环形缓冲区的滑动窗口准确率统计（最近 200 条）
     * 线程安全，写操作加锁，读操作通过 volatile 保证可见性。
     */
    static class AccuracyStats {
        private static final int WINDOW = 200;
        private final int[] buf = new int[WINDOW]; // 1=准确, 0=不准确
        private int writePos = 0;
        private int count = 0;
        private int accurateCount = 0;

        synchronized void record(boolean accurate) {
            if (count >= WINDOW) {
                // 覆盖旧条目时，先从统计中移除旧值
                accurateCount -= buf[writePos];
            } else {
                count++;
            }
            buf[writePos] = accurate ? 1 : 0;
            accurateCount += buf[writePos];
            writePos = (writePos + 1) % WINDOW;
        }

        synchronized int total() { return count; }

        synchronized double accuracy() {
            return count == 0 ? 0.5 : (double) accurateCount / count;
        }
    }
}
