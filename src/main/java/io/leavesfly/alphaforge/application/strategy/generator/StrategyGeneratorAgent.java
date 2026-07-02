package io.leavesfly.alphaforge.application.strategy.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.alphaforge.application.strategy.condition.ScoringConditionRegistry;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.application.strategy.validator.StrategyValidator;
import io.leavesfly.alphaforge.application.strategy.validator.ValidationResult;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LLM 驱动的策略生成器
 *
 * 核心能力：
 * 1. 自然语言 → 策略 YAML：用户描述策略思路，LLM 生成完整 YAML
 * 2. 市场感知生成：注入当前市场环境，生成适配策略
 * 3. Few-shot 增强：注入已有策略作为参考示例
 * 4. 迭代优化：根据回测反馈自动修改策略
 *
 * 实现：
 * - 使用 LlmPort.chatForStructuredOutput 获取结构化 JSON
 * - 自动校验生成的 YAML（StrategyValidator）
 * - 校验失败时自动重试（最多 2 次，将错误信息反馈给 LLM 修正）
 */
@Component
public class StrategyGeneratorAgent {

    private static final Logger log = LoggerFactory.getLogger(StrategyGeneratorAgent.class);

    private static final int MAX_RETRY = 2;

    private final LlmPort llmPort;
    private final StrategyCatalog catalog;
    private final StrategyValidator validator;
    private final ObjectMapper objectMapper;

    public StrategyGeneratorAgent(LlmPort llmPort,
                                  StrategyCatalog catalog,
                                  StrategyValidator validator,
                                  ObjectMapper objectMapper) {
        this.llmPort = llmPort;
        this.catalog = catalog;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据自然语言描述生成策略 YAML
     *
     * @param context 生成上下文
     * @return 生成结果（含 YAML + 元信息 + 校验状态）
     */
    public StrategyGenerationResult generate(StrategyGenerationContext context) {
        log.info("LLM 策略生成开始: desc={}", context.getUserDescription());

        String systemPrompt = buildSystemPrompt();
        List<String> referenceYamls = collectReferenceStrategies(context);

        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            String userPrompt = buildUserPrompt(context, referenceYamls, attempt);
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));

            Map<String, Object> jsonSchema = buildJsonSchema();

            try {
                String response = llmPort.chatForStructuredOutput(messages, jsonSchema);
                StrategyGenerationResult result = parseResponse(response);

                if (result == null) {
                    log.warn("LLM 返回无法解析（attempt={}）", attempt);
                    continue;
                }

                // 自动校验
                ValidationResult validation = validator.validate(result.getYamlContent());
                result.setValid(validation.isValid());
                result.setValidationErrors(validation.isValid() ? null : validation.getErrorsJoined());

                if (validation.isValid()) {
                    log.info("策略生成成功（attempt={}）: id={}, label={}", attempt, result.getStrategyId(), result.getLabel());
                    return result;
                }

                log.warn("策略校验失败（attempt={}）: {}", attempt, validation.getErrorsJoined());
                // 将校验错误反馈到下一轮 prompt 中（通过 referenceYamls 传递错误信息）
                referenceYamls = List.of("校验错误（请修正）: " + validation.getErrorsJoined());

            } catch (Exception e) {
                log.error("LLM 策略生成异常（attempt={}）: {}", attempt, e.getMessage());
            }
        }

        log.warn("LLM 策略生成失败，已达最大重试次数");
        return null;
    }

    // ==================== Prompt 构建 ====================

    private String buildSystemPrompt() {
        return """
                你是一位专业的量化策略工程师，擅长将自然语言策略思路转化为可执行的 YAML 策略定义。

                ## 策略 YAML 格式规范

                ```yaml
                schema_version: 1
                id: <策略ID，小写字母+数字+下划线>
                label: <中文策略名称>
                description: <策略描述>
                category: <technical|fundamental|sentiment|event>
                risk_level: <low|medium|high>
                applicable_market: [<适用市场阶段: bull/bear/range/recovery/all>]
                applicable_cap: [<适用市值: large/mid/small/all>]
                tags: [<语义标签>]

                backtest:
                  parameters:
                    <参数名>: <参数值>
                  param_space:
                    <参数名>: [<搜索候选值列表>]
                  entry_conditions:
                    - type: <条件类型>
                      <条件参数>
                  exit_conditions:
                    - type: <条件类型>
                      <条件参数>
                  position_size: <0-1之间的仓位比例>
                ```

                ## 支持的 backtest 条件类型

                """ + String.join(", ", BacktestConditionEvaluator.SUPPORTED_TYPES) + """

                ## 条件类型参数说明

                - ma_cross: 均线交叉 (fast: MA5/MA10, slow: MA20/MA30, direction: golden/death)
                - volume_breakout: 放量突破 (multiple: 2.0, min_change: 3.0)
                - stop_loss: 止损 (pct: -8，相对买入价的百分比)
                - take_profit: 止盈 (pct: 15)
                - price_above_ma: 价格在均线上方 (ma: 20 或 [5,10,20])
                - price_below_ma / break_below_ma: 价格跌破均线 (ma: 30)
                - ma_arrangement: 均线排列 (direction: bullish)
                - trend_above: 趋势在均线上方 (ma: 60)
                - pullback_to: 回调到均线 (ma: 20, tolerance_pct: 2.0)
                - volume_shrink: 缩量 (ratio: 0.5)
                - near_box_low / near_box_high: 接近箱体低点/高点 (tolerance_pct: 2.0)
                - box_break_down: 破位下行 (pct: -3)
                - consecutive_up: 连续上涨 (days: 3)
                - first_pullback: 首次回调 (max_pct: -5)
                - holding_days: 持仓天数 (max: 5)
                - wave_position: 波浪位置 (position: wave3_start/wave5_end)
                - sentiment_extreme: 情绪极值 (level: oversold/overbought)
                - event_trigger: 事件触发 (category: positive/negative, min_change: 5.0)
                - fundamental_filter: 基本面过滤 (revenue_growth_min, roe_min, max_pe)
                - price_near_support: 价格接近支撑 (tolerance_pct: 3.0)
                - fundamental_deterioration: 基本面恶化 (无额外参数)
                - change_below: 跌幅超限 (pct: -5)

                ## 支持的 scoring 条件 key

                """ + String.join(", ", ScoringConditionRegistry.SUPPORTED_KEYS) + """

                ## 输出要求

                返回 JSON 对象，包含：
                - strategy_id: 策略ID（英文蛇形命名）
                - label: 中文策略名称
                - description: 策略描述
                - category: 分类 (technical/fundamental/sentiment/event)
                - risk_level: 风险等级 (low/medium/high)
                - yaml_content: 完整的 YAML 内容（纯文本，不含 markdown 代码块标记）
                - reasoning: LLM 生成该策略的推理过程（中文）
                """;
    }

    private String buildUserPrompt(StrategyGenerationContext context, List<String> references, int attempt) {
        StringBuilder sb = new StringBuilder();

        if (attempt == 0) {
            sb.append("请根据以下描述生成一个量化策略：\n\n");
        } else {
            sb.append("上次生成的策略校验失败，请根据以下错误修正后重新生成：\n\n");
        }

        sb.append("## 用户描述\n").append(context.getUserDescription()).append("\n\n");

        if (context.getCategory() != null && !context.getCategory().isBlank()) {
            sb.append("## 分类建议\n").append(context.getCategory()).append("\n\n");
        }
        if (context.getMarketPhase() != null && !context.getMarketPhase().isBlank()) {
            sb.append("## 当前市场阶段\n").append(context.getMarketPhase()).append("\n\n");
        }
        if (context.getSuggestedId() != null && !context.getSuggestedId().isBlank()) {
            sb.append("## 策略 ID 建议\n").append(context.getSuggestedId()).append("\n\n");
        }
        if (context.getAdditionalRequirements() != null && !context.getAdditionalRequirements().isBlank()) {
            sb.append("## 额外要求\n").append(context.getAdditionalRequirements()).append("\n\n");
        }

        // Few-shot 参考策略
        if (references != null && !references.isEmpty()) {
            sb.append("## 参考策略（已有的类似策略，可借鉴格式但不要完全复制）\n");
            for (int i = 0; i < Math.min(2, references.size()); i++) {
                sb.append("```yaml\n").append(references.get(i)).append("\n```\n\n");
            }
        }

        // 迭代模式：注入回测反馈
        if (context.isIterative()) {
            sb.append("## 上次回测结果（请据此优化策略条件）\n");
            sb.append(context.getLastBacktestSummary()).append("\n\n");
        }

        sb.append("## 生成要求\n");
        sb.append("1. 入场条件必须全部满足才买入（AND 逻辑）\n");
        sb.append("2. 出场条件满足任一即卖出（OR 逻辑），至少包含止损条件\n");
        sb.append("3. position_size 建议设为 0.9-0.95\n");
        sb.append("4. 如果适合参数优化，请声明 param_space\n");
        sb.append("5. yaml_content 必须是纯文本 YAML，不要包含 ```yaml 标记\n");

        return sb.toString();
    }

    // ==================== JSON Schema ====================

    private Map<String, Object> buildJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "strategy_id", Map.of("type", "string"),
                        "label", Map.of("type", "string"),
                        "description", Map.of("type", "string"),
                        "category", Map.of("type", "string"),
                        "risk_level", Map.of("type", "string"),
                        "yaml_content", Map.of("type", "string"),
                        "reasoning", Map.of("type", "string")
                ),
                "required", List.of("strategy_id", "label", "category", "yaml_content", "reasoning")
        );
    }

    // ==================== 响应解析 ====================

    private StrategyGenerationResult parseResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            String strategyId = root.path("strategy_id").asText("");
            String label = root.path("label").asText("");
            String description = root.path("description").asText("");
            String category = root.path("category").asText("technical");
            String riskLevel = root.path("risk_level").asText("medium");
            String yamlContent = root.path("yaml_content").asText("");
            String reasoning = root.path("reasoning").asText("");

            if (strategyId.isBlank() || yamlContent.isBlank()) {
                log.warn("LLM 返回缺少必要字段: strategy_id={}, yaml_length={}", strategyId, yamlContent.length());
                return null;
            }

            // 清理 YAML：移除可能的 markdown 代码块标记
            yamlContent = cleanYaml(yamlContent);

            // 确保 id 和 label 与 YAML 内容一致
            if (!yamlContent.contains("id:")) {
                yamlContent = "id: " + strategyId + "\n" + yamlContent;
            }
            if (!yamlContent.contains("label:")) {
                yamlContent = "label: " + label + "\n" + yamlContent;
            }
            if (!yamlContent.contains("category:")) {
                yamlContent = "category: " + category + "\n" + yamlContent;
            }
            if (!yamlContent.contains("risk_level:")) {
                yamlContent = "risk_level: " + riskLevel + "\n" + yamlContent;
            }

            return StrategyGenerationResult.of(yamlContent, strategyId, label, category, reasoning);
        } catch (Exception e) {
            log.error("解析 LLM 策略生成响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 清理 YAML 内容中的 markdown 标记
     */
    private String cleanYaml(String yaml) {
        String cleaned = yaml.trim();
        // 移除开头的 ```yaml 或 ```
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
        }
        // 移除结尾的 ```
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    // ==================== 参考策略收集 ====================

    private List<String> collectReferenceStrategies(StrategyGenerationContext context) {
        List<String> references = new ArrayList<>();
        List<String> refIds = context.getReferenceStrategyIds();

        if (refIds == null || refIds.isEmpty()) {
            // 默认取同分类的 2 个策略作为参考
            String category = context.getCategory();
            if (category != null && !category.isBlank()) {
                List<StrategyDefinition> sameCategory = catalog.listAll().stream()
                        .filter(s -> category.equals(s.getCategory()))
                        .filter(StrategyDefinition::isAvailable)
                        .limit(2)
                        .toList();
                for (StrategyDefinition s : sameCategory) {
                    references.add(serializeDefinition(s));
                }
            }
        } else {
            for (String id : refIds) {
                catalog.find(id).ifPresent(s -> references.add(serializeDefinition(s)));
            }
        }

        // 兜底：如果没有同分类参考，取一个通用参考
        if (references.isEmpty()) {
            catalog.find("ma_golden_cross").ifPresent(s -> references.add(serializeDefinition(s)));
        }

        return references;
    }

    private String serializeDefinition(StrategyDefinition def) {
        StringBuilder sb = new StringBuilder();
        sb.append("schema_version: ").append(def.getSchemaVersion()).append("\n");
        sb.append("id: ").append(def.getId()).append("\n");
        sb.append("label: ").append(def.getLabel()).append("\n");
        sb.append("description: ").append(def.getDescription()).append("\n");
        sb.append("category: ").append(def.getCategory()).append("\n");
        sb.append("risk_level: ").append(def.getRiskLevel()).append("\n");
        if (!def.getApplicableMarket().isEmpty()) {
            sb.append("applicable_market: ").append(def.getApplicableMarket()).append("\n");
        }
        if (!def.getTags().isEmpty()) {
            sb.append("tags: ").append(def.getTags()).append("\n");
        }
        return sb.toString();
    }
}
