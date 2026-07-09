package io.leavesfly.alphaforge.application.strategy.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.strategy.StrategyYamlMapper;
import io.leavesfly.alphaforge.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.alphaforge.application.strategy.condition.ScoringConditionRegistry;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 策略校验器：校验 YAML 内容的结构完整性、条件类型支持度、参数合理性。
 *
 * <p>同时支持旧三段（backtest/scoring/screening）与统一 {@code rules} DSL。</p>
 */
@Component
public class StrategyValidator {

    private static final Set<String> VALID_CATEGORIES = Set.of(
            "technical", "fundamental", "sentiment", "event"
    );

    private static final Set<String> VALID_RISK_LEVELS = Set.of(
            "low", "medium", "high"
    );

    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final ObjectMapper yamlMapper;

    public StrategyValidator(@org.springframework.beans.factory.annotation.Qualifier("yamlObjectMapper")
                             ObjectMapper yamlMapper) {
        this.yamlMapper = yamlMapper;
    }

    /**
     * 校验策略 YAML 内容
     */
    @SuppressWarnings("unchecked")
    public ValidationResult validate(String yamlContent) {
        ValidationResult result = ValidationResult.success();

        if (yamlContent == null || yamlContent.isBlank()) {
            result.addError("策略 YAML 内容不能为空");
            return result;
        }

        Map<String, Object> raw;
        try {
            raw = yamlMapper.readValue(yamlContent, Map.class);
        } catch (Exception e) {
            result.addError("YAML 语法错误: " + e.getMessage());
            return result;
        }

        validateRequiredFields(raw, result);
        if (!result.isValid()) {
            return result;
        }

        validateFieldFormats(raw, result);
        validateBacktestConditions(raw, result);
        validateScoringConditions(raw, result);
        validateParameters(raw, result);

        return result;
    }

    /**
     * 校验并返回解析后的 StrategyDefinition（支持旧三段与统一 rules DSL）
     */
    @SuppressWarnings("unchecked")
    public StrategyDefinition validateAndParse(String yamlContent) {
        ValidationResult result = validate(yamlContent);
        if (!result.isValid()) {
            throw new IllegalArgumentException("策略校验失败: " + result.getErrorsJoined());
        }
        try {
            Map<String, Object> raw = yamlMapper.readValue(yamlContent, Map.class);
            StrategyDefinition definition = StrategyYamlMapper.mapDefinition(raw);
            definition.setCapabilities(StrategyYamlMapper.inferCapabilities(definition));
            definition.setRuntime("implemented");
            definition.setAvailable(true);
            return definition;
        } catch (Exception e) {
            throw new IllegalArgumentException("策略解析失败: " + e.getMessage(), e);
        }
    }

    private void validateRequiredFields(Map<String, Object> raw, ValidationResult result) {
        if (raw.get("id") == null || String.valueOf(raw.get("id")).isBlank()) {
            result.addError("缺少必填字段: id");
        }
        if (raw.get("label") == null || String.valueOf(raw.get("label")).isBlank()) {
            result.addError("缺少必填字段: label");
        }
        if (raw.get("category") == null || String.valueOf(raw.get("category")).isBlank()) {
            result.addError("缺少必填字段: category");
        }
    }

    private void validateFieldFormats(Map<String, Object> raw, ValidationResult result) {
        String id = stringVal(raw.get("id"));
        if (!id.isEmpty() && !VALID_ID_PATTERN.matcher(id).matches()) {
            result.addError("策略 id 格式不合法，需以小写字母开头，仅含小写字母/数字/下划线: " + id);
        }

        String category = stringVal(raw.get("category"));
        if (!category.isEmpty() && !VALID_CATEGORIES.contains(category)) {
            result.addError("策略分类不合法，可选值: " + VALID_CATEGORIES);
        }

        String riskLevel = stringVal(raw.get("risk_level"));
        if (!riskLevel.isEmpty() && !VALID_RISK_LEVELS.contains(riskLevel)) {
            result.addWarning("risk_level 值不在推荐范围内，可选: " + VALID_RISK_LEVELS);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateBacktestConditions(Map<String, Object> raw, ValidationResult result) {
        Map<?, ?> signal = resolveSignalSection(raw);
        if (signal == null) {
            return;
        }

        Object entry = signal.containsKey("entry") ? signal.get("entry") : signal.get("entry_conditions");
        if (entry instanceof List<?> entries) {
            for (Object item : entries) {
                if (item instanceof Map<?, ?> condition) {
                    checkConditionType(condition, "signal.entry", result);
                }
            }
        }

        Object exit = signal.containsKey("exit") ? signal.get("exit") : signal.get("exit_conditions");
        if (exit instanceof List<?> exits) {
            for (Object item : exits) {
                if (item instanceof Map<?, ?> condition) {
                    checkConditionType(condition, "signal.exit", result);
                }
            }
        }

        Object positionSize = signal.get("position_size");
        if (positionSize instanceof Number num) {
            double ps = num.doubleValue();
            if (ps <= 0 || ps > 1) {
                result.addError("position_size 必须在 (0, 1] 范围内，当前: " + ps);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateScoringConditions(Map<String, Object> raw, ValidationResult result) {
        Map<?, ?> score = resolveScoreSection(raw);
        if (score == null) {
            return;
        }

        Object conditions = score.get("conditions");
        if (conditions instanceof Map<?, ?> conds) {
            for (Object key : conds.keySet()) {
                String keyStr = String.valueOf(key);
                if (!ScoringConditionRegistry.SUPPORTED_KEYS.contains(keyStr)) {
                    result.addWarning("scoring 条件 key 未实现: " + keyStr + "（策略可加载但该条件不生效）");
                }
            }
        }

        // rules.score.when 使用统一 type，校验 type 是否可映射
        if (score.get("when") instanceof List<?> whenList) {
            for (Object item : whenList) {
                if (item instanceof Map<?, ?> condition) {
                    Object type = condition.get("type");
                    if (type == null || String.valueOf(type).isBlank()) {
                        result.addError("rules.score.when 条件缺少 type");
                        continue;
                    }
                    Map<String, Object> mapped = StrategyYamlMapper.typedConditionToScoringKeys(
                            (Map<String, Object>) condition);
                    if (mapped.isEmpty()) {
                        result.addWarning("rules.score.when 条件暂无评分映射: " + type);
                    } else {
                        for (String key : mapped.keySet()) {
                            if (!ScoringConditionRegistry.isSupportedKey(key)) {
                                result.addWarning("映射后的 scoring key 未实现: " + key);
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateParameters(Map<String, Object> raw, ValidationResult result) {
        Map<?, ?> signal = resolveSignalSection(raw);
        if (signal == null) {
            return;
        }

        Object paramSpace = signal.get("param_space");
        if (paramSpace == null && raw.get("rules") instanceof Map<?, ?> rules) {
            paramSpace = rules.get("param_space");
        }
        if (paramSpace instanceof Map<?, ?> space) {
            for (Map.Entry<?, ?> entry : space.entrySet()) {
                if (!(entry.getValue() instanceof List<?> list) || list.isEmpty()) {
                    result.addWarning("param_space 中 " + entry.getKey() + " 的搜索值为空或非列表");
                }
            }
        }

        Object parameters = signal.get("parameters");
        if (parameters == null && raw.get("rules") instanceof Map<?, ?> rules) {
            parameters = rules.get("parameters");
        }
        if (parameters instanceof Map<?, ?> params) {
            checkPositiveInt(params, "fast_period", result);
            checkPositiveInt(params, "slow_period", result);
            checkPositiveInt(params, "short_ma", result);
            checkPositiveInt(params, "long_ma", result);
            checkPositiveInt(params, "ma_period", result);
            checkPositiveInt(params, "lookback_days", result);
        }
    }

    private Map<?, ?> resolveSignalSection(Map<String, Object> raw) {
        if (raw.get("backtest") instanceof Map<?, ?> backtest) {
            return backtest;
        }
        if (raw.get("rules") instanceof Map<?, ?> rules
                && rules.get("signal") instanceof Map<?, ?> signal) {
            return signal;
        }
        return null;
    }

    private Map<?, ?> resolveScoreSection(Map<String, Object> raw) {
        if (raw.get("scoring") instanceof Map<?, ?> scoring) {
            return scoring;
        }
        if (raw.get("rules") instanceof Map<?, ?> rules
                && rules.get("score") instanceof Map<?, ?> score) {
            return score;
        }
        return null;
    }

    private void checkConditionType(Map<?, ?> condition, String section, ValidationResult result) {
        Object type = condition.get("type");
        if (type == null || String.valueOf(type).isBlank()) {
            result.addError(section + " 条件缺少 type 字段");
            return;
        }
        String typeStr = String.valueOf(type);
        if (!BacktestConditionEvaluator.SUPPORTED_TYPES.contains(typeStr)) {
            result.addError(section + " 条件类型不支持: " + typeStr
                    + "，支持类型: " + BacktestConditionEvaluator.SUPPORTED_TYPES);
        }
    }

    private void checkPositiveInt(Map<?, ?> params, String key, ValidationResult result) {
        Object value = params.get(key);
        if (value instanceof Number num && num.intValue() <= 0) {
            result.addWarning("参数 " + key + " 应为正整数，当前: " + value);
        }
    }

    private String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
