package io.leavesfly.alphaforge.application.strategy.engine;

import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.application.service.feedback.StrategyParameterTuner;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.lifecycle.StrategyLifecycleService;
import io.leavesfly.alphaforge.application.strategy.lifecycle.StrategyLifecycleState;
import io.leavesfly.alphaforge.application.strategy.model.ScoringProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.strategy.CustomStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 策略复盘调度器 — 定期审查策略运行表现，并在 L4 下自动降级/审计调优建议。
 */
@Component
public class StrategyReviewScheduler {

    private static final Logger log = LoggerFactory.getLogger(StrategyReviewScheduler.class);

    private final StrategyCatalog catalog;
    private final StrategyPerformanceTracker performanceTracker;
    private final StrategyParameterTuner parameterTuner;

    private final AutonomyPolicy autonomyPolicy;

    private final StrategyLifecycleService lifecycleService;

    public StrategyReviewScheduler(StrategyCatalog catalog, StrategyPerformanceTracker performanceTracker,
                                    StrategyParameterTuner parameterTuner,
                                    ObjectProvider<AutonomyPolicy> autonomyPolicy,
                                    ObjectProvider<StrategyLifecycleService> lifecycleService) {
        this.catalog = catalog;
        this.performanceTracker = performanceTracker;
        this.parameterTuner = parameterTuner;
        this.autonomyPolicy = autonomyPolicy.getIfAvailable();
        this.lifecycleService = lifecycleService.getIfAvailable();
    }

    @Scheduled(cron = "${strategy.review.cron:0 30 18 * * MON-FRI}")
    public void dailyReview() {
        List<StrategyDefinition> strategies = catalog.listByCapability("scoring");
        if (strategies.isEmpty()) {
            return;
        }

        log.info("=== 策略每日复盘开始（{} 个 scoring 策略）===", strategies.size());

        int staleCount = 0;
        int lowDiscriminationCount = 0;
        int normalCount = 0;
        int demoted = 0;

        for (StrategyDefinition s : strategies) {
            ScoringProfile profile = s.getScoring();
            if (profile == null || profile.getScoreWeight() <= 0) continue;

            double matchRate = performanceTracker.getMatchRate(s.getId());
            int effectiveWeight = performanceTracker.getEffectiveWeight(
                    s.getId(), profile.getScoreWeight(), profile.isAutoDecay(), profile.getMinWeight());

            if (matchRate < 0) {
                log.debug("策略 {} 无评估数据", s.getId());
                continue;
            }

            String status;
            if (matchRate < 0.1) {
                status = "⚠过时";
                staleCount++;
                if (tryAutoDemote(s.getId(), matchRate)) {
                    demoted++;
                }
            } else if (matchRate > 0.9) {
                status = "⚠低区分";
                lowDiscriminationCount++;
            } else if (matchRate >= 0.3 && matchRate <= 0.7) {
                status = "✓正常";
                normalCount++;
            } else {
                status = "观察中";
                normalCount++;
            }

            log.info("策略 {} 原权重={} 有效权重={} 命中率={} 状态={}",
                    s.getId(), profile.getScoreWeight(), effectiveWeight,
                    String.format("%.1f%%", matchRate * 100), status);
        }

        log.info("=== 策略复盘完成: {} 过时, {} 低区分, {} 正常, {} 自动降级 ===",
                staleCount, lowDiscriminationCount, normalCount, demoted);

        if (parameterTuner != null) {
            List<String> strategyIds = strategies.stream()
                    .map(StrategyDefinition::getId).toList();
            var tuningResult = parameterTuner.batchSuggestTuning(strategyIds);
            if (!tuningResult.isEmpty()) {
                log.info("=== 策略参数调优建议: {} 个策略有建议 ===", tuningResult.size());
                tuningResult.forEach((id, suggestions) -> {
                    suggestions.forEach(s -> {
                        log.info("  策略 {}: {}", id, s.description());
                        if (autonomyPolicy != null && autonomyPolicy.canAutoApplyParams()) {
                            // TuningSuggestion 多为方向性建议，无可映射数值参数时仅审计
                            autonomyPolicy.audit("tuning_suggestion", "strategy", id,
                                    "review", "advisory",
                                    s.action() + ": " + s.description());
                        }
                    });
                });
            }
        }
    }

    private boolean tryAutoDemote(String strategyId, double matchRate) {
        if (autonomyPolicy == null || !autonomyPolicy.canAutoDemote() || lifecycleService == null) {
            return false;
        }
        CustomStrategy custom = lifecycleService.findById(strategyId);
        if (custom == null) {
            return false;
        }
        if (!StrategyLifecycleState.PUBLISHED.name().equals(custom.getLifecycleState())) {
            return false;
        }
        try {
            lifecycleService.transition(strategyId, StrategyLifecycleState.TESTING);
            autonomyPolicy.audit("auto_demote", "strategy", strategyId,
                    "PUBLISHED", "TESTING",
                    String.format("matchRate=%.1f%% < 10%%", matchRate * 100));
            return true;
        } catch (Exception e) {
            log.warn("自动降级失败 {}: {}", strategyId, e.getMessage());
            return false;
        }
    }
}
