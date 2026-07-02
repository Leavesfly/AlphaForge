package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.strategy.generator.StrategyGenerationContext;
import io.leavesfly.alphaforge.application.strategy.generator.StrategyGenerationResult;
import io.leavesfly.alphaforge.application.strategy.generator.StrategyGeneratorAgent;
import io.leavesfly.alphaforge.application.strategy.lifecycle.StrategyLifecycleService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略生成工具 — 供 ReActAgent 通过 Function Calling 自主生成量化策略
 *
 * Agent 可在对话中接收用户的自然语言策略描述，
 * 调用此工具自动生成策略 YAML 并保存为草稿。
 */
@Component
public class GenerateStrategyTool implements Tool {

    private final StrategyGeneratorAgent generator;
    private final StrategyLifecycleService lifecycleService;

    public GenerateStrategyTool(StrategyGeneratorAgent generator,
                                StrategyLifecycleService lifecycleService) {
        this.generator = generator;
        this.lifecycleService = lifecycleService;
    }

    @Override
    public String name() {
        return "generate_strategy";
    }

    @Override
    public String description() {
        return "根据自然语言描述自动生成量化策略 YAML，校验后保存为草稿策略。支持指定分类、市场环境和参考策略";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> description = new HashMap<>();
        description.put("type", "string");
        description.put("description", "用户的自然语言策略描述，如：均线金叉策略，5日均线上穿20日均线买入");
        properties.put("description", description);

        Map<String, Object> category = new HashMap<>();
        category.put("type", "string");
        category.put("description", "策略分类: technical/fundamental/sentiment/event");
        category.put("default", "technical");
        properties.put("category", category);

        Map<String, Object> marketPhase = new HashMap<>();
        marketPhase.put("type", "string");
        marketPhase.put("description", "当前市场阶段: bull/bear/range/recovery");
        properties.put("market_phase", marketPhase);

        Map<String, Object> strategyId = new HashMap<>();
        strategyId.put("type", "string");
        strategyId.put("description", "策略 ID 建议（英文蛇形命名，如 my_ma_cross）");
        properties.put("strategy_id", strategyId);

        params.put("properties", properties);
        params.put("required", new String[]{"description"});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String description = (String) args.get("description");
        if (description == null || description.isBlank()) {
            throw new ToolException("参数 description 不能为空", "PARAM_MISSING");
        }

        String category = (String) args.getOrDefault("category", "technical");
        String marketPhase = (String) args.get("market_phase");
        String suggestedId = (String) args.get("strategy_id");

        StrategyGenerationContext context = new StrategyGenerationContext();
        context.setUserDescription(description);
        context.setCategory(category);
        context.setMarketPhase(marketPhase);
        context.setSuggestedId(suggestedId);

        StrategyGenerationResult result = generator.generate(context);
        if (result == null) {
            return "策略生成失败：LLM 未能生成有效策略，请调整描述后重试";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("策略生成结果：\n");
        sb.append(String.format("- 策略 ID: %s\n", result.getStrategyId()));
        sb.append(String.format("- 名称: %s\n", result.getLabel()));
        sb.append(String.format("- 分类: %s\n", result.getCategory()));
        sb.append(String.format("- 校验状态: %s\n", result.isValid() ? "通过" : "失败"));

        if (!result.isValid()) {
            sb.append(String.format("- 校验错误: %s\n", result.getValidationErrors()));
            sb.append("\n生成的 YAML（未通过校验，需手动修正）：\n");
            sb.append(result.getYamlContent());
            return sb.toString();
        }

        // 校验通过，自动保存为草稿
        try {
            lifecycleService.create(
                    result.getStrategyId(),
                    result.getLabel(),
                    "LLM 生成: " + description.substring(0, Math.min(100, description.length())),
                    result.getCategory(),
                    result.getYamlContent(),
                    "llm"
            );
            sb.append("- 已保存为草稿策略\n");
            sb.append(String.format("\n推理说明: %s\n", result.getReasoning()));
            sb.append("\n生成的 YAML：\n").append(result.getYamlContent());
        } catch (Exception e) {
            sb.append(String.format("- 保存失败: %s\n", e.getMessage()));
            sb.append("\n生成的 YAML（手动保存）：\n").append(result.getYamlContent());
        }

        return sb.toString();
    }
}
