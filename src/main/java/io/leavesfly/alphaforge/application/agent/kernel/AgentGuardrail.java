package io.leavesfly.alphaforge.application.agent.kernel;

import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.application.autonomy.TradingRiskGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent 统一治理闸 — 任何状态变更步骤（下单/晋升/写回参数）执行前必经此处。
 *
 * <p>校验链：任务策略授权 → 自治总开关（默认关闭）→ 风控熔断状态。
 * 不放宽任何现有风控；与轨道（功能轨/认知轨）无关，仅与「是否状态变更」有关。
 */
@Component
public class AgentGuardrail {

    private static final Logger log = LoggerFactory.getLogger(AgentGuardrail.class);

    /** 可选依赖：自治策略门面 */
    private final AutonomyPolicy autonomyPolicy;

    /** 可选依赖：交易风控闸 */
    private final TradingRiskGuard riskGuard;

    @Autowired
    public AgentGuardrail(ObjectProvider<AutonomyPolicy> autonomyPolicy,
                          ObjectProvider<TradingRiskGuard> riskGuard) {
        this.autonomyPolicy = autonomyPolicy.getIfAvailable();
        this.riskGuard = riskGuard.getIfAvailable();
    }

    /** 测试用构造器：无自治策略/风控依赖（等价于两者均不可用） */
    public AgentGuardrail() {
        this.autonomyPolicy = null;
        this.riskGuard = null;
    }

    /**
     * 断言某步骤可执行；不通过抛 {@link AgentGuardrailException}。
     * 只读步骤直接放行；状态变更步骤逐项校验。
     */
    public void assertStepAllowed(AgentTask task, PlanStep step) {
        if (step == null || !step.isMutating()) {
            return;
        }

        if (task == null || !task.getPolicy().isAllowStateMutation()) {
            throw new AgentGuardrailException("任务策略未授权状态变更: "
                    + (step.getName() != null ? step.getName() : "unknown"));
        }

        if (autonomyPolicy != null && !autonomyPolicy.isEnabled()) {
            throw new AgentGuardrailException("自治总开关关闭，拒绝状态变更: " + step.getName());
        }

        if (riskGuard != null && riskGuard.isHalted()) {
            throw new AgentGuardrailException("交易已熔断，拒绝状态变更: " + riskGuard.getHaltReason());
        }

        log.debug("Guardrail 放行状态变更步骤: {}", step.getName());
    }

    /** 快速判断某任务当前是否允许执行其状态变更步骤（不抛异常）。 */
    public boolean isMutationAllowed(AgentTask task) {
        if (task == null || !task.getPolicy().isAllowStateMutation()) {
            return false;
        }
        if (autonomyPolicy != null && !autonomyPolicy.isEnabled()) {
            return false;
        }
        return riskGuard == null || !riskGuard.isHalted();
    }
}
