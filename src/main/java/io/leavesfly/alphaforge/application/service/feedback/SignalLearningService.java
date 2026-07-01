package io.leavesfly.alphaforge.application.service.feedback;

import io.leavesfly.alphaforge.application.factor.evolution.FactorEvolutionMemory;
import io.leavesfly.alphaforge.application.factor.evolution.EvolvableFactorLibrary;
import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignal;
import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignalOutcome;
import io.leavesfly.alphaforge.domain.repository.signal.DecisionSignalOutcomeRepository;
import io.leavesfly.alphaforge.domain.repository.signal.DecisionSignalRepository;
import io.leavesfly.alphaforge.domain.model.feedback.ErrorPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 信号学习服务 — 统一的经验/反馈/因子经验学习入口
 *
 * 合并了原 SignalLearningService + SignalFeedbackLoop，消除中间委托层，提供单一入口：
 * 1. recordExperience() — 记录分析经验（委托给 ExperienceMemory）
 * 2. buildLearningPrompt() — 统一构建学习提示（合并信号反馈 + 经验提示）
 * 3. buildUnifiedExperienceHint() — 合并信号级+因子级经验提示（原 FactorExperienceBridge 能力）
 * 4. getErrorPatterns() — 获取错误模式（委托给 ExperienceMemory）
 * 5. getGlobalFeedbackSummary() — 获取全局反馈摘要
 * 6. updateOutcome() — 更新经验效果（委托给 ExperienceMemory）
 * 7. buildFeedbackPrompt() — 构建 Few-shot 信号反馈提示
 */
@Service
public class SignalLearningService {

    private static final Logger log = LoggerFactory.getLogger(SignalLearningService.class);

    /** Few-shot 示例最大数量 */
    private static final int MAX_CORRECT_EXAMPLES = 3;
    private static final int MAX_ERROR_EXAMPLES = 3;
    /** 查询历史信号的天数范围 */
    private static final int LOOKBACK_DAYS = 30;

    private final ExperienceMemory experienceMemory;
    private final DecisionSignalRepository signalRepository;
    private final DecisionSignalOutcomeRepository outcomeRepository;

    /** 可选依赖：因子进化记忆（因子级经验注入） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FactorEvolutionMemory factorEvolutionMemory;

    /** 可选依赖：可进化因子库（因子推荐） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EvolvableFactorLibrary factorLibrary;

    public SignalLearningService(ExperienceMemory experienceMemory,
                                  DecisionSignalRepository signalRepository,
                                  DecisionSignalOutcomeRepository outcomeRepository) {
        this.experienceMemory = experienceMemory;
        this.signalRepository = signalRepository;
        this.outcomeRepository = outcomeRepository;
    }

    // ==================== 信号级经验 ====================

    /**
     * 记录一次分析经验
     */
    public void recordExperience(String stockCode, String signal, int score, String confidence,
                                  Map<String, Object> technicalSnapshot, String marketSentiment) {
        experienceMemory.recordExperience(stockCode, signal, score, confidence,
                technicalSnapshot, marketSentiment);
    }

    /**
     * 更新经验效果（当信号被评估后调用）
     */
    public void updateOutcome(String stockCode, String date, String outcome, Double returnPct) {
        experienceMemory.updateOutcome(stockCode, date, outcome, returnPct);
    }

    /**
     * 统一构建学习提示 — 合并信号反馈 Few-shot 示例和经验记忆提示
     */
    public String buildLearningPrompt(String stockCode, Map<String, Object> technicalSnapshot) {
        StringBuilder sb = new StringBuilder();

        // 1. 信号反馈 Few-shot 示例（来自 DB 历史信号效果）
        try {
            String feedback = buildFeedbackPrompt(stockCode);
            if (feedback != null && !feedback.isBlank()) {
                sb.append(feedback);
            }
        } catch (Exception e) {
            log.debug("构建信号反馈提示失败: {} - {}", stockCode, e.getMessage());
        }

        // 2. 经验记忆提示（来自内存中的条件匹配经验）
        try {
            String expHint = experienceMemory.getExperienceHint(stockCode, technicalSnapshot);
            if (expHint != null && !expHint.isBlank()) {
                sb.append(expHint);
            }
        } catch (Exception e) {
            log.debug("构建经验记忆提示失败: {} - {}", stockCode, e.getMessage());
        }

        return sb.toString();
    }

    public List<ErrorPattern> getErrorPatterns() {
        return experienceMemory.getErrorPatterns();
    }

    public String getExperienceHint(String stockCode, Map<String, Object> currentConditions) {
        return experienceMemory.getExperienceHint(stockCode, currentConditions);
    }

    // ==================== 信号反馈闭环（原 SignalFeedbackLoop） ====================

    /**
     * 构建信号反馈提示（注入到 LLM 分析 prompt 中）
     *
     * 从历史信号效果中提取"经验教训"（正确/错误案例），
     * 构建 Few-shot 示例文本，让 LLM 从自己的历史错误中学习。
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

    // ===== 信号反馈内部方法 =====

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

    // ==================== 因子级经验（原 FactorExperienceBridge 能力） ====================

    /**
     * 构建统一经验提示文本 — 合并信号级经验 + 因子级经验，注入到股票分析 LLM prompt 中
     *
     * @param stockCode         股票代码
     * @param currentConditions 当前技术指标快照
     * @param marketPhase       当前市场阶段
     * @return 统一经验提示文本
     */
    public String buildUnifiedExperienceHint(String stockCode,
                                               Map<String, Object> currentConditions,
                                               String marketPhase) {
        StringBuilder sb = new StringBuilder();

        // 1. 信号级经验
        String signalHint = getExperienceHint(stockCode, currentConditions);
        if (signalHint != null && !signalHint.isBlank()) {
            sb.append(signalHint);
        }

        // 2. 因子级经验
        if (factorEvolutionMemory != null) {
            List<FactorRecommendation> recommendations = recommendFactors(marketPhase, 3);
            if (!recommendations.isEmpty()) {
                sb.append("\n## 进化因子推荐\n");
                sb.append("当前市场阶段（").append(marketPhase).append("）下表现最优的进化因子：\n");
                for (FactorRecommendation rec : recommendations) {
                    sb.append(String.format("- %s: IC=%.4f Sharpe=%.2f — %s\n",
                            rec.factorName(), rec.ic(), rec.sharpeRatio(), rec.description()));
                }
            }
        }

        // 3. 因子库中的进化因子列表
        if (factorLibrary != null) {
            List<String> evolvedFactors = factorLibrary.listEvolvedFactors();
            if (!evolvedFactors.isEmpty()) {
                sb.append("\n## 可用的进化因子\n");
                sb.append(String.join(", ", evolvedFactors)).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 推荐当前市场条件下表现最优的进化因子
     */
    public List<FactorRecommendation> recommendFactors(String marketPhase, int limit) {
        List<FactorRecommendation> recommendations = new ArrayList<>();
        if (factorEvolutionMemory == null) return recommendations;

        var topRecords = factorEvolutionMemory.getTopPerformersByCondition(marketPhase, limit * 2);

        if (topRecords.size() < limit) {
            var globalTop = factorEvolutionMemory.getTopPerformers(limit);
            for (var record : globalTop) {
                if (topRecords.size() >= limit) break;
                if (topRecords.stream().noneMatch(r -> r.getFactorId().equals(record.getFactorId()))) {
                    topRecords.add(record);
                }
            }
        }

        for (var record : topRecords.stream().limit(limit).toList()) {
            var info = factorLibrary != null ? factorLibrary.getEvolvedFactorInfo(record.getFactorName()) : null;
            if (info != null) {
                recommendations.add(new FactorRecommendation(
                        record.getFactorId(),
                        record.getFactorName(),
                        record.getFactorExpression(),
                        record.getIc(),
                        record.getSharpeRatio(),
                        record.getEvaluationScore(),
                        record.getMarketCondition()
                ));
            }
        }

        return recommendations;
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

    /** 因子推荐项 */
    public record FactorRecommendation(
            String factorId,
            String factorName,
            String description,
            double ic,
            double sharpeRatio,
            double overallScore,
            String marketCondition
    ) {}
}
