package io.leavesfly.alphaforge.application.agent.kernel;

/**
 * 计划步骤 — SOP 骨架或 LLM 动态规划产出的最小执行单元。
 */
public class PlanStep {

    /** 步骤种类 */
    public enum Kind {
        /** 确定性步骤（本地代码，无 LLM） */
        DETERMINISTIC,
        /** LLM 推理步骤 */
        LLM_REASONING,
        /** 工具调用步骤 */
        TOOL,
        /** 子 Agent 步骤 */
        SUBAGENT
    }

    private final String id;
    private final String name;
    private final Kind kind;
    private final String description;
    private final boolean mutating;

    public PlanStep(String id, String name, Kind kind, String description, boolean mutating) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.description = description;
        this.mutating = mutating;
    }

    /** 只读步骤快捷构造 */
    public static PlanStep of(String id, String name, Kind kind, String description) {
        return new PlanStep(id, name, kind, description, false);
    }

    /** 状态变更步骤快捷构造（必经 Guardrail） */
    public static PlanStep mutating(String id, String name, Kind kind, String description) {
        return new PlanStep(id, name, kind, description, true);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    /** 是否为状态变更型步骤（下单/晋升/写回参数等）。 */
    public boolean isMutating() {
        return mutating;
    }

    @Override
    public String toString() {
        return kind + ":" + name + (mutating ? "(mutating)" : "");
    }
}
