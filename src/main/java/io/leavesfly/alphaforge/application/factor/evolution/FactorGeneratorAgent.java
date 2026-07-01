package io.leavesfly.alphaforge.application.factor.evolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.factor.evolution.model.*;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 因子生成器 Agent — LLM 驱动的因子自动发现与进化
 *
 * 实现策略：
 * 1. 使用 LlmPort.chatForStructuredOutput 让 LLM 返回结构化的因子定义 JSON
 * 2. 因子表达式为受限 DSL（通过 FactorExpressionExecutor 安全执行）
 * 3. 首轮从零生成，后续代通过变异/交叉/反向变异进化
 *
 * DSL 语法示例：
 *   "momentum_20d * volume_ratio_20d"
 *   "rsi_14 - 50"
 *   "max(momentum_5d, momentum_20d) / volatility_20d"
 *   "abs(ma_gap_5_20) * normalize(boll_position)"
 */
@Component
public class FactorGeneratorAgent {

    private static final Logger log = LoggerFactory.getLogger(FactorGeneratorAgent.class);

    private final LlmPort llmPort;
    private final FactorExpressionExecutor expressionExecutor;
    private final ObjectMapper objectMapper;

    /** 可用的原子因子名（LLM 可引用的构建块） */
    private static final List<String> AVAILABLE_FACTORS = List.of(
            "momentum_5d", "momentum_10d", "momentum_20d",
            "rsi_14", "volume_ratio_20d", "volatility_20d",
            "ma_gap_5_20", "boll_position"
    );

    /** 可用的 DSL 函数 */
    private static final List<String> DSL_FUNCTIONS = List.of(
            "abs(x)", "max(x, y)", "min(x, y)", "normalize(x)", "clamp(x, min, max)"
    );

    public FactorGeneratorAgent(LlmPort llmPort,
                                  FactorExpressionExecutor expressionExecutor,
                                  com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.llmPort = llmPort;
        this.expressionExecutor = expressionExecutor;
        this.objectMapper = objectMapper;
    }

    public List<FactorCandidate> generateInitialFactors(FactorGenerationContext context) {
        log.info("生成第 0 代因子（初始生成）...");

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildInitialGenerationPrompt(context);

        return callLlmForFactors(systemPrompt, userPrompt, 0, MutationType.INITIAL, null, null);
    }

    public List<FactorCandidate> mutateFactors(List<FactorCandidate> topFactors,
                                                FactorGenerationContext context) {
        log.info("变异第 {} 代因子（{} 个父代）...", context.getEvolutionGeneration(), topFactors.size());

        String systemPrompt = buildSystemPrompt();

        List<FactorCandidate> mutated = new ArrayList<>();
        for (FactorCandidate parent : topFactors) {
            List<FactorCandidate> children = callLlmForFactors(
                    systemPrompt,
                    buildSingleMutationPrompt(parent, context),
                    context.getEvolutionGeneration(),
                    MutationType.PARAM_MUTATE,
                    parent.getFactorId(),
                    null
            );
            mutated.addAll(children);
        }
        return mutated;
    }

    public List<FactorCandidate> crossbreedFactors(List<FactorCandidate> parents,
                                                    FactorGenerationContext context) {
        log.info("交叉繁殖第 {} 代因子（{} 个父代）...", context.getEvolutionGeneration(), parents.size());

        if (parents.size() < 2) return Collections.emptyList();

        String systemPrompt = buildSystemPrompt();
        List<FactorCandidate> crossbred = new ArrayList<>();

        // 两两配对交叉
        for (int i = 0; i < parents.size() - 1; i++) {
            for (int j = i + 1; j < parents.size(); j++) {
                FactorCandidate parent1 = parents.get(i);
                FactorCandidate parent2 = parents.get(j);

                List<FactorCandidate> children = callLlmForFactors(
                        systemPrompt,
                        buildCrossbreedPrompt(parent1, parent2, context),
                        context.getEvolutionGeneration(),
                        MutationType.CROSSBREED,
                        parent1.getFactorId(),
                        parent2.getFactorId()
                );
                crossbred.addAll(children);
            }
        }
        return crossbred;
    }

    public List<FactorCandidate> inverseMutate(List<FailurePattern> failurePatterns,
                                                FactorGenerationContext context) {
        log.info("反向变异第 {} 代因子（{} 个失败模式）...",
                context.getEvolutionGeneration(), failurePatterns.size());

        if (failurePatterns.isEmpty()) return Collections.emptyList();

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildInverseMutationPrompt(failurePatterns, context);

        return callLlmForFactors(systemPrompt, userPrompt,
                context.getEvolutionGeneration(), MutationType.INVERSE_MUTATE, null, null);
    }

    public String getAgentName() {
        return "factor_generator_agent";
    }

    /** Agent 角色（固定为 "factor_generator"） */
    public String getRole() {
        return "factor_generator";
    }

    // ===== Prompt 构建 =====

    private String buildSystemPrompt() {
        return """
                你是一位专业的量化因子研究员，擅长发现新的 Alpha 因子。
                你的任务是根据给定的信息，生成有效的量化因子表达式。

                ## 可用的原子因子
                """ + String.join(", ", AVAILABLE_FACTORS) + """

                ## 原子因子说明
                - momentum_5d/10d/20d: N日动量（收益率%）
                - rsi_14: 14日RSI（0-100）
                - volume_ratio_20d: 成交量比（当前/20日均量）
                - volatility_20d: 20日年化波动率(%)
                - ma_gap_5_20: MA5-MA20 偏离度(%)
                - boll_position: 布林带位置（0-100）

                ## 支持的 DSL 函数
                """ + String.join(", ", DSL_FUNCTIONS) + """

                ## 表达式规则
                1. 表达式只能使用上述原子因子和 DSL 函数
                2. 支持算术运算: + - * / 和括号 ()
                3. 数值常量可直接使用（如 50, 100, 3.14）
                4. 示例: "momentum_20d * volume_ratio_20d"
                5. 示例: "(rsi_14 - 50) / volatility_20d"
                6. 示例: "max(momentum_5d, momentum_20d) - abs(ma_gap_5_20)"

                ## 输出格式
                返回 JSON 数组，每个因子包含：
                - factor_name: 因子名称（英文蛇形命名，如 vol_weighted_momentum）
                - factor_expression: 因子表达式（DSL）
                - category: 分类（momentum/mean_reversion/volatility/volume/trend/custom）
                - description: 中文描述
                - market_condition: 适用市场条件（bull/bear/consolidation/any）
                - reasoning: 生成该因子的推理过程（中文）
                """;
    }

    private String buildInitialGenerationPrompt(FactorGenerationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("请生成 ").append(context.isFirstGeneration() ? "8" : "6")
                .append(" 个多样化的初始因子表达式。\n\n");
        sb.append("当前市场阶段: ").append(context.getMarketPhase()).append("\n");
        sb.append("已有因子（避免重复）: ").append(
                context.getExistingFactorNames() != null
                        ? String.join(", ", context.getExistingFactorNames()) : "无").append("\n\n");

        if (context.getEvolutionHint() != null && !context.getEvolutionHint().isBlank()) {
            sb.append(context.getEvolutionHint()).append("\n");
        }

        sb.append("要求：\n");
        sb.append("1. 因子表达式要多样化，覆盖不同维度（动量、均值回归、波动率、量价）\n");
        sb.append("2. 优先考虑组合因子（多个原子因子的非线性组合）\n");
        sb.append("3. 考虑当前市场阶段（").append(context.getMarketPhase()).append("）的特征\n");

        return sb.toString();
    }

    private String buildSingleMutationPrompt(FactorCandidate parent, FactorGenerationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对以下因子进行变异，生成 2 个变体：\n\n");
        sb.append("## 原始因子\n");
        sb.append("- 名称: ").append(parent.getFactorName()).append("\n");
        sb.append("- 表达式: ").append(parent.getFactorExpression()).append("\n");
        sb.append("- 分类: ").append(parent.getCategory()).append("\n");
        sb.append("- 描述: ").append(parent.getDescription()).append("\n\n");

        sb.append("## 变异要求\n");
        sb.append("1. 可以调整参数（如将 momentum_20d 改为 momentum_10d）\n");
        sb.append("2. 可以修改表达式结构（如添加 normalize 或 abs 变换）\n");
        sb.append("3. 可以添加条件约束（如用 clamp 限制范围）\n");
        sb.append("4. 两个变体应该走不同的变异方向\n");
        sb.append("5. 变异后的因子表达式必须仍然合法可执行\n\n");

        if (context.getEvolutionHint() != null) {
            sb.append(context.getEvolutionHint());
        }

        return sb.toString();
    }

    private String buildCrossbreedPrompt(FactorCandidate parent1, FactorCandidate parent2,
                                            FactorGenerationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将以下两个因子交叉繁殖，生成 1 个新因子：\n\n");
        sb.append("## 父代 1\n");
        sb.append("- 名称: ").append(parent1.getFactorName()).append("\n");
        sb.append("- 表达式: ").append(parent1.getFactorExpression()).append("\n");
        sb.append("- 分类: ").append(parent1.getCategory()).append("\n\n");
        sb.append("## 父代 2\n");
        sb.append("- 名称: ").append(parent2.getFactorName()).append("\n");
        sb.append("- 表达式: ").append(parent2.getFactorExpression()).append("\n");
        sb.append("- 分类: ").append(parent2.getCategory()).append("\n\n");
        sb.append("## 交叉要求\n");
        sb.append("1. 新因子应结合两个父代的特征\n");
        sb.append("2. 可以取一个父代的主体结构，融入另一个的变换\n");
        sb.append("3. 也可以将两个因子做算术组合\n");
        return sb.toString();
    }

    private String buildInverseMutationPrompt(List<FailurePattern> failurePatterns,
                                                 FactorGenerationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下因子模式在历史上失败了，请生成与之相反或修正方向的因子：\n\n");

        for (int i = 0; i < Math.min(3, failurePatterns.size()); i++) {
            FailurePattern fp = failurePatterns.get(i);
            sb.append(String.format("- %s（出现%d次，平均IC=%.4f）\n",
                    fp.getFailureDescription(), fp.getOccurrenceCount(), fp.getAvgIC()));
        }

        sb.append("\n请生成 2 个反向因子，要求：\n");
        sb.append("1. 与失败因子逻辑相反（如失败因子是动量追涨，反向因子可以是动量反转）\n");
        sb.append("2. 或对失败因子添加修正条件\n");
        return sb.toString();
    }

    // ===== LLM 调用与解析 =====

    private List<FactorCandidate> callLlmForFactors(String systemPrompt, String userPrompt,
                                                       int generationRound, MutationType mutationType,
                                                       String parentFactorId, String secondParentFactorId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> jsonSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "factors", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "factor_name", Map.of("type", "string"),
                                                "factor_expression", Map.of("type", "string"),
                                                "category", Map.of("type", "string"),
                                                "description", Map.of("type", "string"),
                                                "market_condition", Map.of("type", "string"),
                                                "reasoning", Map.of("type", "string")
                                        ),
                                        "required", List.of("factor_name", "factor_expression", "category", "description")
                                )
                        )
                ),
                "required", List.of("factors")
        );

        try {
            String response = llmPort.chatForStructuredOutput(messages, jsonSchema);
            return parseFactorCandidates(response, generationRound, mutationType,
                    parentFactorId, secondParentFactorId);
        } catch (Exception e) {
            log.error("LLM 因子生成失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FactorCandidate> parseFactorCandidates(String jsonResponse, int generationRound,
                                                           MutationType mutationType,
                                                           String parentFactorId, String secondParentFactorId) {
        List<FactorCandidate> candidates = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode factorsArray = root.path("factors");

            if (!factorsArray.isArray()) {
                log.warn("LLM 返回的因子列表不是数组: {}", jsonResponse.substring(0, Math.min(200, jsonResponse.length())));
                return candidates;
            }

            for (JsonNode factorNode : factorsArray) {
                try {
                    String factorName = factorNode.path("factor_name").asText("");
                    String factorExpression = factorNode.path("factor_expression").asText("");
                    String category = factorNode.path("category").asText("custom");
                    String description = factorNode.path("description").asText("");
                    String marketCondition = factorNode.path("market_condition").asText("any");
                    String reasoning = factorNode.path("reasoning").asText("");

                    if (factorName.isBlank() || factorExpression.isBlank()) continue;

                    if (!expressionExecutor.validate(factorExpression)) {
                        log.warn("因子表达式非法，跳过: {} expr={}", factorName, factorExpression);
                        continue;
                    }

                    FactorCandidate candidate = new FactorCandidate.Builder()
                            .factorName(factorName)
                            .factorExpression(factorExpression)
                            .category(category)
                            .description(description)
                            .marketCondition(marketCondition)
                            .generationRound(generationRound)
                            .mutationType(mutationType)
                            .parentFactorId(parentFactorId)
                            .secondParentFactorId(secondParentFactorId)
                            .build();

                    candidates.add(candidate);
                    log.debug("解析因子候选: {} expr={}", factorName, factorExpression);

                } catch (Exception e) {
                    log.warn("解析单个因子失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("解析 LLM 因子 JSON 失败: {}", e.getMessage());
        }

        log.info("成功解析 {} 个因子候选", candidates.size());
        return candidates;
    }
}
