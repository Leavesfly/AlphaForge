package io.leavesfly.alphaforge.application.agent.kernel;

/**
 * 评审/反思器 — 对内核产出的草稿结果做质量校验与修正。
 *
 * <p>后续将收敛 LlmAnalysisQualityAssessor（幻觉/逻辑校验）与 AgentDebateOrchestrator
 * 到此接口；阶段 1 提供 {@link PassThroughCritic} 直通实现。
 */
public interface Critic {

    AgentResult review(AgentContext context, AgentResult draft);
}
