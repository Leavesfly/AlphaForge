package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalogLoader;
import io.leavesfly.alphaforge.application.strategy.engine.StrategyPerformanceTracker;
import io.leavesfly.alphaforge.application.strategy.model.ScoringProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.application.strategy.template.StrategyTemplate;
import io.leavesfly.alphaforge.application.strategy.template.StrategyTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略目录 API — 只读的策略发现与复盘视图：
 *
 * 1. 策略目录查询（内置 + 自定义）
 * 2. 策略复盘报告
 * 3. 策略模板列表
 *
 * 策略的创作与生命周期管理（CRUD/校验/调试/状态转换/从模板创建/LLM 生成）
 * 由 {@link StrategyLifecycleController} 提供。
 */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyController {

    private final StrategyCatalog catalog;
    private final StrategyCatalogLoader catalogLoader;
    private final StrategyPerformanceTracker performanceTracker;
    private final StrategyTemplateService templateService;

    public StrategyController(StrategyCatalog catalog, StrategyCatalogLoader catalogLoader,
                              StrategyPerformanceTracker performanceTracker,
                              StrategyTemplateService templateService) {
        this.catalog = catalog;
        this.catalogLoader = catalogLoader;
        this.performanceTracker = performanceTracker;
        this.templateService = templateService;
    }

    // ==================== 策略目录查询 ====================

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

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        return catalog.find(id)
                .<ResponseEntity<?>>map(s -> ResponseEntity.ok(toSummary(s)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload() {
        catalogLoader.reload();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "total", catalog.listAll().size(),
                "message", "策略目录已重新加载"));
    }

    // ==================== 策略复盘报告 ====================

    @GetMapping("/review")
    public ResponseEntity<Map<String, Object>> review() {
        List<StrategyDefinition> scoringStrategies = catalog.listByCapability("scoring");
        List<Map<String, Object>> items = new ArrayList<>();

        for (StrategyDefinition s : scoringStrategies) {
            ScoringProfile profile = s.getScoring();
            if (profile == null || profile.getScoreWeight() <= 0) continue;

            double matchRate = performanceTracker.getMatchRate(s.getId());
            int effectiveWeight = performanceTracker.getEffectiveWeight(
                    s.getId(), profile.getScoreWeight(), profile.isAutoDecay(), profile.getMinWeight());

            String status;
            if (matchRate < 0) status = "no_data";
            else if (matchRate < 0.1) status = "stale";
            else if (matchRate > 0.9) status = "low_discrimination";
            else if (matchRate >= 0.3 && matchRate <= 0.7) status = "healthy";
            else status = "watching";

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("label", profile.getLabel() != null ? profile.getLabel() : s.getLabel());
            item.put("original_weight", profile.getScoreWeight());
            item.put("effective_weight", effectiveWeight);
            item.put("auto_decay", profile.isAutoDecay());
            item.put("match_rate", matchRate >= 0 ? Math.round(matchRate * 1000) / 10.0 : null);
            item.put("status", status);
            items.add(item);
        }

        int stale = (int) items.stream().filter(i -> "stale".equals(i.get("status"))).count();
        int lowDisc = (int) items.stream().filter(i -> "low_discrimination".equals(i.get("status"))).count();
        int healthy = (int) items.stream().filter(i -> "healthy".equals(i.get("status"))).count();

        return ResponseEntity.ok(Map.of(
                "total", items.size(),
                "stale", stale,
                "low_discrimination", lowDisc,
                "healthy", healthy,
                "strategies", items));
    }

    // ==================== 策略模板 ====================

    /** 获取模板列表 */
    @GetMapping("/templates")
    public ResponseEntity<?> listTemplates() {
        List<StrategyTemplate> templates = templateService.listAll();
        List<Map<String, Object>> items = templates.stream().map(t -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("template_id", t.getTemplateId());
            item.put("label", t.getLabel());
            item.put("description", t.getDescription());
            item.put("category", t.getCategory());
            return item;
        }).toList();
        return ResponseEntity.ok(Map.of("total", items.size(), "templates", items));
    }

    // ==================== 辅助方法 ====================

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
        item.put("applicable_market", strategy.getApplicableMarket());
        item.put("applicable_cap", strategy.getApplicableCap());
        item.put("tags", strategy.getTags());
        if (strategy.getBacktest() != null && strategy.getBacktest().hasParamSpace()) {
            item.put("has_param_space", true);
            item.put("param_space", strategy.getBacktest().getParamSpace().keySet());
        }
        if (!strategy.isAvailable() && strategy.getUnavailableReason() != null) {
            item.put("unavailable_reason", strategy.getUnavailableReason());
        }
        item.put("is_custom", false);
        return item;
    }
}
