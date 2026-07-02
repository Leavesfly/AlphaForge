package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalogLoader;
import io.leavesfly.alphaforge.application.strategy.debug.DebugTraceResult;
import io.leavesfly.alphaforge.application.strategy.debug.StrategyDebugService;
import io.leavesfly.alphaforge.application.strategy.engine.StrategyPerformanceTracker;
import io.leavesfly.alphaforge.application.strategy.generator.StrategyGenerationContext;
import io.leavesfly.alphaforge.application.strategy.generator.StrategyGenerationResult;
import io.leavesfly.alphaforge.application.strategy.generator.StrategyGeneratorAgent;
import io.leavesfly.alphaforge.application.strategy.lifecycle.StrategyLifecycleService;
import io.leavesfly.alphaforge.application.strategy.lifecycle.StrategyLifecycleState;
import io.leavesfly.alphaforge.application.strategy.model.ScoringProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.application.strategy.template.StrategyTemplate;
import io.leavesfly.alphaforge.application.strategy.template.StrategyTemplateService;
import io.leavesfly.alphaforge.application.strategy.validator.StrategyValidator;
import io.leavesfly.alphaforge.application.strategy.validator.ValidationResult;
import io.leavesfly.alphaforge.domain.model.entity.strategy.CustomStrategy;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略目录 API — 提供完整的策略开发流程：
 *
 * 1. 策略目录查询（内置 + 自定义）
 * 2. 策略 CRUD（创建/更新/删除/克隆）
 * 3. 策略校验（YAML 语法 + 条件类型 + 参数）
 * 4. 策略调试（逐 K 线 trace）
 * 5. 策略模板（预置模板列表 + 从模板创建）
 * 6. 策略生命周期管理（状态转换 + 版本历史）
 * 7. 策略复盘报告
 * 8. LLM 驱动策略生成（自然语言 → YAML）
 */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyController {

    private final StrategyCatalog catalog;
    private final StrategyCatalogLoader catalogLoader;
    private final StrategyPerformanceTracker performanceTracker;
    private final StrategyLifecycleService lifecycleService;
    private final StrategyValidator validator;
    private final StrategyDebugService debugService;
    private final StrategyTemplateService templateService;
    private final MarketDataPort marketDataPort;
    private final StrategyGeneratorAgent generatorAgent;

    public StrategyController(StrategyCatalog catalog, StrategyCatalogLoader catalogLoader,
                              StrategyPerformanceTracker performanceTracker,
                              StrategyLifecycleService lifecycleService,
                              StrategyValidator validator,
                              StrategyDebugService debugService,
                              StrategyTemplateService templateService,
                              MarketDataPort marketDataPort,
                              StrategyGeneratorAgent generatorAgent) {
        this.catalog = catalog;
        this.catalogLoader = catalogLoader;
        this.performanceTracker = performanceTracker;
        this.lifecycleService = lifecycleService;
        this.validator = validator;
        this.debugService = debugService;
        this.templateService = templateService;
        this.marketDataPort = marketDataPort;
        this.generatorAgent = generatorAgent;
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

    // ==================== 策略 CRUD ====================

    /** 创建策略 */
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody StrategyCreateRequest request) {
        CustomStrategy strategy = lifecycleService.create(
                request.getStrategyId(),
                request.getLabel(),
                request.getDescription(),
                request.getCategory(),
                request.getYamlContent(),
                "api");
        return ResponseEntity.ok(toCustomStrategySummary(strategy));
    }

    /** 更新策略 */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody StrategyUpdateRequest request) {
        CustomStrategy strategy = lifecycleService.update(
                id,
                request.getYamlContent(),
                request.getLabel(),
                request.getDescription(),
                request.getChangeNote());
        return ResponseEntity.ok(toCustomStrategySummary(strategy));
    }

    /** 删除策略 */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        lifecycleService.delete(id);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "策略已删除: " + id));
    }

    /** 克隆策略 */
    @PostMapping("/{id}/clone")
    public ResponseEntity<?> clone(@PathVariable String id, @RequestBody CloneRequest request) {
        CustomStrategy strategy = lifecycleService.clone(
                id,
                request.getNewStrategyId(),
                request.getNewLabel(),
                "clone");
        return ResponseEntity.ok(toCustomStrategySummary(strategy));
    }

    // ==================== 策略校验 ====================

    /** 校验策略 YAML */
    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody ValidateRequest request) {
        ValidationResult result = validator.validate(request.getYamlContent());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", result.isValid());
        response.put("errors", result.getErrors());
        response.put("warnings", result.getWarnings());
        return ResponseEntity.ok(response);
    }

    /** 重新校验已存在的策略 */
    @PostMapping("/{id}/validate")
    public ResponseEntity<?> revalidate(@PathVariable String id) {
        CustomStrategy strategy = lifecycleService.revalidate(id);
        return ResponseEntity.ok(toCustomStrategySummary(strategy));
    }

    // ==================== 策略调试 ====================

    /** 逐 K 线调试策略 */
    @PostMapping("/{id}/debug")
    public ResponseEntity<?> debug(@PathVariable String id, @RequestBody DebugRequest request) {
        StrategyDefinition definition = catalog.find(id).orElse(null);
        if (definition == null) {
            CustomStrategy custom = lifecycleService.findById(id);
            if (custom != null) {
                definition = validator.validateAndParse(custom.getYamlContent());
            }
        }
        if (definition == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "策略不存在: " + id));
        }

        int days = request.getDays() != null ? request.getDays() : 120;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days + 30); // 多取30天用于预热
        List<StockDailyData> data = marketDataPort.getHistoryData(request.getStockCode(), startDate, endDate);
        if (data == null || data.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "无 K 线数据: " + request.getStockCode()));
        }

        DebugTraceResult trace = debugService.debug(definition, data);
        return ResponseEntity.ok(trace);
    }

    // ==================== 策略生命周期 ====================

    /** 查询自定义策略列表 */
    @GetMapping("/custom")
    public ResponseEntity<?> listCustom(
            @RequestParam(required = false) String state) {
        List<CustomStrategy> strategies = state != null && !state.isBlank()
                ? lifecycleService.findByState(StrategyLifecycleState.fromString(state))
                : lifecycleService.findAll();
        List<Map<String, Object>> items = strategies.stream().map(this::toCustomStrategySummary).toList();
        return ResponseEntity.ok(Map.of("total", items.size(), "strategies", items));
    }

    /** 查询自定义策略详情 */
    @GetMapping("/custom/{id}")
    public ResponseEntity<?> getCustom(@PathVariable String id) {
        CustomStrategy strategy = lifecycleService.findById(id);
        if (strategy == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toCustomStrategyDetail(strategy));
    }

    /** 状态转换 */
    @PostMapping("/{id}/transition")
    public ResponseEntity<?> transition(@PathVariable String id, @RequestBody TransitionRequest request) {
        StrategyLifecycleState target = StrategyLifecycleState.fromString(request.getTargetState());
        CustomStrategy strategy = lifecycleService.transition(id, target);
        return ResponseEntity.ok(toCustomStrategySummary(strategy));
    }

    /** 查询版本历史 */
    @GetMapping("/{id}/versions")
    public ResponseEntity<?> versions(@PathVariable String id) {
        List<CustomStrategy> versions = lifecycleService.getVersions(id);
        List<Map<String, Object>> items = versions.stream().map(v -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("version", v.getVersion());
            item.put("label", v.getLabel());
            item.put("description", v.getDescription());
            item.put("created_at", v.getCreatedAt());
            return item;
        }).toList();
        return ResponseEntity.ok(Map.of("total", items.size(), "versions", items));
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

    /** 从模板创建策略 */
    @PostMapping("/from-template")
    public ResponseEntity<?> fromTemplate(@RequestBody FromTemplateRequest request) {
        StrategyTemplate template = templateService.findById(request.getTemplateId());
        if (template == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "模板不存在: " + request.getTemplateId()));
        }
        String yamlContent = templateService.generateFromTemplate(
                request.getTemplateId(),
                request.getStrategyId(),
                request.getLabel());

        CustomStrategy strategy = lifecycleService.create(
                request.getStrategyId(),
                request.getLabel(),
                "从模板创建: " + request.getTemplateId(),
                template.getCategory(),
                yamlContent,
                "template");
        return ResponseEntity.ok(toCustomStrategySummary(strategy));
    }

    // ==================== LLM 驱动策略生成 ====================

    /** LLM 生成策略（自然语言 → YAML） */
    @PostMapping("/generate")
    public ResponseEntity<?> generateStrategy(@RequestBody GenerateRequest request) {
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入策略描述"));
        }

        StrategyGenerationContext context = new StrategyGenerationContext();
        context.setUserDescription(request.getDescription());
        context.setCategory(request.getCategory());
        context.setMarketPhase(request.getMarketPhase());
        context.setSuggestedId(request.getStrategyId());
        context.setAdditionalRequirements(request.getAdditionalRequirements());

        StrategyGenerationResult result = generatorAgent.generate(context);
        if (result == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "LLM 未能生成有效策略，请调整描述后重试"));
        }

        // 如果校验通过且请求要求自动保存
        if (result.isValid() && request.isAutoSave()) {
            try {
                CustomStrategy saved = lifecycleService.create(
                        result.getStrategyId(),
                        result.getLabel(),
                        "LLM 生成: " + request.getDescription().substring(
                                0, Math.min(100, request.getDescription().length())),
                        result.getCategory(),
                        result.getYamlContent(),
                        "llm");
                Map<String, Object> response = toGenerationResultMap(result);
                response.put("saved", true);
                response.put("strategy", toCustomStrategySummary(saved));
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                Map<String, Object> response = toGenerationResultMap(result);
                response.put("saved", false);
                response.put("save_error", e.getMessage());
                return ResponseEntity.ok(response);
            }
        }

        return ResponseEntity.ok(toGenerationResultMap(result));
    }

    private Map<String, Object> toGenerationResultMap(StrategyGenerationResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", result.isValid());
        map.put("strategy_id", result.getStrategyId());
        map.put("label", result.getLabel());
        map.put("category", result.getCategory());
        map.put("valid", result.isValid());
        map.put("validation_errors", result.getValidationErrors());
        map.put("reasoning", result.getReasoning());
        map.put("yaml_content", result.getYamlContent());
        return map;
    }

    // ==================== DTO ====================

    public static class StrategyCreateRequest {
        private String strategyId;
        private String label;
        private String description;
        private String category;
        private String yamlContent;
        public String getStrategyId() { return strategyId; }
        public void setStrategyId(String strategyId) { this.strategyId = strategyId; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getYamlContent() { return yamlContent; }
        public void setYamlContent(String yamlContent) { this.yamlContent = yamlContent; }
    }

    public static class StrategyUpdateRequest {
        private String yamlContent;
        private String label;
        private String description;
        private String changeNote;
        public String getYamlContent() { return yamlContent; }
        public void setYamlContent(String yamlContent) { this.yamlContent = yamlContent; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getChangeNote() { return changeNote; }
        public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
    }

    public static class CloneRequest {
        private String newStrategyId;
        private String newLabel;
        public String getNewStrategyId() { return newStrategyId; }
        public void setNewStrategyId(String newStrategyId) { this.newStrategyId = newStrategyId; }
        public String getNewLabel() { return newLabel; }
        public void setNewLabel(String newLabel) { this.newLabel = newLabel; }
    }

    public static class ValidateRequest {
        private String yamlContent;
        public String getYamlContent() { return yamlContent; }
        public void setYamlContent(String yamlContent) { this.yamlContent = yamlContent; }
    }

    public static class DebugRequest {
        private String stockCode;
        private Integer days;
        public String getStockCode() { return stockCode; }
        public void setStockCode(String stockCode) { this.stockCode = stockCode; }
        public Integer getDays() { return days; }
        public void setDays(Integer days) { this.days = days; }
    }

    public static class TransitionRequest {
        private String targetState;
        public String getTargetState() { return targetState; }
        public void setTargetState(String targetState) { this.targetState = targetState; }
    }

    public static class FromTemplateRequest {
        private String templateId;
        private String strategyId;
        private String label;
        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
        public String getStrategyId() { return strategyId; }
        public void setStrategyId(String strategyId) { this.strategyId = strategyId; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    public static class GenerateRequest {
        private String description;
        private String category;
        private String marketPhase;
        private String strategyId;
        private String additionalRequirements;
        private boolean autoSave = true;
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getMarketPhase() { return marketPhase; }
        public void setMarketPhase(String marketPhase) { this.marketPhase = marketPhase; }
        public String getStrategyId() { return strategyId; }
        public void setStrategyId(String strategyId) { this.strategyId = strategyId; }
        public String getAdditionalRequirements() { return additionalRequirements; }
        public void setAdditionalRequirements(String additionalRequirements) { this.additionalRequirements = additionalRequirements; }
        public boolean isAutoSave() { return autoSave; }
        public void setAutoSave(boolean autoSave) { this.autoSave = autoSave; }
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

    private Map<String, Object> toCustomStrategySummary(CustomStrategy strategy) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("strategy_id", strategy.getStrategyId());
        item.put("label", strategy.getLabel());
        item.put("description", strategy.getDescription());
        item.put("category", strategy.getCategory());
        item.put("lifecycle_state", strategy.getLifecycleState());
        item.put("version", strategy.getVersion());
        item.put("capabilities", strategy.getCapabilities());
        item.put("validation_status", strategy.getValidationStatus());
        item.put("source_strategy_id", strategy.getSourceStrategyId());
        item.put("created_by", strategy.getCreatedBy());
        item.put("created_at", strategy.getCreatedAt());
        item.put("updated_at", strategy.getUpdatedAt());
        item.put("is_custom", true);
        return item;
    }

    private Map<String, Object> toCustomStrategyDetail(CustomStrategy strategy) {
        Map<String, Object> item = toCustomStrategySummary(strategy);
        item.put("yaml_content", strategy.getYamlContent());
        item.put("validation_errors", strategy.getValidationErrors());
        item.put("last_validated_at", strategy.getLastValidatedAt());
        item.put("note", strategy.getNote());
        return item;
    }
}

