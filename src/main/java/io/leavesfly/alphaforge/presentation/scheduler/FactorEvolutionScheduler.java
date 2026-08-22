package io.leavesfly.alphaforge.presentation.scheduler;

import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.application.factor.evolution.FactorEvolutionConfig;
import io.leavesfly.alphaforge.application.factor.evolution.FactorEvolutionOrchestrator;
import io.leavesfly.alphaforge.application.factor.evolution.model.EvolutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 因子进化周调度 — 默认每周一 20:00，受 autonomy.enabled 控制。
 */
@Component
public class FactorEvolutionScheduler {

    private static final Logger log = LoggerFactory.getLogger(FactorEvolutionScheduler.class);

    private final AutonomyPolicy autonomyPolicy;
    private final FactorEvolutionOrchestrator evolutionOrchestrator;

    public FactorEvolutionScheduler(AutonomyPolicy autonomyPolicy,
                                    FactorEvolutionOrchestrator evolutionOrchestrator) {
        this.autonomyPolicy = autonomyPolicy;
        this.evolutionOrchestrator = evolutionOrchestrator;
    }

    @Scheduled(cron = "${AUTONOMY_FACTOR_EVOLUTION_CRON:0 0 20 * * MON}")
    public void weeklyEvolution() {
        if (!autonomyPolicy.isEnabled()) {
            return;
        }
        log.info("========== [因子进化周调度] 开始 ==========");
        try {
            EvolutionResult result = evolutionOrchestrator.runEvolutionCycle(
                    FactorEvolutionConfig.defaultConfig());
            autonomyPolicy.audit("factor_evolution", "factor", "generation-" + result.getGeneration(),
                    "scheduled", "done",
                    String.format("generated=%d passed=%d promoted=%d",
                            result.getCandidatesGenerated(),
                            result.getCandidatesPassed(),
                            result.getCandidatesPromoted()));
            log.info("[因子进化周调度] 完成: gen={} promoted={}",
                    result.getGeneration(), result.getCandidatesPromoted());
        } catch (Exception e) {
            log.error("[因子进化周调度] 失败（不中断主流程）: {}", e.getMessage(), e);
            autonomyPolicy.audit("factor_evolution_fail", "factor", "weekly",
                    "scheduled", "error", e.getMessage());
        }
    }
}
