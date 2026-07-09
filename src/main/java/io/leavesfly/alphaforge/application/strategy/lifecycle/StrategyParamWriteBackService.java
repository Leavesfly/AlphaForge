package io.leavesfly.alphaforge.application.strategy.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.domain.model.entity.strategy.CustomStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将优化后的参数写回自定义策略 YAML，并在原为 PUBLISHED 时降级到 TESTING。
 */
@Service
public class StrategyParamWriteBackService {

    private static final Logger log = LoggerFactory.getLogger(StrategyParamWriteBackService.class);

    private final StrategyLifecycleService lifecycleService;
    private final ObjectMapper yamlMapper;
    private final AutonomyPolicy autonomyPolicy;

    public StrategyParamWriteBackService(StrategyLifecycleService lifecycleService,
                                         @Qualifier("yamlObjectMapper") ObjectMapper yamlMapper,
                                         AutonomyPolicy autonomyPolicy) {
        this.lifecycleService = lifecycleService;
        this.yamlMapper = yamlMapper;
        this.autonomyPolicy = autonomyPolicy;
    }

    /**
     * 合并 bestParams 到 backtest.parameters，更新策略并必要时回 TESTING。
     *
     * @return 更新后的策略；非自定义策略返回 null
     */
    @SuppressWarnings("unchecked")
    public CustomStrategy apply(String strategyId, Map<String, Object> bestParams, String changeNote) {
        if (strategyId == null || bestParams == null || bestParams.isEmpty()) {
            throw new IllegalArgumentException("strategyId 与 bestParams 不能为空");
        }

        CustomStrategy existing = lifecycleService.findById(strategyId);
        if (existing == null) {
            log.info("跳过写回：{} 不是自定义策略（可能为内置 YAML）", strategyId);
            return null;
        }

        Map<String, Object> raw;
        try {
            raw = yamlMapper.readValue(existing.getYamlContent(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("解析策略 YAML 失败: " + e.getMessage(), e);
        }

        Map<String, Object> backtest = asMap(raw.get("backtest"));
        if (backtest == null) {
            backtest = new LinkedHashMap<>();
        }
        Map<String, Object> parameters = asMap(backtest.get("parameters"));
        if (parameters == null) {
            parameters = new LinkedHashMap<>();
        }
        parameters.putAll(bestParams);
        backtest.put("parameters", parameters);
        raw.put("backtest", backtest);

        String newYaml;
        try {
            newYaml = yamlMapper.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("序列化策略 YAML 失败: " + e.getMessage(), e);
        }

        String fromState = existing.getLifecycleState();
        // PUBLISHED 不可直接 update，先降级到 TESTING
        if (StrategyLifecycleState.PUBLISHED.name().equals(fromState)) {
            lifecycleService.transition(strategyId, StrategyLifecycleState.TESTING);
            autonomyPolicy.audit("param_writeback_demote", "strategy", strategyId,
                    fromState, StrategyLifecycleState.TESTING.name(),
                    "demote before param writeback");
        }

        String note = changeNote != null ? changeNote : "auto-opt";
        CustomStrategy updated = lifecycleService.update(
                strategyId, newYaml, existing.getLabel(), existing.getDescription(), note);

        autonomyPolicy.audit("param_writeback", "strategy", strategyId,
                fromState, updated.getLifecycleState(),
                "params applied: " + bestParams);

        log.info("参数已写回策略 {}: {}", strategyId, bestParams);
        return updated;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return null;
    }
}
