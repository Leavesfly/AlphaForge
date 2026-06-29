package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.evaluation.BenchmarkComparison;
import io.leavesfly.alphaforge.application.evaluation.BenchmarkReport;
import io.leavesfly.alphaforge.application.evaluation.BenchmarkSuite;
import io.leavesfly.alphaforge.application.evaluation.LlmAnalysisQuality;
import io.leavesfly.alphaforge.application.factor.evolution.FactorEvolutionConfig;
import io.leavesfly.alphaforge.application.factor.evolution.FactorEvolutionOrchestrator;
import io.leavesfly.alphaforge.application.factor.evolution.model.EvolutionResult;
import io.leavesfly.alphaforge.presentation.api.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * AI 能力 API — 因子自进化、基准评估、LLM 分析质量评估
 *
 * 对应论文前沿能力的 REST API 入口，使新增能力可通过 HTTP 调用。
 */
@RestController
@RequestMapping("/api/v1/ai-capability")
public class AiCapabilityController {

    private static final Logger log = LoggerFactory.getLogger(AiCapabilityController.class);

    private final FactorEvolutionOrchestrator evolutionOrchestrator;
    private final BenchmarkSuite benchmarkSuite;

    public AiCapabilityController(FactorEvolutionOrchestrator evolutionOrchestrator,
                                   BenchmarkSuite benchmarkSuite) {
        this.evolutionOrchestrator = evolutionOrchestrator;
        this.benchmarkSuite = benchmarkSuite;
    }

    // ===== 因子自进化 =====

    /**
     * 触发一轮因子进化
     * POST /api/v1/ai-capability/factor-evolution/run
     */
    @PostMapping("/factor-evolution/run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runFactorEvolution(
            @RequestBody(required = false) Map<String, Object> body) {

        FactorEvolutionConfig config = FactorEvolutionConfig.defaultConfig();
        if (body != null) {
            if (body.containsKey("max_generations")) {
                config.setMaxGenerationRounds(((Number) body.get("max_generations")).intValue());
            }
            if (body.containsKey("candidates_per_round")) {
                config.setCandidatesPerRound(((Number) body.get("candidates_per_round")).intValue());
            }
            if (body.containsKey("min_ic")) {
                config.setMinIC(((Number) body.get("min_ic")).doubleValue());
            }
            if (body.containsKey("preset")) {
                String preset = (String) body.get("preset");
                config = switch (preset) {
                    case "aggressive" -> FactorEvolutionConfig.aggressive();
                    case "conservative" -> FactorEvolutionConfig.conservative();
                    default -> config;
                };
            }
        }

        try {
            EvolutionResult result = evolutionOrchestrator.runEvolutionCycle(config);
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "generation", result.getGeneration(),
                    "candidates_generated", result.getCandidatesGenerated(),
                    "candidates_passed", result.getCandidatesPassed(),
                    "candidates_promoted", result.getCandidatesPromoted(),
                    "converged", result.isConverged(),
                    "convergence_reason", result.getConvergenceReason() != null ? result.getConvergenceReason() : "",
                    "duration_ms", result.getDurationMs()
            ), "因子进化完成"));
        } catch (Exception e) {
            log.error("因子进化失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "因子进化失败: " + e.getMessage()));
        }
    }

    /**
     * 多轮因子进化
     * POST /api/v1/ai-capability/factor-evolution/run-multi
     */
    @PostMapping("/factor-evolution/run-multi")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runMultiGenerationEvolution(
            @RequestBody Map<String, Object> body) {

        int maxGenerations = body.containsKey("max_generations")
                ? ((Number) body.get("max_generations")).intValue() : 5;

        FactorEvolutionConfig config = FactorEvolutionConfig.defaultConfig();
        if (body.containsKey("preset")) {
            String preset = (String) body.get("preset");
            config = switch (preset) {
                case "aggressive" -> FactorEvolutionConfig.aggressive();
                case "conservative" -> FactorEvolutionConfig.conservative();
                default -> config;
            };
        }

        try {
            EvolutionResult result = evolutionOrchestrator.runMultiGenerationEvolution(config, maxGenerations);
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "final_generation", result.getGeneration(),
                    "total_generated", result.getCandidatesGenerated(),
                    "total_promoted", result.getCandidatesPromoted(),
                    "converged", result.isConverged(),
                    "duration_ms", result.getDurationMs()
            ), "多轮进化完成"));
        } catch (Exception e) {
            log.error("多轮进化失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "多轮进化失败: " + e.getMessage()));
        }
    }

    /**
     * 获取因子进化状态
     * GET /api/v1/ai-capability/factor-evolution/status
     */
    @GetMapping("/factor-evolution/status")
    public ResponseEntity<ApiResponse<String>> getEvolutionStatus() {
        return ResponseEntity.ok(ApiResponse.ok(evolutionOrchestrator.getEvolutionStatusSummary()));
    }

    // ===== 策略质量评分 =====

    /**
     * 评估单个策略质量
     * POST /api/v1/ai-capability/benchmark/strategy
     */
    @PostMapping("/benchmark/strategy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> benchmarkStrategy(@RequestBody Map<String, Object> body) {
        String strategyId = (String) body.get("strategy_id");
        String stockCode = (String) body.get("stock_code");
        int days = body.containsKey("days") ? ((Number) body.get("days")).intValue() : 180;

        if (strategyId == null || stockCode == null) {
            return ResponseEntity.ok(ApiResponse.error("strategy_id 和 stock_code 不能为空"));
        }

        try {
            BenchmarkReport report = benchmarkSuite.evaluateStrategy(strategyId, stockCode, days);
            return ResponseEntity.ok(ApiResponse.ok(report.toMap()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "评估失败: " + e.getMessage()));
        }
    }

    /**
     * 对比多个策略
     * POST /api/v1/ai-capability/benchmark/compare
     */
    @PostMapping("/benchmark/compare")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compareStrategies(@RequestBody Map<String, Object> body) {
        String stockCode = (String) body.get("stock_code");
        Object strategiesObj = body.get("strategies");
        int days = body.containsKey("days") ? ((Number) body.get("days")).intValue() : 180;

        if (stockCode == null || strategiesObj == null) {
            return ResponseEntity.ok(ApiResponse.error("stock_code 和 strategies 不能为空"));
        }

        List<String> strategyIds;
        if (strategiesObj instanceof String s) {
            strategyIds = Arrays.stream(s.split(",")).map(String::trim).filter(str -> !str.isEmpty()).toList();
        } else if (strategiesObj instanceof List<?> list) {
            strategyIds = list.stream().map(String::valueOf).toList();
        } else {
            return ResponseEntity.ok(ApiResponse.error("strategies 格式错误"));
        }

        try {
            BenchmarkComparison comparison = benchmarkSuite.compareStrategies(strategyIds, stockCode, days);
            return ResponseEntity.ok(ApiResponse.ok(comparison.toMap()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "对比失败: " + e.getMessage()));
        }
    }

    /**
     * 全策略扫描
     * POST /api/v1/ai-capability/benchmark/scan-all
     */
    @PostMapping("/benchmark/scan-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scanAllStrategies(@RequestBody Map<String, Object> body) {
        String stockCode = (String) body.get("stock_code");
        int days = body.containsKey("days") ? ((Number) body.get("days")).intValue() : 180;

        if (stockCode == null) {
            return ResponseEntity.ok(ApiResponse.error("stock_code 不能为空"));
        }

        try {
            BenchmarkComparison comparison = benchmarkSuite.scanAllStrategies(stockCode, days);
            return ResponseEntity.ok(ApiResponse.ok(comparison.toMap()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "扫描失败: " + e.getMessage()));
        }
    }

    /**
     * 评估 LLM 分析报告质量
     * POST /api/v1/ai-capability/quality/assess
     */
    @PostMapping("/quality/assess")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assessAnalysisQuality(@RequestBody Map<String, Object> body) {
        String llmResponse = (String) body.get("llm_response");
        @SuppressWarnings("unchecked")
        Map<String, Object> contextData = (Map<String, Object>) body.get("context_data");

        if (llmResponse == null || llmResponse.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("llm_response 不能为空"));
        }

        try {
            LlmAnalysisQuality quality = benchmarkSuite.assessAnalysisQuality(llmResponse, contextData);
            return ResponseEntity.ok(ApiResponse.ok(quality.toMap()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "评估失败: " + e.getMessage()));
        }
    }
}
