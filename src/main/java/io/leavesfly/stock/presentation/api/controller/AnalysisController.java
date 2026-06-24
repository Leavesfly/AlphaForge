package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.stock.domain.model.entity.AnalysisReport;
import io.leavesfly.stock.application.service.AnalysisHistoryService;
import io.leavesfly.stock.application.service.TaskQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分析API控制器
 * 对应Python版本的 api/v1/endpoints/analysis.py
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    private final StockAnalysisPipeline pipeline;
    private final AnalysisHistoryService historyService;
    private final TaskQueueService taskQueueService;

    /** 任务状态存储 */
    private final Map<String, Map<String, Object>> taskStore = new ConcurrentHashMap<>();

    public AnalysisController(StockAnalysisPipeline pipeline, AnalysisHistoryService historyService,
                              TaskQueueService taskQueueService) {
        this.pipeline = pipeline;
        this.historyService = historyService;
        this.taskQueueService = taskQueueService;
    }

    /**
     * 统一分析入口 (对齐 dsa-web)
     * POST /api/v1/analysis/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, Object> request) {
        String stockCode = (String) request.getOrDefault("stock_code", "");
        @SuppressWarnings("unchecked")
        List<String> stockCodes = (List<String>) request.get("stock_codes");
        boolean asyncMode = Boolean.TRUE.equals(request.get("async_mode"));
        String analysisPhase = (String) request.getOrDefault("analysis_phase", "auto");
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) request.get("skills");

        String code = stockCode;
        if ((code == null || code.isEmpty()) && stockCodes != null && !stockCodes.isEmpty()) {
            code = String.join(",", stockCodes);
        }
        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供stock_code参数"));
        }

        if (asyncMode) {
            String taskId = UUID.randomUUID().toString().substring(0, 12);
            Map<String, Object> task = new ConcurrentHashMap<>();
            task.put("task_id", taskId);
            task.put("stock_code", code);
            task.put("status", "pending");
            task.put("progress", 0);
            task.put("created_at", new Date().toString());
            taskStore.put(taskId, task);

            final String finalCode = code;
            CompletableFuture.runAsync(() -> {
                try {
                    task.put("status", "running");
                    task.put("progress", 10);
                    AnalysisReport report = pipeline.analyzeSingleStock(finalCode, false, false);
                    task.put("status", "completed");
                    task.put("progress", 100);
                    if (report != null) task.put("result", report);
                } catch (Exception e) {
                    task.put("status", "failed");
                    task.put("error_message", e.getMessage());
                }
            });

            return ResponseEntity.status(202).body(Map.of(
                    "status", "accepted",
                    "task_id", taskId,
                    "stock_code", code));
        } else {
            try {
                AnalysisReport report = pipeline.analyzeSingleStock(code, false, false);
                if (report != null) {
                    return ResponseEntity.ok(Map.of("status", "completed", "report", report));
                }
                return ResponseEntity.internalServerError().body(Map.of("error", "分析失败"));
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
            }
        }
    }

    /**
     * 大盘复盘
     * POST /api/v1/analysis/market-review
     */
    @PostMapping("/market-review")
    public ResponseEntity<Map<String, Object>> marketReview(@RequestBody(required = false) Map<String, Object> request) {
        String taskId = UUID.randomUUID().toString().substring(0, 12);
        Map<String, Object> task = new ConcurrentHashMap<>();
        task.put("task_id", taskId);
        task.put("task_type", "market_review");
        task.put("status", "pending");
        task.put("progress", 0);
        task.put("created_at", new Date().toString());
        taskStore.put(taskId, task);

        CompletableFuture.runAsync(() -> {
            try {
                task.put("status", "running");
                pipeline.runMarketReview();
                task.put("status", "completed");
                task.put("progress", 100);
            } catch (Exception e) {
                task.put("status", "failed");
                task.put("error_message", e.getMessage());
            }
        });

        return ResponseEntity.status(202).body(Map.of("status", "accepted", "task_id", taskId));
    }

    /**
     * 获取任务状态
     * GET /api/v1/analysis/status/{taskId}
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId) {
        Map<String, Object> task = taskStore.get(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    /**
     * 获取任务列表
     * GET /api/v1/analysis/tasks
     */
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit) {
        List<Map<String, Object>> tasks = new ArrayList<>(taskStore.values());
        if (status != null && !status.isEmpty()) {
            tasks.removeIf(t -> !status.equals(t.get("status")));
        }
        tasks.sort((a, b) -> String.valueOf(b.get("created_at")).compareTo(String.valueOf(a.get("created_at"))));
        if (tasks.size() > limit) tasks = tasks.subList(0, limit);
        return ResponseEntity.ok(Map.of("tasks", tasks, "total", taskStore.size()));
    }

    /**
     * 获取任务运行流程
     * GET /api/v1/analysis/tasks/{taskId}/flow
     */
    @GetMapping("/tasks/{taskId}/flow")
    public ResponseEntity<?> getTaskFlow(@PathVariable String taskId) {
        Map<String, Object> task = taskStore.get(taskId);
        if (task == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("task_id", taskId, "steps", List.of(), "status", task.getOrDefault("status", "unknown")));
    }

    /**
     * SSE任务实时更新流
     * GET /api/v1/analysis/tasks/stream
     */
    @GetMapping(value = "/tasks/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter taskStream() {
        SseEmitter emitter = new SseEmitter(300_000L);
        // 简单实现：定时发送当前任务状态
        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < 60; i++) {
                    Thread.sleep(5000);
                    emitter.send(SseEmitter.event().name("tasks").data(Map.of("tasks", new ArrayList<>(taskStore.values()))));
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    // ========== 旧端点保持兼容 ==========

    /** POST /api/v1/analysis/run */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runAnalysis(@RequestBody Map<String, Object> request) {
        String stocks = (String) request.getOrDefault("stocks", "");
        if (stocks.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "请提供stocks参数"));
        CompletableFuture.runAsync(() -> pipeline.runFullAnalysis(stocks, false, false));
        return ResponseEntity.ok(Map.of("status", "accepted", "stocks", stocks));
    }

    /** POST /api/v1/analysis/single */
    @PostMapping("/single")
    public ResponseEntity<?> analyzeSingle(@RequestBody Map<String, Object> request) {
        String stockCode = (String) request.getOrDefault("stock_code", "");
        if (stockCode.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "请提供stock_code参数"));
        try {
            AnalysisReport report = pipeline.analyzeSingleStock(stockCode, false, false);
            return report != null ? ResponseEntity.ok(report) : ResponseEntity.internalServerError().body(Map.of("error", "分析失败"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** GET /api/v1/analysis/history */
    @GetMapping("/history")
    public ResponseEntity<List<AnalysisReport>> getHistory(
            @RequestParam(required = false) String stockCode,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(historyService.getRecentReports(stockCode, limit));
    }

    /** GET /api/v1/analysis/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<?> getReport(@PathVariable Long id) {
        return historyService.getReportById(id)
                .map(report -> ResponseEntity.ok((Object) report))
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /api/v1/analysis/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteReport(@PathVariable Long id) {
        historyService.deleteReport(id);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }

}
