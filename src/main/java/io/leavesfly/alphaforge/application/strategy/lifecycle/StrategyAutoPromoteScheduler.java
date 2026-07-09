package io.leavesfly.alphaforge.application.strategy.lifecycle;

import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.domain.model.entity.strategy.CustomStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自动晋升调度 — 对 TESTING 策略跑质量门，达标则 PUBLISHED。
 * 受 autonomy.auto-promote 控制，默认关闭。
 */
@Component
public class StrategyAutoPromoteScheduler {

    private static final Logger log = LoggerFactory.getLogger(StrategyAutoPromoteScheduler.class);

    private final AutonomyPolicy policy;
    private final StrategyLifecycleService lifecycleService;
    private final StrategyPromotionGate promotionGate;

    public StrategyAutoPromoteScheduler(AutonomyPolicy policy,
                                        StrategyLifecycleService lifecycleService,
                                        StrategyPromotionGate promotionGate) {
        this.policy = policy;
        this.lifecycleService = lifecycleService;
        this.promotionGate = promotionGate;
    }

    @Scheduled(cron = "${autonomy.auto-promote.cron:0 0 21 * * MON-FRI}")
    public void autoPromote() {
        if (!policy.canAutoPromote()) {
            return;
        }
        List<CustomStrategy> testing = lifecycleService.findByState(StrategyLifecycleState.TESTING);
        if (testing.isEmpty()) {
            return;
        }
        log.info("=== 自动晋升扫描: {} 个 TESTING 策略 ===", testing.size());
        int promoted = 0;
        for (CustomStrategy s : testing) {
            try {
                PromotionDecision decision = promotionGate.evaluateCustom(s, null, 365);
                promotionGate.cacheDecision(decision);
                if (decision.isPromotable()) {
                    lifecycleService.transition(s.getStrategyId(), StrategyLifecycleState.PUBLISHED);
                    promoted++;
                } else {
                    log.info("策略 {} 未达晋升门槛: {}", s.getStrategyId(), decision.getReason());
                }
            } catch (Exception e) {
                log.warn("自动晋升失败: id={}, err={}", s.getStrategyId(), e.getMessage());
            }
        }
        log.info("=== 自动晋升完成: {}/{} ===", promoted, testing.size());
    }
}
