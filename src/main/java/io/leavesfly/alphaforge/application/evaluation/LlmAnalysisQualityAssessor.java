package io.leavesfly.alphaforge.application.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 分析质量评估器 — 自动化评估 LLM 生成的分析报告
 *
 * 对应论文：
 * - AlphaForgeBench: 端到端策略设计评估
 * - PHANTOM (Goldman Sachs, NeurIPS 2025): 金融长上下文 QA 幻觉检测
 *
 * 评估方式（规则引擎，无需额外 LLM 调用）：
 * 1. 幻觉检测 — 检查报告中引用的数值是否在提供的数据上下文中存在
 * 2. 逻辑一致性 — 信号与评分的匹配度、推理链是否自洽
 * 3. 完整性 — 是否覆盖技术面/基本面/风险等关键维度
 * 4. 可操作性 — 是否包含入场价/止损价/目标价
 * 5. 风险披露 — 是否提及风险因素
 */
@Component
public class LlmAnalysisQualityAssessor {

    private static final Logger log = LoggerFactory.getLogger(LlmAnalysisQualityAssessor.class);

    // ===== 信号-评分匹配规则 =====
    private static final Map<String, int[]> SIGNAL_SCORE_RANGES = Map.of(
            "strong_buy", new int[]{75, 100},
            "buy", new int[]{55, 80},
            "neutral", new int[]{40, 60},
            "sell", new int[]{20, 50},
            "strong_sell", new int[]{0, 30}
    );

    // ===== 关键词模式 =====
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "(?:RSI|MACD|PE|PB|ROE|市盈率|市净率|净资产收益率|换手率|涨跌幅|振幅|波动率|成交量|成交额|夏普|回撤|胜率|收益)[^0-9]*(\\d+\\.?\\d*)");

    private static final List<String> REQUIRED_DIMENSIONS = List.of(
            "技术", "趋势", "均线", "量", "风险", "支撑", "阻力"
    );

    private static final List<String> OPERATION_KEYWORDS = List.of(
            "入场", "止损", "目标", "仓位", "建仓", "加仓", "减仓", "止盈"
    );

    private static final List<String> RISK_KEYWORDS = List.of(
            "风险", "下行", "不确定性", "警示", "注意", "谨慎", "波动", "回撤"
    );

    /**
     * 评估 LLM 分析报告质量
     *
     * @param llmResponse  LLM 生成的分析文本（或 JSON）
     * @param contextData  提供给 LLM 的上下文数据（用于幻觉检测，可为 null）
     * @return 质量评估结果
     */
    public LlmAnalysisQuality assess(String llmResponse, Map<String, Object> contextData) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return buildEmptyQuality();
        }

        Map<String, LlmAnalysisQuality.DimensionResult> dims = new LinkedHashMap<>();
        List<String> hallucinations = new ArrayList<>();
        List<String> contradictions = new ArrayList<>();
        List<String> missingDims = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        // 1. 数据准确性（幻觉检测）
        LlmAnalysisQuality.DimensionResult dataDim = checkDataAccuracy(llmResponse, contextData, hallucinations);
        dims.put("data_accuracy", dataDim);

        // 2. 逻辑一致性
        LlmAnalysisQuality.DimensionResult logicDim = checkLogicalConsistency(llmResponse, contradictions);
        dims.put("logical_consistency", logicDim);

        // 3. 完整性
        LlmAnalysisQuality.DimensionResult completenessDim = checkCompleteness(llmResponse, missingDims);
        dims.put("completeness", completenessDim);

        // 4. 可操作性
        LlmAnalysisQuality.DimensionResult actionabilityDim = checkActionability(llmResponse);
        dims.put("actionability", actionabilityDim);

        // 5. 风险披露
        LlmAnalysisQuality.DimensionResult riskDim = checkRiskDisclosure(llmResponse);
        dims.put("risk_disclosure", riskDim);

        // 综合评分
        double overall = 0.25 * dataDim.score()
                + 0.25 * logicDim.score()
                + 0.20 * completenessDim.score()
                + 0.20 * actionabilityDim.score()
                + 0.10 * riskDim.score();

        // 幻觉和逻辑矛盾额外惩罚
        if (!hallucinations.isEmpty()) {
            overall = QualityScoreUtils.applyPenalty(overall, 5, hallucinations.size());
            suggestions.add("报告中存在 " + hallucinations.size() + " 处数据幻觉，请核对引用数据");
        }
        if (!contradictions.isEmpty()) {
            overall = QualityScoreUtils.applyPenalty(overall, 5, contradictions.size());
            suggestions.add("报告存在 " + contradictions.size() + " 处逻辑矛盾，请检查信号与评分匹配性");
        }
        if (!missingDims.isEmpty()) {
            suggestions.add("缺失分析维度: " + String.join(", ", missingDims));
        }

        overall = QualityScoreUtils.clampScore(overall);

        log.info("LLM分析质量评估: score={:.1f} level={} 幻觉={} 矛盾={} 缺失={}",
                overall, LlmAnalysisQuality.QualityLevel.fromScore(overall),
                hallucinations.size(), contradictions.size(), missingDims.size());

        return new LlmAnalysisQuality(overall, dims, hallucinations, contradictions,
                missingDims, suggestions);
    }

    // ===== 幻觉检测 =====

    /**
     * 幻觉检测 — 检查报告中引用的数值是否在上下文数据中存在
     */
    private LlmAnalysisQuality.DimensionResult checkDataAccuracy(String response,
                                                                    Map<String, Object> contextData,
                                                                    List<String> hallucinations) {
        if (contextData == null || contextData.isEmpty()) {
            return new LlmAnalysisQuality.DimensionResult("数据准确性", 70,
                    "无上下文数据可供交叉验证");
        }

        // 构建上下文中所有数值的集合
        Set<String> contextNumbers = extractContextNumbers(contextData);

        // 从报告中提取引用的数值
        List<String> reportNumbers = extractReportNumbers(response);

        int verified = 0;
        int totalChecked = 0;

        for (String reportNum : reportNumbers) {
            totalChecked++;
            boolean found = contextNumbers.stream().anyMatch(ctxNum ->
                    Math.abs(Double.parseDouble(ctxNum) - Double.parseDouble(reportNum)) < 0.5);
            if (found) {
                verified++;
            } else {
                // 数值在上下文中不存在 → 可能是幻觉
                hallucinations.add(String.format("引用数值 %s 在提供的数据中未找到", reportNum));
            }
        }

        double score;
        if (totalChecked == 0) {
            score = 80; // 没有引用具体数值，无法判断
        } else {
            score = (double) verified / totalChecked * 100;
        }

        // 幻觉数量惩罚
        if (hallucinations.size() > 3) score *= 0.7;

        String detail = String.format("引用数值 %d 个，验证通过 %d 个，幻觉 %d 个",
                totalChecked, verified, hallucinations.size());

        return new LlmAnalysisQuality.DimensionResult("数据准确性", score, detail,
                hallucinations.size() > 3 ? hallucinations.subList(0, 3) : hallucinations);
    }

    /** 从上下文数据中提取所有数值 */
    private Set<String> extractContextNumbers(Map<String, Object> contextData) {
        Set<String> numbers = new HashSet<>();
        for (Object value : contextData.values()) {
            if (value instanceof Number n) {
                numbers.add(String.valueOf(n.doubleValue()));
            } else if (value instanceof String s) {
                Matcher m = NUMBER_PATTERN.matcher(s);
                while (m.find()) {
                    numbers.add(m.group(1));
                }
            } else if (value instanceof Map<?, ?> map) {
                numbers.addAll(extractContextNumbers((Map<String, Object>) map));
            }
        }
        return numbers;
    }

    /** 从报告文本中提取引用的数值 */
    private List<String> extractReportNumbers(String response) {
        List<String> numbers = new ArrayList<>();
        Matcher m = NUMBER_PATTERN.matcher(response);
        while (m.find()) {
            numbers.add(m.group(1));
        }
        return numbers;
    }

    // ===== 逻辑一致性检查 =====

    /**
     * 检查信号与评分是否匹配、推理是否自洽
     */
    private LlmAnalysisQuality.DimensionResult checkLogicalConsistency(String response,
                                                                         List<String> contradictions) {
        String signal = extractField(response, "signal");
        int score = extractIntField(response, "score");

        double consistencyScore = 100;

        // 检查信号-评分匹配
        if (signal != null && !signal.isEmpty() && score >= 0) {
            int[] range = SIGNAL_SCORE_RANGES.get(signal);
            if (range != null) {
                if (score < range[0] || score > range[1]) {
                    contradictions.add(String.format("信号 %s 与评分 %d 不匹配（期望区间 %d-%d）",
                            signal, score, range[0], range[1]));
                    consistencyScore -= 30;
                }
            }
        }

        // 检查置信度与评分一致性
        String confidence = extractField(response, "confidence");
        if (confidence != null && score >= 0) {
            if (("高".equals(confidence) && score < 60) || ("低".equals(confidence) && score > 70)) {
                contradictions.add(String.format("置信度 %s 与评分 %d 不一致", confidence, score));
                consistencyScore -= 15;
            }
        }

        // 检查信号与文本情绪一致性
        if (signal != null) {
            boolean hasPositiveWords = response.contains("上涨") || response.contains("看多") || response.contains("买入");
            boolean hasNegativeWords = response.contains("下跌") || response.contains("看空") || response.contains("卖出");

            if (("buy".equals(signal) || "strong_buy".equals(signal)) && hasNegativeWords && !hasPositiveWords) {
                contradictions.add("信号为买入但文本情绪偏空");
                consistencyScore -= 20;
            }
            if (("sell".equals(signal) || "strong_sell".equals(signal)) && hasPositiveWords && !hasNegativeWords) {
                contradictions.add("信号为卖出但文本情绪偏多");
                consistencyScore -= 20;
            }
        }

        consistencyScore = Math.max(0, consistencyScore);
        String detail = String.format("信号=%s 评分=%d 置信度=%s 矛盾数=%d",
                signal != null ? signal : "未检测", score,
                confidence != null ? confidence : "未检测", contradictions.size());

        return new LlmAnalysisQuality.DimensionResult("逻辑一致性", consistencyScore, detail, contradictions);
    }

    // ===== 完整性检查 =====

    private LlmAnalysisQuality.DimensionResult checkCompleteness(String response,
                                                                    List<String> missingDims) {
        int covered = 0;
        for (String dim : REQUIRED_DIMENSIONS) {
            if (response.contains(dim)) {
                covered++;
            } else {
                missingDims.add(dim);
            }
        }

        double score = (double) covered / REQUIRED_DIMENSIONS.size() * 100;
        String detail = String.format("覆盖 %d/%d 关键维度", covered, REQUIRED_DIMENSIONS.size());
        return new LlmAnalysisQuality.DimensionResult("完整性", score, detail);
    }

    // ===== 可操作性检查 =====

    private LlmAnalysisQuality.DimensionResult checkActionability(String response) {
        int found = 0;
        List<String> missing = new ArrayList<>();
        for (String keyword : OPERATION_KEYWORDS) {
            if (response.contains(keyword)) {
                found++;
            } else {
                missing.add(keyword);
            }
        }

        double score = (double) found / OPERATION_KEYWORDS.size() * 100;
        String detail = String.format("操作要素 %d/%d", found, OPERATION_KEYWORDS.size());
        return new LlmAnalysisQuality.DimensionResult("可操作性", score, detail,
                missing.size() > 4 ? missing.subList(0, 3) : missing);
    }

    // ===== 风险披露检查 =====

    private LlmAnalysisQuality.DimensionResult checkRiskDisclosure(String response) {
        int found = 0;
        for (String keyword : RISK_KEYWORDS) {
            if (response.contains(keyword)) found++;
        }

        double score = found >= 3 ? 90 : found >= 1 ? 60 : 30;
        String detail = String.format("风险关键词 %d 个", found);
        return new LlmAnalysisQuality.DimensionResult("风险披露", score, detail);
    }

    // ===== 辅助方法 =====

    private String extractField(String response, String field) {
        try {
            String pattern = "\"" + field + "\"";
            int idx = response.indexOf(pattern);
            if (idx < 0) return null;
            int colon = response.indexOf(":", idx);
            int q1 = response.indexOf("\"", colon + 1);
            int q2 = response.indexOf("\"", q1 + 1);
            if (q1 >= 0 && q2 > q1) return response.substring(q1 + 1, q2);
        } catch (Exception ignored) {}
        return null;
    }

    private int extractIntField(String response, String field) {
        try {
            String pattern = "\"" + field + "\"";
            int idx = response.indexOf(pattern);
            if (idx < 0) return -1;
            int colon = response.indexOf(":", idx);
            int start = colon + 1;
            while (start < response.length() && !Character.isDigit(response.charAt(start)) &&
                    response.charAt(start) != '-') start++;
            int end = start;
            while (end < response.length() && (Character.isDigit(response.charAt(end)) ||
                    response.charAt(end) == '.')) end++;
            if (end > start) return (int) Double.parseDouble(response.substring(start, end));
        } catch (Exception ignored) {}
        return -1;
    }

    private LlmAnalysisQuality buildEmptyQuality() {
        return new LlmAnalysisQuality(0, Map.of(),
                List.of(), List.of(), List.of(), List.of("LLM 响应为空"));
    }
}
