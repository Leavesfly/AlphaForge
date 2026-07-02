package io.leavesfly.alphaforge.application.strategy.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.alphaforge.application.strategy.condition.ScoringConditionRegistry;
import io.leavesfly.alphaforge.application.strategy.model.BacktestProfile;
import io.leavesfly.alphaforge.application.strategy.model.ScoringProfile;
import io.leavesfly.alphaforge.application.strategy.model.ScreeningProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalogLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 策略校验器：校验 YAML 内容的结构完整性、条件类型支持度、参数合理性。
 *
 * 校验维度：
 * 1. YAML 语法校验 — 能否被 YAML Mapper 解析
 * 2. 必填字段校验 — id / label / category 等
 * 3. 条件类型校验 — backtest 条件是否在 SUPPORTED_TYPES 中
 * 4. 评分条件校验 — scoring 条件 key 是否在 SUPPORTED_KEYS 中
 * 5. 参数合理性校验 — 仓位比例范围、均线周期等
 */
@Component
public class StrategyValidator {

    private static final Logger log = LoggerFactory.getLogger(StrategyValidator.class);

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
     *
     * @param yamlContent 策略 YAML 字符串
     * @return 校验结果
     */
    public ValidationResult validate(String yamlContent) {
        ValidationResult result = ValidationResult.success();

        if (yamlContent == null || yamlContent.isBlank()) {
            result.addError("策略 YAML 内容不能为空");
            return result;
        }

        // 1. YAML 语法校验
        Map<String, Object> raw;
        try {
            raw = yamlMapper.readValue(yamlContent, Map.class);
        } catch (Exception e) {
            result.addError("YAML 语法错误: " + e.getMessage());
            return result;
        }

        // 2. 必填字段校验
        validateRequiredFields(raw, result);
        if (!result.isValid()) {
            return result;
        }

        // 3. 字段格式校验
        validateFieldFormats(raw, result);

        // 4. backtest 条件校验
        validateBacktestConditions(raw, result);

        // 5. scoring 条件校验
        validateScoringConditions(raw, result);

        // 6. 参数合理性校验
        validateParameters(raw, result);

        return result;
    }

    /**
     * 校验并返回解析后的 StrategyDefinition
     */
    @SuppressWarnings("unchecked")
    public StrategyDefinition validateAndParse(String yamlContent) {
        ValidationResult result = validate(yamlContent);
        if (!result.isValid()) {
            throw new IllegalArgumentException("策略校验失败: " + result.getErrorsJoined());
        }
        try {
            Map<String, Object> raw = yamlMapper.readValue(yamlContent, Map.class);
            StrategyDefinition definition = new StrategyDefinition();
            definition.setId(stringVal(raw.get("id")));
            definition.setSchemaVersion(intVal(raw.get("schema_version"), 1));
            definition.setLabel(stringVal(raw.get("label")));
            definition.setDescription(stringVal(raw.get("description")));
            definition.setCategory(stringVal(raw.get("category")));
            definition.setRiskLevel(stringVal(raw.get("risk_level"), "medium"));

            if (raw.get("backtest") instanceof Map<?, ?> backtestRaw) {
                definition.setBacktest(mapBacktest((Map<String, Object>) backtestRaw));
            }
            if (raw.get("screening") instanceof Map<?, ?> screeningRaw) {
                definition.setScreening(mapScreening((Map<String, Object>) screeningRaw));
            }
            if (raw.get("scoring") instanceof Map<?, ?> scoringRaw) {
                definition.setScoring(mapScoring((Map<String, Object>) scoringRaw));
            }

            List<String> caps = new java.util.ArrayList<>();
            if (definition.getBacktest() != null) caps.add("backtest");
            if (definition.getScreening() != null) caps.add("screening");
            if (definition.getScoring() != null) caps.add("scoring");
            definition.setCapabilities(caps);
            definition.setRuntime("implemented");
            definition.setAvailable(true);

            // 解析策略元数据
            definition.setApplicableMarket(parseStringList(raw.get("applicable_market")));
            definition.setApplicableCap(parseStringList(raw.get("applicable_cap")));
            definition.setTags(parseStringList(raw.get("tags")));

            return definition;
        } catch (Exception e) {
            throw new IllegalArgumentException("策略解析失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private BacktestProfile mapBacktest(Map<String, Object> raw) {
        BacktestProfile profile = new BacktestProfile();
        if (raw.get("parameters") instanceof Map<?, ?> params) {
            profile.setParameters((Map<String, Object>) params);
        }
        if (raw.get("entry_conditions") instanceof List<?> entries) {
            profile.setEntryConditions(entries.stream().map(this::asConditionMap).toList());
        }
        if (raw.get("exit_conditions") instanceof List<?> exits) {
            profile.setExitConditions(exits.stream().map(this::asConditionMap).toList());
        }
        profile.setPositionSize(doubleVal(raw.get("position_size"), 0.95));
        if (raw.get("simulation") instanceof Map<?, ?> simulation) {
            profile.setSimulation((Map<String, Object>) simulation);
        }
        if (raw.get("param_space") instanceof Map<?, ?> paramSpace) {
            Map<String, List<Object>> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) paramSpace).entrySet()) {
                if (entry.getValue() instanceof List<?> list) {
                    result.put(entry.getKey(), new java.util.ArrayList<>(list));
                }
            }
            profile.setParamSpace(result);
        }
        return profile;
    }

    @SuppressWarnings("unchecked")
    private ScreeningProfile mapScreening(Map<String, Object> raw) {
        ScreeningProfile profile = new ScreeningProfile();
        if (raw.get("parameters") instanceof Map<?, ?> params) {
            profile.setParameters((Map<String, Object>) params);
        }
        if (raw.get("scoring_rules") instanceof List<?> rules) {
            profile.setScoringRules(rules.stream().map(this::asConditionMap).toList());
        }
        if (raw.get("reason_templates") instanceof Map<?, ?> templates) {
            profile.setReasonTemplates((Map<String, String>) templates);
        }
        if (raw.get("fallback") instanceof Map<?, ?> fallback) {
            profile.setFallback((Map<String, Object>) fallback);
        }
        return profile;
    }

    @SuppressWarnings("unchecked")
    private ScoringProfile mapScoring(Map<String, Object> raw) {
        ScoringProfile profile = new ScoringProfile();
        profile.setScoreWeight(intVal(raw.get("score_weight"), 0));
        if (raw.get("conditions") instanceof Map<?, ?> conditions) {
            profile.setConditions((Map<String, Object>) conditions);
        }
        profile.setLabel(stringVal(raw.get("label")));
        profile.setAutoDecay(Boolean.TRUE.equals(raw.get("auto_decay")));
        profile.setDecayWindow(intVal(raw.get("decay_window"), 30));
        profile.setMinWeight(intVal(raw.get("min_weight"), 5));
        return profile;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asConditionMap(Object item) {
        return (Map<String, Object>) item;
    }

    private int intVal(Object value, int defaultValue) {
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private double doubleVal(Object value, double defaultValue) {
        return value instanceof Number ? ((Number) value).doubleValue() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String str && !str.isBlank()) {
            return List.of(str.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of();
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
        Object backtestObj = raw.get("backtest");
        if (!(backtestObj instanceof Map<?, ?> backtestRaw)) {
            return; // backtest 段可选
        }

        // 校验入场条件
        if (backtestRaw.get("entry_conditions") instanceof List<?> entries) {
            for (Object item : entries) {
                if (item instanceof Map<?, ?> condition) {
                    checkConditionType(condition, "backtest.entry_conditions", result);
                }
            }
        }

        // 校验出场条件
        if (backtestRaw.get("exit_conditions") instanceof List<?> exits) {
            for (Object item : exits) {
                if (item instanceof Map<?, ?> condition) {
                    checkConditionType(condition, "backtest.exit_conditions", result);
                }
            }
        }

        // 校验仓位比例
        Object positionSize = backtestRaw.get("position_size");
        if (positionSize instanceof Number num) {
            double ps = num.doubleValue();
            if (ps <= 0 || ps > 1) {
                result.addError("position_size 必须在 (0, 1] 范围内，当前: " + ps);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateScoringConditions(Map<String, Object> raw, ValidationResult result) {
        Object scoringObj = raw.get("scoring");
        if (!(scoringObj instanceof Map<?, ?> scoringRaw)) {
            return; // scoring 段可选
        }

        Object conditions = scoringRaw.get("conditions");
        if (conditions instanceof Map<?, ?> conds) {
            for (Object key : conds.keySet()) {
                String keyStr = String.valueOf(key);
                if (!ScoringConditionRegistry.SUPPORTED_KEYS.contains(keyStr)) {
                    result.addWarning("scoring 条件 key 未实现: " + keyStr + "（策略可加载但该条件不生效）");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateParameters(Map<String, Object> raw, ValidationResult result) {
        Object backtestObj = raw.get("backtest");
        if (!(backtestObj instanceof Map<?, ?> backtestRaw)) {
            return;
        }

        // 检查参数空间定义
        if (backtestRaw.get("param_space") instanceof Map<?, ?> paramSpace) {
            for (Map.Entry<?, ?> entry : paramSpace.entrySet()) {
                if (!(entry.getValue() instanceof List<?> list) || list.isEmpty()) {
                    result.addWarning("param_space 中 " + entry.getKey() + " 的搜索值为空或非列表");
                }
            }
        }

        // 检查参数中的均线周期是否为正整数
        if (backtestRaw.get("parameters") instanceof Map<?, ?> params) {
            checkPositiveInt(params, "fast_period", result);
            checkPositiveInt(params, "slow_period", result);
            checkPositiveInt(params, "short_ma", result);
            checkPositiveInt(params, "long_ma", result);
            checkPositiveInt(params, "ma_period", result);
            checkPositiveInt(params, "lookback_days", result);
        }
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

    private String stringVal(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
