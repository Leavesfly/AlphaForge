package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.strategy.StrategyCatalog;
import io.leavesfly.stock.application.strategy.StrategyCatalogLoader;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略目录 API。
 */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyController {

    private final StrategyCatalog catalog;
    private final StrategyCatalogLoader catalogLoader;

    public StrategyController(StrategyCatalog catalog, StrategyCatalogLoader catalogLoader) {
        this.catalog = catalog;
        this.catalogLoader = catalogLoader;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String capability) {
        List<StrategyDefinition> strategies = capability == null || capability.isBlank()
                ? catalog.listAll()
                : catalog.listByCapability(capability);

        List<Map<String, Object>> items = strategies.stream().map(this::toSummary).toList();
        return ResponseEntity.ok(Map.of(
                "total", items.size(),
                "categories", catalog.getCategories(),
                "capabilities", catalog.getCapabilities(),
                "strategies", items));
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload() {
        catalogLoader.reload();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "total", catalog.listAll().size(),
                "message", "策略目录已重新加载"));
    }

    private Map<String, Object> toSummary(StrategyDefinition strategy) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", strategy.getId());
        item.put("label", strategy.getLabel());
        item.put("description", strategy.getDescription());
        item.put("category", strategy.getCategory());
        item.put("risk_level", strategy.getRiskLevel());
        item.put("capabilities", strategy.getCapabilities());
        item.put("runtime", strategy.getRuntime());
        item.put("available", strategy.isAvailable());
        if (!strategy.isAvailable() && strategy.getUnavailableReason() != null) {
            item.put("unavailable_reason", strategy.getUnavailableReason());
        }
        return item;
    }
}
