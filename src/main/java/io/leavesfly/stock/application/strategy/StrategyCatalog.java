package io.leavesfly.stock.application.strategy;

import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 策略目录：内存中的策略注册表。
 *
 * 由 StrategyCatalogLoader 在启动时填充，供各引擎按 id 或 capability 查询策略定义。
 */
@Component
public class StrategyCatalog {

    /** 策略 id → 定义，保持 catalog.yaml 中的声明顺序 */
    private final Map<String, StrategyDefinition> strategies = new LinkedHashMap<>();
    /** 分类代码 → 中文标签，如 trend_following → 趋势跟踪 */
    private Map<String, String> categories = Collections.emptyMap();
    /** 能力代码 → 中文说明，如 backtest → 历史回测 */
    private Map<String, String> capabilities = Collections.emptyMap();

    /** 注册一条策略定义 */
    public void put(StrategyDefinition definition) {
        strategies.put(definition.getId(), definition);
    }

    /** 清空目录（热更新前） */
    public void clear() {
        strategies.clear();
    }

    /** 按策略 id 查找，如 ma_golden_cross */
    public Optional<StrategyDefinition> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategies.get(id));
    }

    public List<StrategyDefinition> listAll() {
        return new ArrayList<>(strategies.values());
    }

    /** 按能力筛选：backtest / screening / scoring */
    public List<StrategyDefinition> listByCapability(String capability) {
        return strategies.values().stream()
                .filter(s -> s.supports(capability))
                .toList();
    }

    public Map<String, String> getCategories() { return categories; }
    public void setCategories(Map<String, String> categories) {
        this.categories = categories != null ? categories : Collections.emptyMap();
    }

    public Map<String, String> getCapabilities() { return capabilities; }
    public void setCapabilities(Map<String, String> capabilities) {
        this.capabilities = capabilities != null ? capabilities : Collections.emptyMap();
    }
}
