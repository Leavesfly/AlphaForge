package io.leavesfly.alphaforge.domain.service;

import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisResult;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.SignalQualityPredictor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 信号独立验证器（Maker-Checker 分离模式） — 领域验证服务
 *
 * 核心设计原则：生成信号的 ReActAgent 不评判自身输出。
 * 本组件作为独立的"Checker"，在 Pipeline 的 LLM 分析结果写入数据库前，
 * 通过多维交叉验证判断信号可信度，并在必要时调整信号。
 *
 * 验证维度：
 * 1. 技术指标一致性检验 —— 技术评分与 LLM 信号是否相符
 * 2. 矛盾检测 —— RSI 超买却建议买入 / RSI 超卖却建议卖出等明显矛盾
 * 3. 多指标共识度 —— 几个维度（MACD/KDJ/RSI/均线）与信号方向一致
 * 4. 大盘环境门控 —— 市场情绪极端时拦截高置信度信号
 * 5. 信号稳定性检验 —— 历史信号频繁反转则降低本次置信度
 */
@Component
public class SignalVerifier {

    private static final Logger log = LoggerFactory.getLogger(SignalVerifier.class);

    // 技术评分与 LLM 信号一致性阈值（可自适应调整）
    private int scoreAgreeBuyThreshold = 60;
    private int scoreAgreeSellThreshold = 40;
    // 矛盾检测阈值（默认值，可根据市场环境自适应）
    private double rsiOverbought = 80.0;
    private double rsiOversold = 20.0;

    // 可选：ML 信号质量预测器
    private final SignalQualityPredictor qualityPredictor;

    @Autowired
    public SignalVerifier(@Autowired(required = false) SignalQualityPredictor qualityPredictor) {
        this.qualityPredictor = qualityPredictor;
        if (qualityPredictor != null && qualityPredictor.isReady()) {
            log.info("SignalVerifier 已集成 ML 信号质量预测器: {}", qualityPredictor.getClass().getSimpleName());
        }
    }

    /** 根据大盘环境自适应调整 RSI 阈值 */
    private void adaptThresholds(Map<String, Object> marketContext) {
        // 重置为默认值
        rsiOverbought = 80.0;
        rsiOversold = 20.0;
        scoreAgreeBuyThreshold = 60;
        scoreAgreeSellThreshold = 40;

        if (marketContext == null || marketContext.isEmpty()) return;

        String sentiment = String.valueOf(marketContext.getOrDefault("market_sentiment", ""));
        // 牛市环境下提高超买阈值（允许更激进的买入）
        if (sentiment.contains("乐观") || sentiment.contains("回暖")) {
            rsiOverbought = 85.0;
            scoreAgreeBuyThreshold = 55;
        }
        // 熊市环境下降低超卖阈值（更保守的买入）
        else if (sentiment.contains("悲观") || sentiment.contains("谨慎")) {
            rsiOversold = 15.0;
            scoreAgreeBuyThreshold = 65;
            scoreAgreeSellThreshold = 45;
        }
    }

    /**
     * 验证分析结果，返回含调整建议的验证报告
     */
    public VerificationResult verify(
            AnalysisResult result,
            Map<String, Object> technicalResult,
            Map<String, Object> marketContext,
            List<StockDailyData> historyData,
            List<String> recentSignals) {

        VerificationResult vr = new VerificationResult();
        vr.originalSignal = result.signal;
        vr.originalScore = result.score;

        List<String> contradictions = new ArrayList<>();
        List<String> agreements = new ArrayList<>();
        int agreementCount = 0;
        int totalChecks = 0;

        // ===== 1. 技术评分 vs LLM 信号一致性检验 =====
        if (result.score != null && result.signal != null) {
            totalChecks++;
            boolean buySignal = result.signal.contains("buy");
            boolean sellSignal = result.signal.contains("sell");

            if (buySignal && result.score >= scoreAgreeBuyThreshold) {
                agreementCount++;
                agreements.add("技术评分(" + result.score + ")与买入信号一致");
            } else if (sellSignal && result.score <= scoreAgreeSellThreshold) {
                agreementCount++;
                agreements.add("技术评分(" + result.score + ")与卖出信号一致");
            } else if (!buySignal && !sellSignal) {
                agreementCount++;
                agreements.add("中性信号，评分中性");
            } else {
                contradictions.add("技术评分(" + result.score + ")与信号(" + result.signal + ")方向不符");
            }
        }

        // 自适应阈值：根据大盘环境调整 RSI 阈值
        adaptThresholds(marketContext);

        // ===== 2. RSI 矛盾检测 =====
        if (technicalResult != null) {
            Object rsiObj = technicalResult.get("rsi");
            if (rsiObj instanceof Map<?, ?> rsiMap) {
                totalChecks++;
                String rsiStatus = (String) rsiMap.get("status");
                String rsi6Str = (String) rsiMap.get("RSI6");
                double rsi6 = parseDouble(rsi6Str, 50.0);

                boolean buySignal = result.signal != null && result.signal.contains("buy");
                boolean sellSignal = result.signal != null && result.signal.contains("sell");

                if (buySignal && rsi6 >= rsiOverbought) {
                    contradictions.add("RSI(" + String.format("%.1f", rsi6) + ")超买区间(阈" + String.format("%.0f", rsiOverbought) + ")，但信号为买入");
                } else if (sellSignal && rsi6 <= rsiOversold) {
                    contradictions.add("RSI(" + String.format("%.1f", rsi6) + ")超卖区间(阈" + String.format("%.0f", rsiOversold) + ")，但信号为卖出");
                } else if (buySignal && "超卖区间".equals(rsiStatus)) {
                    agreements.add("RSI超卖支持买入信号");
                    agreementCount++;
                } else if (sellSignal && "超买".equals(rsiStatus)) {
                    agreements.add("RSI超买支持卖出信号");
                    agreementCount++;
                } else {
                    agreementCount++;
                }
            }

            // ===== 3. MACD 多指标共识 =====
            Object macdObj = technicalResult.get("macd");
            if (macdObj instanceof Map<?, ?> macdMap) {
                totalChecks++;
                String cross = (String) macdMap.get("cross");
                boolean buySignal = result.signal != null && result.signal.contains("buy");
                boolean sellSignal = result.signal != null && result.signal.contains("sell");

                if ("金叉".equals(cross) && buySignal) {
                    agreementCount++;
                    agreements.add("MACD金叉支持买入信号");
                } else if ("死叉".equals(cross) && sellSignal) {
                    agreementCount++;
                    agreements.add("MACD死叉支持卖出信号");
                } else if ("死叉".equals(cross) && buySignal) {
                    contradictions.add("MACD死叉与买入信号矛盾");
                } else if ("金叉".equals(cross) && sellSignal) {
                    contradictions.add("MACD金叉与卖出信号矛盾");
                } else {
                    agreementCount++;
                }
            }

            // ===== 4. 均线排列共识 =====
            Object maObj = technicalResult.get("ma_analysis");
            if (maObj instanceof Map<?, ?> maMap) {
                totalChecks++;
                String arrangement = (String) maMap.get("arrangement");
                boolean buySignal = result.signal != null && result.signal.contains("buy");
                boolean sellSignal = result.signal != null && result.signal.contains("sell");

                if ("多头排列".equals(arrangement) && buySignal) {
                    agreementCount++;
                    agreements.add("均线多头排列支持买入");
                } else if ("空头排列".equals(arrangement) && sellSignal) {
                    agreementCount++;
                    agreements.add("均线空头排列支持卖出");
                } else if ("空头排列".equals(arrangement) && buySignal) {
                    contradictions.add("均线空头排列与买入信号矛盾");
                } else if ("多头排列".equals(arrangement) && sellSignal) {
                    contradictions.add("均线多头排列与卖出信号矛盾");
                } else {
                    agreementCount++;
                }
            }
        }

        // ===== 5. 大盘环境极端门控 =====
        if (marketContext != null) {
            String sentiment = (String) marketContext.get("market_sentiment");
            if ("极度恐慌".equals(sentiment) && result.signal != null && result.signal.contains("buy")) {
                totalChecks++;
                contradictions.add("大盘极度恐慌情绪下买入信号风险极高");
            } else if ("极度贪婪".equals(sentiment) && result.signal != null && result.signal.contains("sell")) {
                totalChecks++;
                contradictions.add("大盘极度贪婪情绪下卖出信号需审慎");
            }
        }

        // ===== 6. 信号稳定性检验（频繁反转降低置信度）=====
        if (recentSignals != null && recentSignals.size() >= 3) {
            int reversals = countReversals(recentSignals);
            if (reversals >= 2) {
                vr.stabilityNote = "近期信号频繁反转(" + reversals + "次)，置信度降低";
                log.debug("[{}] 信号稳定性警告: {}", result.stockCode, vr.stabilityNote);
            }
        }

        // ===== 综合判定 =====
        vr.contradictions = contradictions;
        vr.agreements = agreements;
        vr.agreementCount = agreementCount;
        vr.totalChecks = totalChecks;
        vr.consensusRatio = totalChecks > 0 ? (double) agreementCount / totalChecks : 1.0;

        // ML 信号质量预测（可选）
        if (qualityPredictor != null && qualityPredictor.isReady()
                && result.signal != null && result.score != null) {
            try {
                SignalQualityPredictor.QualityPrediction mlPrediction =
                        qualityPredictor.predict(result.signal, result.score,
                                result.confidence, technicalResult, marketContext);
                if (mlPrediction.suggestDowngrade && !vr.wasAdjusted()) {
                    vr.adjustedSignal = "neutral";
                    vr.adjustedScore = 50;
                    vr.adjustmentReason = "ML预测器建议降级: " + mlPrediction.reason;
                    log.warn("[{}] ML信号质量预测器建议降级信号: {} (准确率:{})",
                            result.stockCode, result.signal, String.format("%.1f%%", mlPrediction.predictedAccuracy * 100));
                }
                if (mlPrediction.adjustedConfidence != null) {
                    vr.mlAdjustedConfidence = mlPrediction.adjustedConfidence;
                }
            } catch (Exception e) {
                log.debug("ML信号质量预测失败: {}", e.getMessage());
            }
        }

        // 置信度判定
        if (!contradictions.isEmpty() && vr.consensusRatio < 0.5) {
            vr.confidence = "low";
            vr.passed = false;
            if (contradictions.size() >= 2) {
                vr.adjustedSignal = "neutral";
                vr.adjustedScore = 50;
                vr.adjustmentReason = "多项指标矛盾，信号降级为中性: " + String.join("; ", contradictions);
                log.warn("[{}] Verifier信号降级: {} → neutral | {}", result.stockCode,
                        result.signal, vr.adjustmentReason);
            } else {
                vr.adjustedSignal = result.signal;
                vr.adjustedScore = result.score;
                vr.adjustmentReason = "存在矛盾: " + contradictions.get(0);
            }
        } else if (vr.consensusRatio >= 0.75) {
            vr.confidence = "high";
            vr.passed = true;
            vr.adjustedSignal = result.signal;
            vr.adjustedScore = result.score;
        } else {
            vr.confidence = "medium";
            vr.passed = true;
            vr.adjustedSignal = result.signal;
            vr.adjustedScore = result.score;
        }

        log.info("[{}] Verifier完成 | 信号:{} → {} | 共识度:{}/{} | 矛盾:{} | 置信:{}",
                result.stockCode, result.signal, vr.adjustedSignal,
                agreementCount, totalChecks, contradictions.size(), vr.confidence);

        return vr;
    }

    /**
     * 将验证结果写回 AnalysisResult（由 Pipeline 调用）
     */
    public void applyVerification(AnalysisResult result, VerificationResult vr) {
        if (vr.adjustedSignal != null && !vr.adjustedSignal.equals(result.signal)) {
            result.signal = vr.adjustedSignal;
        }
        if (vr.adjustedScore != null && !vr.adjustedScore.equals(result.score)) {
            result.score = vr.adjustedScore;
        }
        String verifyNote = buildVerifyNote(vr);
        if (result.riskNote == null || result.riskNote.isEmpty()) {
            result.riskNote = verifyNote;
        } else {
            result.riskNote = result.riskNote + " | " + verifyNote;
        }
        if (!vr.passed || "low".equals(vr.confidence)) {
            result.confidence = "低";
        } else if ("high".equals(vr.confidence)) {
            result.confidence = "高";
        }
        if (vr.stabilityNote != null) {
            result.riskNote = result.riskNote + " | " + vr.stabilityNote;
        }
    }

    // ===== 辅助方法 =====

    private int countReversals(List<String> signals) {
        int count = 0;
        for (int i = 1; i < signals.size(); i++) {
            String prev = signals.get(i - 1);
            String curr = signals.get(i);
            boolean prevBuy = prev != null && prev.contains("buy");
            boolean prevSell = prev != null && prev.contains("sell");
            boolean currBuy = curr != null && curr.contains("buy");
            boolean currSell = curr != null && curr.contains("sell");
            if ((prevBuy && currSell) || (prevSell && currBuy)) {
                count++;
            }
        }
        return count;
    }

    private double parseDouble(String s, double defaultVal) {
        if (s == null) return defaultVal;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private String buildVerifyNote(VerificationResult vr) {
        StringBuilder sb = new StringBuilder("Verifier[共识");
        sb.append(vr.agreementCount).append("/").append(vr.totalChecks).append("]");
        if (!vr.contradictions.isEmpty()) {
            sb.append(" 矛盾:").append(vr.contradictions.get(0));
        } else if (!vr.agreements.isEmpty()) {
            sb.append(" ✓").append(vr.agreements.get(0));
        }
        return sb.toString();
    }

    // ===== 验证结果数据类 =====

    /**
     * 验证报告
     */
    public static class VerificationResult {
        public String originalSignal;
        public Integer originalScore;
        public String adjustedSignal;
        public Integer adjustedScore;
        public String adjustmentReason;
        public boolean passed = true;
        public String confidence = "medium";
        public int agreementCount;
        public int totalChecks;
        public double consensusRatio;
        public List<String> contradictions = new ArrayList<>();
        public List<String> agreements = new ArrayList<>();
        public String stabilityNote;
        public String mlAdjustedConfidence;

        public boolean wasAdjusted() {
            return adjustedSignal != null && !adjustedSignal.equals(originalSignal);
        }

        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("共识度:").append(String.format("%.0f%%", consensusRatio * 100));
            sb.append(" 置信:").append(confidence);
            if (wasAdjusted()) {
                sb.append(" 信号调整:").append(originalSignal).append("→").append(adjustedSignal);
            }
            if (!contradictions.isEmpty()) {
                sb.append(" 矛盾:").append(contradictions.size()).append("项");
            }
            return sb.toString();
        }
    }
}
