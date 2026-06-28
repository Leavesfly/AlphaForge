package io.leavesfly.stock.application.strategy.engine;

import io.leavesfly.stock.application.strategy.StrategyCatalog;
import io.leavesfly.stock.application.strategy.model.ScoringProfile;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 策略复盘调度器 — 定期审查策略运行表现。
 *
 * 每日收盘后自动汇总各 scoring 策略的命中率与衰减状态，
 * 输出日志供运维监控。可结合 LLM 生成自然语言复盘报告。
 *
 * 调度时间：每个交易日 18:30（收盘后 30 分钟）。
 * 需在 Application 类上启用 @EnableScheduling。
 */
@Component
public class StrategyReviewScheduler {

    private static final Logger log = LoggerFactory.getLogger(StrategyReviewScheduler.class);

    private final StrategyCatalog catalog;
    private final StrategyPerformanceTracker performanceTracker;

    public StrategyReviewScheduler(StrategyCatalog catalog, StrategyPerformanceTracker performanceTracker) {
        this.catalog = catalog;
        this.performanceTracker = performanceTracker;
    }

    /**
     * 每日策略复盘 — 收盘后自动运行。
     * cron: 秒 分 时 日 月 周
     */
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

        log.info("=== 策略复盘完成: {} 过时, {} 低区分, {} 正常 ===",
                staleCount, lowDiscriminationCount, normalCount);
    }
}
