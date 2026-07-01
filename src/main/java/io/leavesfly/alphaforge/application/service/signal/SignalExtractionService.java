package io.leavesfly.alphaforge.application.service.signal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisResult;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.util.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 信号提取服务 — 从 LLM 响应中提取结构化交易信号
 *
 * 从 StockAnalysisPipeline 提取的信号提取逻辑，负责：
 * - 从 JSON/文本中提取 signal/score/confidence/summary 等字段
 * - 信号标准化（normalize）
 * - 置信度解析
 * - 通知内容格式化
 * - 决策信号持久化
 */
@Service
public class SignalExtractionService {

    private static final Logger log = LoggerFactory.getLogger(SignalExtractionService.class);

    private static final Pattern SCORE_PATTERN =
            Pattern.compile("(?:评分|score|total_score)\\s*[:：]\\s*(\\d{1,3})");

    private final ObjectMapper objectMapper;
    private final DecisionSignalService decisionSignalService;

    public SignalExtractionService(ObjectMapper objectMapper,
                                     DecisionSignalService decisionSignalService) {
        this.objectMapper = objectMapper;
        this.decisionSignalService = decisionSignalService;
    }

    // ==================== 信号提取 ====================

    /**
     * 从 LLM 响应中提取结构化字段（signal/score/confidence/summary 等）
     */
    public void extractFromLlmResponse(AnalysisResult result, String response) {
        if (response == null || response.isEmpty()) {
            result.signal = "neutral";
            result.score = 50;
            return;
        }

        // 优先尝试从 JSON 中提取（结构化输出模式）
        if (tryExtractFromJson(result, response)) {
            return;
        }

        // Fallback: 从文本中提取
        extractFromText(result, response);
    }

    /** 尝试从 LLM 响应中解析 JSON 并提取结构化字段 */
    private boolean tryExtractFromJson(AnalysisResult result, String response) {
        try {
            String json = CommonUtils.extractJsonFromText(response);
            if (json == null) return false;

            JsonNode node = objectMapper.readTree(json);

            String signal = node.path("signal").asText("");
            if (!signal.isEmpty()) {
                result.signal = normalize(signal);
            }

            int score = node.path("score").asInt(-1);
            if (score >= 0 && score <= 100) {
                result.score = score;
            }

            String confidence = node.path("confidence").asText(null);
            if (confidence != null && !confidence.isEmpty()) result.confidence = confidence;

            String summary = node.path("summary").asText(null);
            if (summary != null && !summary.isEmpty()) result.summary = summary;

            String advice = node.path("operation_advice").asText(null);
            if (advice != null && !advice.isEmpty()) result.operationAdvice = advice;

            String riskNote = node.path("risk_note").asText(null);
            if (riskNote != null && !riskNote.isEmpty()) result.riskNote = riskNote;

            JsonNode targetPrice = node.path("target_price");
            if (targetPrice.isNumber()) result.targetPrice = targetPrice.asDouble();

            JsonNode stopLoss = node.path("stop_loss_price");
            if (stopLoss.isNumber()) result.stopLossPrice = stopLoss.asDouble();

            return result.signal != null && result.score != null;
        } catch (Exception e) {
            log.debug("JSON 提取失败，回退到文本匹配: {}", e.getMessage());
            return false;
        }
    }

    /** 文本模式提取（Fallback） */
    private void extractFromText(AnalysisResult result, String response) {
        String lower = response.toLowerCase();
        if (lower.contains("强烈买入") || lower.contains("strong buy")) {
            result.signal = "strong_buy";
        } else if (lower.contains("买入") || lower.contains("buy")) {
            result.signal = "buy";
        } else if (lower.contains("卖出") || lower.contains("sell")) {
            result.signal = "sell";
        } else {
            result.signal = "neutral";
        }

        Integer extractedScore = extractScoreFromText(response);
        if (extractedScore != null) {
            result.score = extractedScore;
        } else if (result.score == null) {
            result.score = switch (result.signal) {
                case "strong_buy" -> 80;
                case "buy" -> 65;
                case "sell" -> 35;
                case "strong_sell" -> 20;
                default -> 50;
            };
        }
    }

    /** 从文本中提取评分数字 */
    private Integer extractScoreFromText(String response) {
        Matcher matcher = SCORE_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                int score = Integer.parseInt(matcher.group(1));
                if (score >= 0 && score <= 100) return score;
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    // ==================== 信号工具方法 ====================

    /** 信号标准化 */
    public String normalize(String signal) {
        if (signal == null) return "neutral";
        String s = signal.toLowerCase().trim();
        // 兼容中英文
        if (s.contains("strong") && s.contains("buy")) return "strong_buy";
        if (s.contains("strong") && s.contains("sell")) return "strong_sell";
        if (s.contains("buy") || s.contains("买入")) return "buy";
        if (s.contains("sell") || s.contains("卖出")) return "sell";
        return "neutral";
    }

    /** 置信度文本 → 数值 */
    public double parseConfidenceLevel(String confidence) {
        if (confidence == null) return 0.3;
        if ("高".equals(confidence) || "high".equals(confidence)) return 0.8;
        if ("中等".equals(confidence) || "medium".equals(confidence)) return 0.5;
        return 0.3;
    }

    /** 信号 Emoji */
    public String getSignalEmoji(String signal) {
        if (signal == null) return "⚖️";
        return switch (signal) {
            case "strong_buy" -> "🔥";
            case "buy" -> "📈";
            case "sell" -> "📉";
            case "strong_sell" -> "⚠️";
            default -> "⚖️";
        };
    }

    /** 格式化通知内容 */
    public String formatNotification(AnalysisReport report) {
        return String.format("信号: %s | 评分: %d/100\n价格: %.2f (%+.2f%%)\n%s",
                report.getSignal(),
                report.getTotalScore() != null ? report.getTotalScore() : 0,
                report.getCurrentPrice() != null ? report.getCurrentPrice() : 0.0,
                report.getChangePct() != null ? report.getChangePct() : 0.0,
                report.getSummary() != null ? report.getSummary() : "");
    }

    // ==================== 决策信号持久化 ====================

    /**
     * 从分析结果中提取并持久化决策信号
     */
    public void persistDecisionSignal(AnalysisReport report, AnalysisResult result) {
        if (result == null || result.signal == null) return;
        if ("neutral".equals(result.signal)) return;

        try {
            String action = result.signal.contains("buy") ? "buy"
                    : result.signal.contains("sell") ? "sell" : "hold";
            Integer score = result.score != null ? result.score : 50;
            Double confidence = result.confidence != null
                    ? parseConfidenceLevel(result.confidence) : 0.5;

            decisionSignalService.extractFromReport(
                    report.getId(), report.getStockCode(), report.getStockName(),
                    action, confidence, score,
                    result.targetPrice, result.stopLossPrice, result.operationAdvice);
        } catch (Exception e) {
            log.debug("[{}] 信号提取失败: {}", report.getStockCode(), e.getMessage());
        }
    }
}
