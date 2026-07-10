package io.leavesfly.alphaforge.application.agent.kernel;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 混合规划器（认知轨默认）— 组合 SOP 确定性骨架与 LLM 动态规划。
 *
 * <p>策略：优先采用 {@link SopPlanner} 的确定性骨架保证关键风控/合规步骤固定；
 * 当骨架为空（未定义 SOP 的任务）时回退到 {@link LlmPlanner} 动态分解。
 */
@Component
@Primary
public class HybridPlanner implements Planner {

    private final SopPlanner sopPlanner;
    private final LlmPlanner llmPlanner;

    public HybridPlanner(SopPlanner sopPlanner, LlmPlanner llmPlanner) {
        this.sopPlanner = sopPlanner;
        this.llmPlanner = llmPlanner;
    }

    @Override
    public AgentPlan plan(AgentTask task, AgentContext context) {
        AgentPlan sop = sopPlanner.plan(task, context);
        if (sop != null && !sop.isEmpty()) {
            return sop;
        }
        return llmPlanner.plan(task, context);
    }
}
