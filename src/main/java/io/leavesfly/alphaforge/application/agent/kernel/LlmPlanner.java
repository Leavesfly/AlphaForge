package io.leavesfly.alphaforge.application.agent.kernel;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM 动态规划器 — 对开放式任务做动态步骤分解。
 *
 * <p>阶段 1 提供最小实现：返回单个 LLM 推理步骤占位（不实际调用 LLM 生成计划），
 * 作为 SOP 骨架缺失时的兜底。阶段 2+ 将接入真正的 LLM 任务分解。
 */
@Component
public class LlmPlanner implements Planner {

    @Override
    public AgentPlan plan(AgentTask task, AgentContext context) {
        PlanStep reasoning = PlanStep.of("dynamic", "dynamic_reasoning",
                PlanStep.Kind.LLM_REASONING, "LLM 动态推理完成任务: " + task.getGoal());
        return new AgentPlan(task.getType(), List.of(reasoning));
    }
}
