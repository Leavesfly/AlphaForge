package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.factor.evolution.FactorEvolutionOrchestrator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 因子进化状态查询工具（只读）
 *
 * 返回进化代数、已提升因子数、失败模式数与最优因子摘要。
 * 仅查询，不触发进化；进化的触发属状态变更，由内核 Guardrail 约束（后续阶段接入）。
 */
@Component
public class FactorEvolutionStatusTool implements Tool {

    private final FactorEvolutionOrchestrator evolutionOrchestrator;

    public FactorEvolutionStatusTool(FactorEvolutionOrchestrator evolutionOrchestrator) {
        this.evolutionOrchestrator = evolutionOrchestrator;
    }

    @Override
    public String name() {
        return "factor_evolution_status";
    }

    @Override
    public String description() {
        return "查询因子进化状态摘要（进化代数、已提升因子数、失败模式、最优因子）。只读，不触发进化。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        params.put("properties", new HashMap<>());
        params.put("required", new String[]{});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        try {
            String summary = evolutionOrchestrator.getEvolutionStatusSummary();
            return (summary == null || summary.isBlank()) ? "暂无因子进化记录" : summary;
        } catch (Exception e) {
            throw new ToolException("查询因子进化状态失败: " + e.getMessage(), e);
        }
    }
}
