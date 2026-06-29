package io.leavesfly.alphaforge.application.agent.reasoning;

/**
 * 推理步骤 — 结构化推理链中的一步
 */
public class ReasoningStep {

    /** 步骤编号和名称（如 "1. 观察"） */
    private final String name;

    /** 步骤目标（简述这一步要做什么） */
    private final String goal;

    /** 步骤指令（详细的执行指导） */
    private final String instruction;

    public ReasoningStep(String name, String goal, String instruction) {
        this.name = name;
        this.goal = goal;
        this.instruction = instruction;
    }

    public String getName() { return name; }
    public String getGoal() { return goal; }
    public String getInstruction() { return instruction; }

    @Override
    public String toString() {
        return name + " — " + goal + ": " + instruction;
    }
}
