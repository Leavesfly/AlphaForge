package io.leavesfly.alphaforge.application.service.signal;

import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignal;
import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignalOutcome;
import io.leavesfly.alphaforge.application.service.feedback.SignalLearningService;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.repository.signal.DecisionSignalOutcomeRepository;
import io.leavesfly.alphaforge.domain.repository.signal.DecisionSignalRepository;
import io.leavesfly.alphaforge.application.strategy.engine.StrategyPerformanceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 信号结果自动评估器（复利记忆层 Compounding Memory）
 *
 * 实现论文中"Loop"的自我改进核心：
 * 每轮分析结束后，自动回溯历史信号，对比实际市场表现，
 * 将评估结果写入 decision_signal_outcomes，构成系统的"经验积累"。
 *
 * 评估窗口：
 * - short:  5 个交易日后
 * - medium: 10 个交易日后
 * - long:   20 个交易日后
 *
 * 评估结果：
 * - correct:   信号方向与实际走势一致，且收益达标
 * - incorrect: 信号方向与实际走势相反
 * - partial:   方向正确但收益不足目标
 * - expired:   超过有效期，无法有效评估
 */
@Service
public class SignalOutcomeEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SignalOutcomeEvaluator.class);

    private static final String ENGINE_VERSION = "v1.0";

    /** 短期 / 中期 / 长期 评估天数 */
    private static final Map<String, Integer> HORIZON_DAYS = Map.of(
            "short", 5,
            "medium", 10,
            "long", 20
    );

    /** 视为"正确"的最低收益率（买入信号需涨超此值，卖出信号需跌超此值）*/
    private static final double MIN_CORRECT_RETURN_PCT = 1.5;

    private final DecisionSignalRepository signalRepository;
    private final DecisionSignalOutcomeRepository outcomeRepository;
    private final MarketDataPort marketDataPort;
    private final StrategyPerformanceTracker performanceTracker;
    private final SignalLearningService signalLearningService;

    public SignalOutcomeEvaluator(DecisionSignalRepository signalRepository,
                                  DecisionSignalOutcomeRepository outcomeRepository,
                                  MarketDataPort marketDataPort,
                                  StrategyPerformanceTracker performanceTracker,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false) SignalLearningService signalLearningService) {
        this.signalRepository = signalRepository;
        this.outcomeRepository = outcomeRepository;
        this.marketDataPort = marketDataPort;
        this.performanceTracker = performanceTracker;
        this.signalLearningService = signalLearningService;
    }

    /**
     * 批量评估到期信号（由 Scheduler 每日盘后调用）
     *
     * @return 评估摘要
     */
    public EvaluationSummary evaluatePendingSignals() {
        EvaluationSummary summary = new EvaluationSummary();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(60); // 最多评估60天内的信号
        List<DecisionSignal> activeSignals = signalRepository.findByStatusOrderByCreatedAtDesc("active");

        log.info("===== 信号结果评估开始 | 活跃信号: {} 条 =====", activeSignals.size());

        for (DecisionSignal signal : activeSignals) {
            if (signal.getCreatedAt() == null || signal.getCreatedAt().isBefore(cutoff)) {
                continue;
            }
            evaluateSignalAllHorizons(signal, summary);
        }

        log.info("===== 信号结果评估完成 | 评估:{} 跳过:{} 错误:{} =====",
                summary.evaluated, summary.skipped, summary.errors);
        return summary;
    }

    /**
     * 评估单条信号的所有时间窗口
     */
    private void evaluateSignalAllHorizons(DecisionSignal signal, EvaluationSummary summary) {
        LocalDate signalDate = signal.getCreatedAt().toLocalDate();

        for (Map.Entry<String, Integer> entry : HORIZON_DAYS.entrySet()) {
            String horizon = entry.getKey();
            int days = entry.getValue();

            // 检查是否已评估
            List<DecisionSignalOutcome> existing = outcomeRepository.findBySignalId(signal.getId());
            boolean alreadyEvaluated = existing.stream()
                    .anyMatch(o -> horizon.equals(o.getHorizon()) && "evaluated".equals(o.getEvalStatus()));
            if (alreadyEvaluated) {
                continue;
            }

            // 判断评估窗口是否到期
            LocalDate evalDate = signalDate.plusDays(days);
            if (evalDate.isAfter(LocalDate.now())) {
                // 还未到评估时间
                continue;
            }

            try {
                DecisionSignalOutcome outcome = doEvaluate(signal, horizon, evalDate);
                outcomeRepository.save(outcome);
                summary.evaluated++;
                log.info("信号[{}] {} {} 评估完成: {} 收益:{}%",
                        signal.getId(), signal.getStockCode(), horizon,
                        outcome.getOutcome(),
                        outcome.getReturnPct() != null ? String.format("%.2f", outcome.getReturnPct()) : "0.00");
            } catch (Exception e) {
                summary.errors++;
                log.warn("信号[{}] {} 评估失败: {}", signal.getId(), signal.getStockCode(), e.getMessage());
                // 记录为 unable
                saveUnableOutcome(signal, horizon, e.getMessage());
            }
        }
    }

    /**
     * 执行单条信号的单窗口评估
     */
    private DecisionSignalOutcome doEvaluate(DecisionSignal signal, String horizon, LocalDate evalDate) {
        DecisionSignalOutcome outcome = new DecisionSignalOutcome();
        outcome.setSignalId(signal.getId());
        outcome.setHorizon(horizon);
        outcome.setEngineVersion(ENGINE_VERSION);
        outcome.setEvaluatedAt(LocalDateTime.now());

        // 获取信号发出时的价格（入场价）
        Double entryPrice = resolveEntryPrice(signal);
        if (entryPrice == null || entryPrice <= 0) {
            outcome.setEvalStatus("unable");
            outcome.setUnableReason("无法确定入场价");
            return outcome;
        }
        outcome.setEntryPrice(entryPrice);

        // 获取评估窗口结束时的价格（出场价）
        Double exitPrice = resolveExitPrice(signal.getStockCode(), evalDate);
        if (exitPrice == null || exitPrice <= 0) {
            outcome.setEvalStatus("unable");
            outcome.setUnableReason("无法获取评估日价格");
            return outcome;
        }
        outcome.setExitPrice(exitPrice);

        // 计算收益率
        double returnPct = (exitPrice - entryPrice) / entryPrice * 100.0;
        outcome.setReturnPct(returnPct);

        // 实际走势方向
        String actualMovement = returnPct > 0.5 ? "up" : returnPct < -0.5 ? "down" : "flat";
        outcome.setActualMovement(actualMovement);

        // 预期方向（根据信号动作）
        String action = signal.getAction();
        String directionExpected = "buy".equals(action) || "strong_buy".equals(action) ? "up" : "down";
        outcome.setDirectionExpected(directionExpected);

        // 判断结果
        String outcomeStr = judgeOutcome(action, returnPct, actualMovement);
        outcome.setOutcome(outcomeStr);
        outcome.setEvalStatus("evaluated");

        // 回调策略性能追踪器，驱动权重自动调优闭环
        boolean wasCorrect = "correct".equals(outcomeStr) || "partial".equals(outcomeStr);
        String agentId = signal.getSourceAgent() != null ? signal.getSourceAgent() : "pipeline";
        performanceTracker.recordOutcome(agentId, wasCorrect);

        // 更新跨轮次经验记忆（闭环：信号效果→经验更新→下次分析参考）
        if (signalLearningService != null) {
            try {
                signalLearningService.updateOutcome(
                        signal.getStockCode(),
                        signal.getCreatedAt().toLocalDate().toString(),
                        outcomeStr,
                        returnPct);
            } catch (Exception e) {
                log.debug("更新经验记忆失败: {}", e.getMessage());
            }
        }

        return outcome;
    }

    /**
     * 判断信号结果
     */
    private String judgeOutcome(String action, double returnPct, String actualMovement) {
        boolean isBuy = "buy".equals(action) || "strong_buy".equals(action);
        boolean isSell = "sell".equals(action) || "strong_sell".equals(action);

        if (isBuy) {
            if (returnPct >= MIN_CORRECT_RETURN_PCT) return "correct";
            if (returnPct >= 0) return "partial";
            return "incorrect";
        } else if (isSell) {
            if (returnPct <= -MIN_CORRECT_RETURN_PCT) return "correct";
            if (returnPct <= 0) return "partial";
            return "incorrect";
        }
        return "partial"; // hold/neutral 信号
    }

    /**
     * 解析入场价（优先使用信号的 entryLow，其次实时报价）
     */
    private Double resolveEntryPrice(DecisionSignal signal) {
        if (signal.getEntryLow() != null && signal.getEntryLow() > 0) {
            return signal.getEntryLow();
        }
        if (signal.getEntryHigh() != null && signal.getEntryHigh() > 0) {
            return signal.getEntryHigh();
        }
        // 尝试从历史数据获取信号日收盘价
        try {
            LocalDate signalDate = signal.getCreatedAt().toLocalDate();
            var history = marketDataPort.getHistoryData(signal.getStockCode(), signalDate.minusDays(1), signalDate.plusDays(1));
            for (var bar : history) {
                if (signalDate.equals(bar.getTradeDate()) && bar.getClosePrice() != null) {
                    return bar.getClosePrice();
                }
            }
        } catch (Exception e) {
            log.debug("获取信号日历史价格失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取评估日收盘价
     */
    private Double resolveExitPrice(String stockCode, LocalDate evalDate) {
        try {
            // 尝试获取评估日附近的价格（前后各1天以应对非交易日）
            var history = marketDataPort.getHistoryData(stockCode,
                    evalDate.minusDays(3), evalDate.plusDays(1));
            // 找最接近 evalDate 的交易日
            return history.stream()
                    .filter(d -> d.getTradeDate() != null && !d.getTradeDate().isAfter(evalDate))
                    .max(Comparator.comparing(d -> d.getTradeDate()))
                    .map(d -> d.getClosePrice())
                    .orElse(null);
        } catch (Exception e) {
            log.debug("获取评估日价格失败: {} {}", stockCode, e.getMessage());
            return null;
        }
    }

    private void saveUnableOutcome(DecisionSignal signal, String horizon, String reason) {
        try {
            DecisionSignalOutcome outcome = new DecisionSignalOutcome();
            outcome.setSignalId(signal.getId());
            outcome.setHorizon(horizon);
            outcome.setEngineVersion(ENGINE_VERSION);
            outcome.setEvalStatus("unable");
            outcome.setUnableReason(reason != null && reason.length() > 200 ? reason.substring(0, 200) : reason);
            outcome.setEvaluatedAt(LocalDateTime.now());
            outcomeRepository.save(outcome);
        } catch (Exception e) {
            log.debug("保存unable评估记录失败: {}", e.getMessage());
        }
    }

    /**
     * 获取信号准确率统计
     */
    public AccuracyStats getAccuracyStats() {
        try {
            List<Map<String, Object>> stats = outcomeRepository.getOutcomeStats();
            AccuracyStats result = new AccuracyStats();
            for (Map<String, Object> row : stats) {
                String outcomeVal = String.valueOf(row.getOrDefault("outcome", ""));
                int cnt = ((Number) row.getOrDefault("count", 0)).intValue();
                switch (outcomeVal) {
                    case "correct" -> result.correct += cnt;
                    case "incorrect" -> result.incorrect += cnt;
                    case "partial" -> result.partial += cnt;
                }
            }
            result.total = result.correct + result.incorrect + result.partial;
            result.accuracyPct = result.total > 0
                    ? (double) (result.correct + result.partial) / result.total * 100.0 : 0.0;
            return result;
        } catch (Exception e) {
            log.debug("获取准确率统计失败: {}", e.getMessage());
            return new AccuracyStats();
        }
    }

    /**
     * 刷新准确率并同步到策略权重追踪器（原 LoopStateManager.refreshAccuracy 逻辑）
     *
     * 在信号评估完成后调用，驱动自动调优闭环。
     */
    public void refreshAndSyncAccuracy() {
        try {
            AccuracyStats stats = getAccuracyStats();
            performanceTracker.updateGlobalAccuracy(stats.accuracyPct);
            log.info("信号准确率刷新: {}% (correct:{} incorrect:{} partial:{})",
                    String.format("%.1f", stats.accuracyPct), stats.correct, stats.incorrect, stats.partial);
        } catch (Exception e) {
            log.debug("刷新准确率失败: {}", e.getMessage());
        }
    }

    // ===== 数据类 =====

    /** 批量评估摘要 */
    public static class EvaluationSummary {
        public int evaluated;
        public int skipped;
        public int errors;

        @Override
        public String toString() {
            return String.format("EvaluationSummary{evaluated=%d, skipped=%d, errors=%d}",
                    evaluated, skipped, errors);
        }
    }

    /** 准确率统计 */
    public static class AccuracyStats {
        public int correct;
        public int incorrect;
        public int partial;
        public int total;
        public double accuracyPct;
    }
}
