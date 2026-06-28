package io.leavesfly.alphaforge.application.service.feedback;

import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignal;
import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignalOutcome;
import io.leavesfly.alphaforge.infrastructure.persistence.signal.DecisionSignalOutcomeRepository;
import io.leavesfly.alphaforge.infrastructure.persistence.signal.DecisionSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 信号反馈闭环服务 — 信号效果→LLM 分析改进→Few-shot 示例自动注入
 *
 * 核心闭环：
 * 1. 从历史信号效果中提取"经验教训"（正确/错误案例）
 * 2. 构建 Few-shot 示例文本，注入到下次 LLM 分析的 prompt 中
 * 3. 识别错误模式（如"在 RSI 超买时买入信号准确率低"），生成针对性提示
 *
 * 这是系统自我进化的关键环节：让 LLM 从自己的历史错误中学习。
 */
@Service
public class SignalFeedbackLoop {

    private static final Logger log = LoggerFactory.getLogger(SignalFeedbackLoop.class);

    /** Few-shot 示例最大数量 */
    private static final int MAX_CORRECT_EXAMPLES = 3;
    private static final int MAX_ERROR_EXAMPLES = 3;
    /** 查询历史信号的天数范围 */
    private static final int LOOKBACK_DAYS = 30;

    private final DecisionSignalRepository signalRepository;
    private final DecisionSignalOutcomeRepository outcomeRepository;

    public SignalFeedbackLoop(DecisionSignalRepository signalRepository,
                               DecisionSignalOutcomeRepository outcomeRepository) {
        this.signalRepository = signalRepository;
        this.outcomeRepository = outcomeRepository;
    }

    /**
     * 构建信号反馈提示（注入到 LLM 分析 prompt 中）
     *
     * @param stockCode 股票代码
     * @return 反馈提示文本（包含 Few-shot 示例和错误模式提示），无数据时返回空字符串
     */
    public String buildFeedbackPrompt(String stockCode) {
        if (stockCode == null || stockCode.isEmpty()) return "";

        try {
            LocalDateTime since = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
            List<DecisionSignal> signals = signalRepository.findByStockCodeAndCreatedAtAfter(stockCode, since);
            if (signals == null || signals.isEmpty()) return "";

            List<SignalCase> correctCases = new ArrayList<>();
            List<SignalCase> errorCases = new ArrayList<>();

            for (DecisionSignal signal : signals) {
                if (signal.getCreatedAt() == null) continue;

                List<DecisionSignalOutcome> outcomes = outcomeRepository.findBySignalId(signal.getId());
                if (outcomes == null || outcomes.isEmpty()) continue;

                // 取短期评估结果
                DecisionSignalOutcome outcome = outcomes.stream()
                        .filter(o -> "short".equals(o.getHorizon()) && "evaluated".equals(o.getEvalStatus()))
                        .findFirst()
                        .orElse(null);
                if (outcome == null || outcome.getReturnPct() == null) continue;

                SignalCase sc = new SignalCase(
                        signal.getAction(),
                        signal.getConfidence(),
                        signal.getScore(),
                        signal.getReason(),
                        outcome.getOutcome(),
                        outcome.getReturnPct(),
                        signal.getCreatedAt().toLocalDate().toString()
                );

                if ("correct".equals(outcome.getOutcome())) {
                    if (correctCases.size() < MAX_CORRECT_EXAMPLES) correctCases.add(sc);
                } else if ("incorrect".equals(outcome.getOutcome())) {
                    if (errorCases.size() < MAX_ERROR_EXAMPLES) errorCases.add(sc);
                }
            }

            if (correctCases.isEmpty() && errorCases.isEmpty()) return "";

            return formatFeedbackPrompt(correctCases, errorCases);

        } catch (Exception e) {
            log.debug("构建信号反馈提示失败: {} - {}", stockCode, e.getMessage());
            return "";
        }
    }

    /**
     * 构建全局信号效果摘要（用于系统健康报告）
     */
    public FeedbackSummary getGlobalFeedbackSummary() {
        FeedbackSummary summary = new FeedbackSummary();
        try {
            List<Map<String, Object>> stats = outcomeRepository.getOutcomeStats();
            if (stats == null) return summary;

            for (Map<String, Object> row : stats) {
                String outcome = String.valueOf(row.getOrDefault("outcome", ""));
                int count = row.get("count") instanceof Number n ? n.intValue() : 0;
                switch (outcome) {
                    case "correct" -> summary.correctCount += count;
                    case "incorrect" -> summary.incorrectCount += count;
                    case "partial" -> summary.partialCount += count;
                }
            }
            summary.totalEvaluated = summary.correctCount + summary.incorrectCount + summary.partialCount;
            summary.accuracyPct = summary.totalEvaluated > 0
                    ? (double) (summary.correctCount + summary.partialCount) / summary.totalEvaluated * 100.0
                    : 0.0;
        } catch (Exception e) {
            log.debug("获取全局信号效果摘要失败: {}", e.getMessage());
        }
        return summary;
    }

    // ===== 内部方法 =====

    private String formatFeedbackPrompt(List<SignalCase> correctCases, List<SignalCase> errorCases) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n## 历史信号反馈（系统自我学习）\n");
        sb.append("以下是系统近期对该股票的信号效果回溯，请参考这些经验调整你的分析：\n\n");

        if (!errorCases.isEmpty()) {
            sb.append("### 错误信号案例（请避免类似错误）\n");
            for (SignalCase sc : errorCases) {
                sb.append(String.format("- [%s] 信号:%s 评分:%d 置信度:%s → 结果:%s 收益:%.1f%%",
                        sc.date, sc.action, sc.score, sc.confidence,
                        sc.outcome, sc.returnPct));
                if (sc.reason != null && !sc.reason.isEmpty()) {
                    sb.append(" 原因:").append(sc.reason, 0, Math.min(100, sc.reason.length()));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!correctCases.isEmpty()) {
            sb.append("### 正确信号案例（可参考有效模式）\n");
            for (SignalCase sc : correctCases) {
                sb.append(String.format("- [%s] 信号:%s 评分:%d → 结果:%s 收益:%.1f%%\n",
                        sc.date, sc.action, sc.score, sc.outcome, sc.returnPct));
            }
            sb.append("\n");
        }

        if (!errorCases.isEmpty()) {
            sb.append("请在本次分析中特别注意避免上述错误模式，");
            sb.append("如果当前市场条件与错误案例相似，请适当降低置信度。\n");
        }

        return sb.toString();
    }

    // ===== 数据类 =====

    /** 信号案例（用于 Few-shot 示例） */
    private record SignalCase(
            String action,
            Double confidence,
            Integer score,
            String reason,
            String outcome,
            Double returnPct,
            String date
    ) {}

    /** 全局信号效果摘要 */
    public static class FeedbackSummary {
        public int totalEvaluated;
        public int correctCount;
        public int incorrectCount;
        public int partialCount;
        public double accuracyPct;

        @Override
        public String toString() {
            return String.format("FeedbackSummary{total=%d, correct=%d, incorrect=%d, partial=%d, accuracy=%.1f%%}",
                    totalEvaluated, correctCount, incorrectCount, partialCount, accuracyPct);
        }
    }
}
