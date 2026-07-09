package io.leavesfly.alphaforge.application.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.strategy.condition.BacktestConditionEvaluator;
import io.leavesfly.alphaforge.application.strategy.condition.ScoringConditionRegistry;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
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
    private final io.leavesfly.alphaforge.domain.repository.strategy.CustomStrategyRepository customStrategyRepository;

    public StrategyCatalogLoader(StrategyCatalog catalog,
                                 BacktestConditionEvaluator backtestConditionEvaluator,
                                 @org.springframework.beans.factory.annotation.Qualifier("yamlObjectMapper")
                                 ObjectMapper yamlMapper,
                                 io.leavesfly.alphaforge.domain.repository.strategy.CustomStrategyRepository customStrategyRepository) {
        this.catalog = catalog;
        this.backtestConditionEvaluator = backtestConditionEvaluator;
        this.yamlMapper = yamlMapper;
        this.customStrategyRepository = customStrategyRepository;
    }

    /** 测试用：无 Spring 上下文时加载 catalog */
    public static StrategyCatalogLoader createAndLoad() {
        StrategyCatalog catalog = new StrategyCatalog();
        BacktestConditionEvaluator evaluator = new BacktestConditionEvaluator();
        ObjectMapper yamlMapper = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        StrategyCatalogLoader loader = new StrategyCatalogLoader(catalog, evaluator, yamlMapper, null);
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

        // 加载用户自定义策略（PUBLISHED 状态）
        loadCustomStrategies();
    }

    /** 加载已发布的自定义策略到策略目录 */
    private void loadCustomStrategies() {
        if (customStrategyRepository == null) {
            return; // 测试模式跳过
        }
        try {
            java.util.List<io.leavesfly.alphaforge.domain.model.entity.strategy.CustomStrategy> published =
                    customStrategyRepository.findByLifecycleState("PUBLISHED");
            int loaded = 0;
            for (var cs : published) {
                try {
                    StrategyDefinition definition = parseYamlContent(cs.getYamlContent());
                    if (definition != null) {
                        validateAndMarkAvailability(definition);
                        catalog.put(definition);
                        loaded++;
                    }
                } catch (Exception e) {
                    log.warn("加载自定义策略失败: id={}, error={}", cs.getStrategyId(), e.getMessage());
                }
            }
            if (loaded > 0) {
                log.info("已加载 {} 个自定义策略到策略目录", loaded);
            }
        } catch (Exception e) {
            log.warn("加载自定义策略目录失败: {}", e.getMessage());
        }
    }

    /** 从 YAML 字符串解析策略定义（支持旧三段与统一 rules DSL） */
    private StrategyDefinition parseYamlContent(String yamlContent) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = yamlMapper.readValue(yamlContent, Map.class);
        StrategyDefinition definition = StrategyYamlMapper.mapDefinition(raw);
        definition.setCapabilities(StrategyYamlMapper.inferCapabilities(definition));
        definition.setRuntime("implemented");
        return definition;
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
                if (!ScoringConditionRegistry.isSupportedKey(key)) {
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
            StrategyDefinition definition = StrategyYamlMapper.mapDefinition(raw);
            if (definition.getId() == null || definition.getId().isBlank()) {
                definition.setId(id);
            }
            Object caps = meta.get("capabilities");
            if (caps instanceof List<?> list) {
                definition.setCapabilities(list.stream().map(String::valueOf).toList());
            } else {
                definition.setCapabilities(StrategyYamlMapper.inferCapabilities(definition));
            }
            definition.setRuntime(stringVal(meta.get("runtime"), "planned"));
            return definition;
        }
    }

    private String stringVal(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    public StrategyCatalog getCatalog() {
        return catalog;
    }
}
