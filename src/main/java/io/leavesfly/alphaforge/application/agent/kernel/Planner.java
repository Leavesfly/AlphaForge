package io.leavesfly.alphaforge.application.agent.kernel;

/**
 * 规划器 — 将 AgentTask 分解为可执行的 {@link AgentPlan}。
 *
 * <p>混合编排：{@code SopPlanner} 提供确定性骨架（固定关键风控/合规步骤），
 * {@code LlmPlanner} 对开放步骤做动态分解，{@code HybridPlanner} 组合二者。
 */
public interface Planner {

    AgentPlan plan(AgentTask task, AgentContext context);
}
