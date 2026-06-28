package io.leavesfly.stock.application.service.signal;

import io.leavesfly.stock.domain.model.entity.signal.DecisionSignal;
import io.leavesfly.stock.domain.model.entity.signal.DecisionSignalOutcome;
import io.leavesfly.stock.domain.model.entity.signal.DecisionSignalFeedback;
import io.leavesfly.stock.infrastructure.persistence.signal.DecisionSignalRepository;
import io.leavesfly.stock.infrastructure.persistence.signal.DecisionSignalOutcomeRepository;
import io.leavesfly.stock.infrastructure.persistence.signal.DecisionSignalFeedbackRepository;
import io.leavesfly.stock.application.strategy.engine.StrategyPerformanceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 决策信号结果评估 + 反馈服务
 * 对齐 Python: decision_signal_outcome_service.py
 * 使用真实数据库持久化替代内存模拟
 */
@Service
public class DecisionSignalOutcomeService {

    private static final Logger log = LoggerFactory.getLogger(DecisionSignalOutcomeService.class);
    private final DecisionSignalRepository signalRepo;
    private final DecisionSignalOutcomeRepository outcomeRepo;
    private final DecisionSignalFeedbackRepository feedbackRepo;
    private final StrategyPerformanceTracker performanceTracker;

    public DecisionSignalOutcomeService(DecisionSignalRepository signalRepo,
                                         DecisionSignalOutcomeRepository outcomeRepo,
                                         DecisionSignalFeedbackRepository feedbackRepo,
                                         StrategyPerformanceTracker performanceTracker) {
        this.signalRepo = signalRepo;
        this.outcomeRepo = outcomeRepo;
        this.feedbackRepo = feedbackRepo;
        this.performanceTracker = performanceTracker;
    }

    /** 运行信号结果评估（从 DB 读取真实评估结果，真实评估由 Scheduler 每日执行） */
    public List<Map<String, Object>> runEvaluation(Map<String, Object> request) {
        List<Map<String, Object>> results = new ArrayList<>();
        Long signalId = request != null && request.get("signal_id") != null ? ((Number) request.get("signal_id")).longValue() : null;
        int limit = request != null && request.get("limit") != null ? ((Number) request.get("limit")).intValue() : 50;

        List<DecisionSignal> signals;
        if (signalId != null) {
            DecisionSignal s = signalRepo.findById(signalId);
            signals = s != null ? List.of(s) : List.of();
        } else {
            signals = signalRepo.findTop20ByOrderByCreatedAtDesc();
            if (signals.size() > limit) signals = signals.subList(0, limit);
        }

        for (DecisionSignal signal : signals) {
            // 优先使用已有的真实评估结果（由 SignalOutcomeEvaluator 写入）
            List<DecisionSignalOutcome> existing = outcomeRepo.findBySignalId(signal.getId());
            if (!existing.isEmpty()) {
                DecisionSignalOutcome latest = existing.get(0);
                results.add(Map.of(
                    "signal_id", signal.getId(),
                    "stock_code", signal.getStockCode() != null ? signal.getStockCode() : "",
                    "outcome", latest.getOutcome() != null ? latest.getOutcome() : "pending",
                    "return_pct", latest.getReturnPct() != null ? latest.getReturnPct() : 0.0,
                    "eval_status", latest.getEvalStatus() != null ? latest.getEvalStatus() : "pending"
                ));
            } else {
                // 尚未评估，返回 pending 状态（真实评估由每日 Scheduler 完成）
                results.add(Map.of(
                    "signal_id", signal.getId(),
                    "stock_code", signal.getStockCode() != null ? signal.getStockCode() : "",
                    "outcome", "pending",
                    "return_pct", 0.0,
                    "eval_status", "pending"
                ));
            }
        }
        return results;
    }

    /** 获取结果列表(分页) */
    public List<Map<String, Object>> listOutcomes(Long signalId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<DecisionSignalOutcome> items = outcomeRepo.findAll(signalId, null, null, null, null, pageSize, offset);
        return items.stream().map(this::outcomeToMap).collect(Collectors.toList());
    }

    /** 获取统计数据 */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Map<String, Object>> rawStats = outcomeRepo.getOutcomeStats();
        int total = 0, winCount = 0, lossCount = 0;
        for (Map<String, Object> row : rawStats) {
            int count = ((Number) row.getOrDefault("count", 0)).intValue();
            total += count;
            String outcome = (String) row.get("outcome");
            if ("win".equals(outcome)) winCount = count;
            else if ("loss".equals(outcome)) lossCount = count;
        }
        stats.put("total", total);
        stats.put("win_count", winCount);
        stats.put("loss_count", lossCount);
        stats.put("win_rate", total > 0 ? (double) winCount / total : null);
        stats.put("breakdowns", Map.of());
        stats.put("unable_reasons", Map.of());
        return stats;
    }

    /** 获取信号反馈 */
    public Map<String, Object> getFeedback(Long signalId) {
        DecisionSignalFeedback fb = feedbackRepo.findBySignalId(signalId);
        if (fb == null) {
            return Map.of("signal_id", signalId, "feedback_value", "", "reason_code", "", "note", "", "source", "");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signal_id", fb.getSignalId());
        result.put("feedback_value", fb.getFeedbackValue() != null ? fb.getFeedbackValue() : "");
        result.put("reason_code", fb.getReasonCode() != null ? fb.getReasonCode() : "");
        result.put("note", fb.getNote() != null ? fb.getNote() : "");
        result.put("source", fb.getSource() != null ? fb.getSource() : "");
        result.put("created_at", fb.getCreatedAt());
        result.put("updated_at", fb.getUpdatedAt());
        return result;
    }

    /** 保存信号反馈并驱动策略权重调优 */
    public Map<String, Object> saveFeedback(Long signalId, Map<String, Object> request) {
        DecisionSignalFeedback fb = new DecisionSignalFeedback();
        fb.setSignalId(signalId);
        fb.setFeedbackValue((String) request.get("feedback_value"));
        fb.setReasonCode((String) request.get("reason_code"));
        fb.setNote((String) request.get("note"));
        fb.setSource((String) request.getOrDefault("source", "web"));
        feedbackRepo.saveOrUpdate(fb);
        // 人工反馈驱动策略权重调优（接通为封闭的反馈闭环）
        String feedbackValue = fb.getFeedbackValue();
        if (feedbackValue != null && !feedbackValue.isEmpty()) {
            boolean wasCorrect = "useful".equals(feedbackValue);
            DecisionSignal signal = signalRepo.findById(signalId);
            if (signal != null) {
                String agentId = signal.getSourceAgent() != null ? signal.getSourceAgent() : "pipeline";
                performanceTracker.recordOutcome(agentId, wasCorrect);
                log.info("信号[{}] 人工反馈: {} → 策略记录[{}] {}",
                        signalId, feedbackValue, agentId, wasCorrect ? "正确" : "错误");
            }
        }
        return getFeedback(signalId);
    }

    private Map<String, Object> outcomeToMap(DecisionSignalOutcome o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("signal_id", o.getSignalId());
        m.put("horizon", o.getHorizon());
        m.put("engine_version", o.getEngineVersion());
        m.put("eval_status", o.getEvalStatus());
        m.put("outcome", o.getOutcome());
        m.put("return_pct", o.getReturnPct());
        m.put("entry_price", o.getEntryPrice());
        m.put("exit_price", o.getExitPrice());
        m.put("actual_movement", o.getActualMovement());
        m.put("direction_expected", o.getDirectionExpected());
        m.put("unable_reason", o.getUnableReason());
        m.put("evaluated_at", o.getEvaluatedAt());
        m.put("created_at", o.getCreatedAt());
        return m;
    }
}
