package io.leavesfly.alphaforge.application.strategy.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.application.strategy.validator.StrategyValidator;
import io.leavesfly.alphaforge.application.strategy.validator.ValidationResult;
import io.leavesfly.alphaforge.domain.model.entity.strategy.CustomStrategy;
import io.leavesfly.alphaforge.domain.repository.strategy.CustomStrategyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 策略生命周期管理服务
 *
 * 提供完整的策略开发流程：
 * 创建 → 校验 → 调试 → 测试 → 发布 → 废弃
 *
 * 同时负责将 PUBLISHED 状态的自定义策略注册到 StrategyCatalog，
 * 使其能被回测/评分/选股引擎调用。
 */
@Service
public class StrategyLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(StrategyLifecycleService.class);

    private final CustomStrategyRepository repository;
    private final StrategyValidator validator;
    private final StrategyCatalog catalog;
    private final ObjectMapper jsonMapper;

    public StrategyLifecycleService(CustomStrategyRepository repository,
                                   StrategyValidator validator,
                                   StrategyCatalog catalog,
                                   ObjectMapper jsonMapper) {
        this.repository = repository;
        this.validator = validator;
        this.catalog = catalog;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 创建新策略
     */
    public CustomStrategy create(String strategyId, String label, String description,
                                  String category, String yamlContent, String createdBy) {
        if (repository.existsByStrategyId(strategyId)) {
            throw new IllegalArgumentException("策略 ID 已存在: " + strategyId);
        }
        if (catalog.find(strategyId).isPresent()) {
            throw new IllegalArgumentException("策略 ID 与内置策略冲突: " + strategyId);
        }

        // 自动校验
        ValidationResult validation = validator.validate(yamlContent);
        String validationStatus = validation.isValid() ? "valid" : "invalid";
        String validationErrors = null;
        try {
            validationErrors = validation.isValid() ? null : jsonMapper.writeValueAsString(validation.getErrors());
        } catch (Exception ignored) {
            validationErrors = validation.getErrorsJoined();
        }

        CustomStrategy strategy = new CustomStrategy();
        strategy.setStrategyId(strategyId);
        strategy.setLabel(label);
        strategy.setDescription(description);
        strategy.setCategory(category);
        strategy.setYamlContent(yamlContent);
        strategy.setLifecycleState(StrategyLifecycleState.DRAFT.name());
        strategy.setVersion(1);
        strategy.setCapabilities(detectCapabilities(yamlContent));
        strategy.setValidationStatus(validationStatus);
        strategy.setValidationErrors(validationErrors);
        strategy.setLastValidatedAt(LocalDateTime.now());
        strategy.setCreatedBy(createdBy != null ? createdBy : "api");

        repository.insert(strategy);
        repository.insertVersion(strategyId, 1, yamlContent, label, description, "初始创建");

        log.info("策略已创建: id={}, label={}, validation={}", strategyId, label, validationStatus);
        return strategy;
    }

    /**
     * 更新策略（保存新版本）
     */
    public CustomStrategy update(String strategyId, String yamlContent, String label,
                                  String description, String changeNote) {
        CustomStrategy existing = repository.findByStrategyId(strategyId);
        if (existing == null) {
            throw new IllegalArgumentException("策略不存在: " + strategyId);
        }
        if (StrategyLifecycleState.fromString(existing.getLifecycleState()) == StrategyLifecycleState.PUBLISHED) {
            throw new IllegalStateException("已发布的策略不能直接修改，请先回退到 TESTING 状态");
        }

        // 重新校验
        ValidationResult validation = validator.validate(yamlContent);
        String validationStatus = validation.isValid() ? "valid" : "invalid";
        String validationErrors = null;
        try {
            validationErrors = validation.isValid() ? null : jsonMapper.writeValueAsString(validation.getErrors());
        } catch (Exception ignored) {
            validationErrors = validation.getErrorsJoined();
        }

        int newVersion = existing.getVersion() + 1;
        existing.setYamlContent(yamlContent);
        if (label != null && !label.isBlank()) existing.setLabel(label);
        if (description != null) existing.setDescription(description);
        existing.setVersion(newVersion);
        existing.setCapabilities(detectCapabilities(yamlContent));
        existing.setValidationStatus(validationStatus);
        existing.setValidationErrors(validationErrors);
        existing.setLastValidatedAt(LocalDateTime.now());

        repository.update(existing);
        repository.insertVersion(strategyId, newVersion, yamlContent,
                existing.getLabel(), existing.getDescription(),
                changeNote != null ? changeNote : "版本更新 v" + newVersion);

        // 如果策略已注册在目录中，更新目录
        if (validation.isValid()) {
            registerToCatalog(existing);
        }

        log.info("策略已更新: id={}, version={}, validation={}", strategyId, newVersion, validationStatus);
        return existing;
    }

    /**
     * 删除策略（归档而非物理删除）
     */
    public void delete(String strategyId) {
        CustomStrategy existing = repository.findByStrategyId(strategyId);
        if (existing == null) {
            throw new IllegalArgumentException("策略不存在: " + strategyId);
        }
        // 从目录中移除
        catalog.remove(strategyId);
        // 物理删除（版本历史保留）
        repository.deleteByStrategyId(strategyId);
        log.info("策略已删除: id={}", strategyId);
    }

    /**
     * 克隆策略（内置或自定义策略）
     */
    public CustomStrategy clone(String sourceStrategyId, String newStrategyId,
                                String newLabel, String createdBy) {
        if (repository.existsByStrategyId(newStrategyId) || catalog.find(newStrategyId).isPresent()) {
            throw new IllegalArgumentException("目标策略 ID 已存在: " + newStrategyId);
        }

        // 从内置目录或自定义策略中获取源策略
        String yamlContent = null;
        String label = newLabel;
        String description = null;
        String category = "technical";

        // 先查找内置策略
        var builtin = catalog.find(sourceStrategyId);
        if (builtin.isPresent()) {
            StrategyDefinition def = builtin.get();
            yamlContent = serializeToYaml(def);
            label = newLabel != null ? newLabel : def.getLabel() + " (副本)";
            description = def.getDescription();
            category = def.getCategory();
        } else {
            // 查找自定义策略
            CustomStrategy source = repository.findByStrategyId(sourceStrategyId);
            if (source == null) {
                throw new IllegalArgumentException("源策略不存在: " + sourceStrategyId);
            }
            yamlContent = source.getYamlContent();
            label = newLabel != null ? newLabel : source.getLabel() + " (副本)";
            description = source.getDescription();
            category = source.getCategory();
        }

        // 替换策略 ID
        yamlContent = yamlContent.replaceFirst("id:\\s*" + sourceStrategyId, "id: " + newStrategyId);

        return create(newStrategyId, label, description, category, yamlContent,
                createdBy != null ? createdBy : "clone");
    }

    /**
     * 状态转换
     */
    public CustomStrategy transition(String strategyId, StrategyLifecycleState targetState) {
        CustomStrategy existing = repository.findByStrategyId(strategyId);
        if (existing == null) {
            throw new IllegalArgumentException("策略不存在: " + strategyId);
        }

        StrategyLifecycleState currentState = StrategyLifecycleState.fromString(existing.getLifecycleState());
        if (!currentState.canTransitionTo(targetState)) {
            throw new IllegalStateException(
                    "非法状态转换: " + currentState + " → " + targetState);
        }

        // 转到 PUBLISHED 或 TESTING 前必须通过校验
        if ((targetState == StrategyLifecycleState.PUBLISHED || targetState == StrategyLifecycleState.TESTING)
                && !"valid".equals(existing.getValidationStatus())) {
            throw new IllegalStateException("策略未通过校验，不能转换到 " + targetState);
        }

        existing.setLifecycleState(targetState.name());
        repository.update(existing);

        // 根据状态管理目录注册
        if (targetState == StrategyLifecycleState.PUBLISHED) {
            registerToCatalog(existing);
        } else if (targetState == StrategyLifecycleState.DEPRECATED
                || targetState == StrategyLifecycleState.ARCHIVED) {
            catalog.remove(strategyId);
        }

        log.info("策略状态转换: id={}, {} → {}", strategyId, currentState, targetState);
        return existing;
    }

    /**
     * 重新校验策略
     */
    public CustomStrategy revalidate(String strategyId) {
        CustomStrategy existing = repository.findByStrategyId(strategyId);
        if (existing == null) {
            throw new IllegalArgumentException("策略不存在: " + strategyId);
        }

        ValidationResult validation = validator.validate(existing.getYamlContent());
        String validationStatus = validation.isValid() ? "valid" : "invalid";
        String validationErrors = null;
        try {
            validationErrors = validation.isValid() ? null : jsonMapper.writeValueAsString(validation.getErrors());
        } catch (Exception ignored) {
            validationErrors = validation.getErrorsJoined();
        }

        existing.setValidationStatus(validationStatus);
        existing.setValidationErrors(validationErrors);
        existing.setLastValidatedAt(LocalDateTime.now());
        repository.update(existing);

        return existing;
    }

    /**
     * 获取策略详情
     */
    public CustomStrategy findById(String strategyId) {
        return repository.findByStrategyId(strategyId);
    }

    /**
     * 获取全部自定义策略
     */
    public List<CustomStrategy> findAll() {
        return repository.findAll();
    }

    /**
     * 按状态查询
     */
    public List<CustomStrategy> findByState(StrategyLifecycleState state) {
        return repository.findByLifecycleState(state.name());
    }

    /**
     * 获取版本历史
     */
    public List<CustomStrategy> getVersions(String strategyId) {
        return repository.findVersions(strategyId);
    }

    /**
     * 加载所有已发布的自定义策略到目录（启动时调用）
     */
    public void loadPublishedStrategiesToCatalog() {
        List<CustomStrategy> published = repository.findByLifecycleState(
                StrategyLifecycleState.PUBLISHED.name());
        for (CustomStrategy strategy : published) {
            try {
                registerToCatalog(strategy);
            } catch (Exception e) {
                log.error("加载自定义策略到目录失败: id={}", strategy.getStrategyId(), e);
            }
        }
        if (!published.isEmpty()) {
            log.info("已加载 {} 个自定义策略到策略目录", published.size());
        }
    }

    // ==================== 内部方法 ====================

    private void registerToCatalog(CustomStrategy strategy) {
        try {
            StrategyDefinition definition = validator.validateAndParse(strategy.getYamlContent());
            catalog.put(definition);
            log.debug("策略已注册到目录: {}", strategy.getStrategyId());
        } catch (Exception e) {
            log.error("策略注册到目录失败: id={}, error={}", strategy.getStrategyId(), e.getMessage());
        }
    }

    private String detectCapabilities(String yamlContent) {
        StringBuilder caps = new StringBuilder();
        if (yamlContent.contains("backtest:")) {
            if (caps.length() > 0) caps.append(",");
            caps.append("backtest");
        }
        if (yamlContent.contains("screening:")) {
            if (caps.length() > 0) caps.append(",");
            caps.append("screening");
        }
        if (yamlContent.contains("scoring:")) {
            if (caps.length() > 0) caps.append(",");
            caps.append("scoring");
        }
        return caps.toString();
    }

    private String serializeToYaml(StrategyDefinition def) {
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
        if (!def.getApplicableCap().isEmpty()) {
            sb.append("applicable_cap: ").append(def.getApplicableCap()).append("\n");
        }
        if (!def.getTags().isEmpty()) {
            sb.append("tags: ").append(def.getTags()).append("\n");
        }
        return sb.toString();
    }
}

