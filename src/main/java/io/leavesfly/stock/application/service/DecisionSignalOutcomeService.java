package io.leavesfly.stock.application.service;

import io.leavesfly.stock.domain.model.entity.DecisionSignal;
import io.leavesfly.stock.infrastructure.persistence.DecisionSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 决策信号复盘 + 汇总服务
 * 对应Python: decision_signal_outcome_service.py + decision_signal_summary.py
 */
@Service
public class DecisionSignalOutcomeService {

    private static final Logger log = LoggerFactory.getLogger(DecisionSignalOutcomeService.class);
    private final DecisionSignalRepository signalRepo;

    public DecisionSignalOutcomeService(DecisionSignalRepository signalRepo) {
        this.signalRepo = signalRepo;
    }

    /** 复盘: 计算信号准确率 */
    public Map<String, Object> calculateAccuracy(String stockCode, int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<DecisionSignal> signals = signalRepo.findByStockCodeAndCreatedAtAfter(stockCode, since);
        if (signals.isEmpty()) {
            result.put("total", 0);
            result.put("accuracy", 0.0);
            return result;
        }
        int correct = 0;
        for (DecisionSignal s : signals) {
            if ("active".equals(s.getStatus())) correct++;
        }
        result.put("total", signals.size());
        result.put("correct", correct);
        result.put("accuracy", signals.size() > 0 ? (double) correct / signals.size() * 100 : 0);
        result.put("stock_code", stockCode);
        return result;
    }

    /** 汇总: 最近N天所有信号统计 */
    public Map<String, Object> summarize(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<DecisionSignal> all = signalRepo.findByCreatedAtAfter(since);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("period_days", days);
        summary.put("total_signals", all.size());

        Map<String, Long> byType = all.stream()
                .collect(Collectors.groupingBy(s -> s.getSignalType() != null ? s.getSignalType() : "unknown", Collectors.counting()));
        summary.put("by_type", byType);

        Map<String, Long> byStock = all.stream()
                .collect(Collectors.groupingBy(s -> s.getStockCode() != null ? s.getStockCode() : "unknown", Collectors.counting()));
        summary.put("top_stocks", byStock.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)));
        return summary;
    }

    /** 运行信号结果评估 */
    public List<Map<String, Object>> runEvaluation(Map<String, Object> request) {
        return List.of();
    }

    /** 获取结果列表 */
    public List<Map<String, Object>> listOutcomes(Long signalId, int page, int pageSize) {
        return List.of();
    }

    /** 获取统计数据 */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<DecisionSignal> all = signalRepo.findTop20ByOrderByCreatedAtDesc();
        stats.put("total", all.size());
        stats.put("win_count", 0);
        stats.put("loss_count", 0);
        stats.put("win_rate", null);
        return stats;
    }

    /** 获取信号反馈 */
    public Map<String, Object> getFeedback(Long signalId) {
        return Map.of("signal_id", signalId, "feedback_value", "", "note", "");
    }

    /** 保存信号反馈 */
    public Map<String, Object> saveFeedback(Long signalId, Map<String, Object> request) {
        Map<String, Object> result = new LinkedHashMap<>(request);
        result.put("signal_id", signalId);
        return result;
    }
}
