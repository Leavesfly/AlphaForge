package io.leavesfly.alphaforge.application.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.alphaforge.application.strategy.condition.ScoringConditionRegistry;
import io.leavesfly.alphaforge.application.strategy.model.BacktestProfile;
import io.leavesfly.alphaforge.application.strategy.model.ScoringProfile;
import io.leavesfly.alphaforge.application.strategy.model.ScreeningProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略 YAML 加载器：启动时填充 {@link StrategyCatalog} 并校验条件覆盖。
 */
@Component
public class StrategyCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(StrategyCatalogLoader.class);
    private static final String CATALOG_PATH = "strategies/catalog.yaml";

    private final StrategyCatalog catalog;
    private final BacktestConditionEvaluator backtestConditionEvaluator;
    private final ObjectMapper yamlMapper;

    public StrategyCatalogLoader(StrategyCatalog catalog,
                                 BacktestConditionEvaluator backtestConditionEvaluator,
                                 @org.springframework.beans.factory.annotation.Qualifier("yamlObjectMapper")
                                 ObjectMapper yamlMapper) {
        this.catalog = catalog;
        this.backtestConditionEvaluator = backtestConditionEvaluator;
        this.yamlMapper = yamlMapper;
    }

    /** 测试用：无 Spring 上下文时加载 catalog */
    public static StrategyCatalogLoader createAndLoad() {
        StrategyCatalog catalog = new StrategyCatalog();
        BacktestConditionEvaluator evaluator = new BacktestConditionEvaluator();
        ObjectMapper yamlMapper = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        StrategyCatalogLoader loader = new StrategyCatalogLoader(catalog, evaluator, yamlMapper);
        loader.load();
        return loader;
    }

    @PostConstruct
    public void load() {
        catalog.clear();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CATALOG_PATH)) {
            if (in == null) {
                throw new IllegalStateException("策略目录不存在: " + CATALOG_PATH);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = yamlMapper.readValue(in, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, String> categories = (Map<String, String>) root.get("categories");
            catalog.setCategories(categories);

            @SuppressWarnings("unchecked")
            Map<String, String> capabilities = (Map<String, String>) root.get("capabilities");
            catalog.setCapabilities(capabilities);

            @SuppressWarnings("unchecked")
            Map<String, Object> strategies = (Map<String, Object>) root.get("strategies");
            if (strategies == null) {
                throw new IllegalStateException("catalog.yaml 缺少 strategies 节点");
            }

            for (Map.Entry<String, Object> entry : strategies.entrySet()) {
                String id = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = (Map<String, Object>) entry.getValue();
                String file = (String) meta.get("file");
                StrategyDefinition definition = loadDefinition(id, file, meta);
                validateAndMarkAvailability(definition);
                catalog.put(definition);
            }

            log.info("已加载 {} 个策略定义", catalog.listAll().size());
        } catch (Exception e) {
            throw new IllegalStateException("加载策略目录失败", e);
        }
    }

    /** 热更新策略目录（admin API 调用） */
    public synchronized void reload() {
        load();
        log.info("策略目录已热更新");
    }

    private void validateAndMarkAvailability(StrategyDefinition definition) {
        List<String> issues = new ArrayList<>();
        String runtime = definition.getRuntime() != null ? definition.getRuntime() : "planned";

        if ("planned".equals(runtime)) {
            definition.setAvailable(false);
            definition.setUnavailableReason("策略尚未实现 (planned)");
            return;
        }

        if (definition.getBacktest() != null) {
            for (Map<String, Object> c : definition.getBacktest().getEntryConditions()) {
                checkBacktestCondition(c, issues);
            }
            for (Map<String, Object> c : definition.getBacktest().getExitConditions()) {
                checkBacktestCondition(c, issues);
            }
        }
        if (definition.getScoring() != null && definition.getScoring().getConditions() != null) {
            for (String key : definition.getScoring().getConditions().keySet()) {
                if (!ScoringConditionRegistry.SUPPORTED_KEYS.contains(key)) {
                    issues.add("未实现的 scoring 条件: " + key);
                }
            }
        }

        if ("partial".equals(runtime) || !issues.isEmpty()) {
            definition.setAvailable(false);
            definition.setUnavailableReason(issues.isEmpty()
                    ? "策略部分实现 (partial)"
                    : String.join("; ", issues));
            log.warn("策略 {} 标记为不可用: {}", definition.getId(), definition.getUnavailableReason());
        } else {
            definition.setAvailable(true);
            definition.setUnavailableReason(null);
        }
    }

    private void checkBacktestCondition(Map<String, Object> condition, List<String> issues) {
        String type = condition.get("type") != null ? String.valueOf(condition.get("type")) : "";
        if (type.isEmpty()) {
            issues.add("backtest 条件缺少 type");
        } else if (!BacktestConditionEvaluator.SUPPORTED_TYPES.contains(type)) {
            issues.add("未实现的 backtest 条件: " + type);
        }
    }

    @SuppressWarnings("unchecked")
    private StrategyDefinition loadDefinition(String id, String file, Map<String, Object> meta) throws Exception {
        String path = "strategies/" + file;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("策略定义不存在: " + path);
            }
            Map<String, Object> raw = yamlMapper.readValue(in, Map.class);
            StrategyDefinition definition = new StrategyDefinition();
            definition.setId(stringVal(raw.get("id"), id));
            definition.setSchemaVersion(intVal(raw.get("schema_version"), 1));
            definition.setLabel(stringVal(raw.get("label"), id));
            definition.setDescription(stringVal(raw.get("description"), ""));
            definition.setCategory(stringVal(raw.get("category"), ""));
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

            Object caps = meta.get("capabilities");
            if (caps instanceof List<?> list) {
                definition.setCapabilities(list.stream().map(String::valueOf).toList());
            }
            definition.setRuntime(stringVal(meta.get("runtime"), "planned"));

            // 解析策略元数据（供 LLM 策略编排使用）
            definition.setApplicableMarket(parseStringList(raw.get("applicable_market")));
            definition.setApplicableCap(parseStringList(raw.get("applicable_cap")));
            definition.setTags(parseStringList(raw.get("tags")));

            return definition;
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
            profile.setParamSpace(parseParamSpace((Map<String, Object>) paramSpace));
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
        profile.setLabel(stringVal(raw.get("label"), null));
        // 解析自动衰减配置
        profile.setAutoDecay(Boolean.TRUE.equals(raw.get("auto_decay")));
        profile.setDecayWindow(intVal(raw.get("decay_window"), 30));
        profile.setMinWeight(intVal(raw.get("min_weight"), 5));
        return profile;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asConditionMap(Object item) {
        return (Map<String, Object>) item;
    }

    private String stringVal(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private int intVal(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private double doubleVal(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String str && !str.isBlank()) {
            return List.of(str.split(","))
                    .stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Object>> parseParamSpace(Map<String, Object> raw) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                result.put(entry.getKey(), new ArrayList<>(list));
            }
        }
        return result;
    }

    public StrategyCatalog getCatalog() {
        return catalog;
    }
}
