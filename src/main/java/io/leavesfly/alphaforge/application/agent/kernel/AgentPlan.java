package io.leavesfly.alphaforge.application.agent.kernel;

import java.util.Collections;
import java.util.List;

/**
 * Agent 执行计划 — 某个任务类型的有序步骤集合（SOP 骨架 + 动态扩展）。
 */
public class AgentPlan {

    private final AgentTaskType taskType;
    private final List<PlanStep> steps;

    public AgentPlan(AgentTaskType taskType, List<PlanStep> steps) {
        this.taskType = taskType;
        this.steps = steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
    }

    public AgentTaskType getTaskType() {
        return taskType;
    }

    public List<PlanStep> getSteps() {
        return steps;
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** 计划中是否包含状态变更步骤（用于 Guardrail 预检）。 */
    public boolean hasMutatingStep() {
        return steps.stream().anyMatch(PlanStep::isMutating);
    }
}
