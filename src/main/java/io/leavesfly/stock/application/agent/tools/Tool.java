package io.leavesfly.stock.application.agent.tools;

import java.util.Map;

/**
 * 工具接口 - Agent可调用的原子能力抽象
 *
 * 参考TinyClaw的Tool设计，保持轻量级：
 * - name(): 工具唯一标识
 * - description(): 供LLM理解的工具说明
 * - parameters(): JSON Schema格式的参数定义
 * - execute(): 执行工具并返回字符串结果
 */
public interface Tool {

    /** 工具名称（全局唯一，用于LLM工具调用标识） */
    String name();

    /** 工具描述（供LLM理解工具用途） */
    String description();

    /** 参数定义（JSON Schema格式，用于OpenAI Function Calling） */
    Map<String, Object> parameters();

    /**
     * 执行工具
     *
     * @param args 工具参数
     * @return 执行结果（字符串格式）
     * @throws ToolException 工具执行失败时抛出
     */
    String execute(Map<String, Object> args) throws ToolException;
}
