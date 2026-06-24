package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.stock.domain.model.entity.AnalysisReport;
import io.leavesfly.stock.domain.model.entity.AnalysisTask;
import io.leavesfly.stock.application.service.AnalysisHistoryService;
import io.leavesfly.stock.application.service.TaskQueueService;
import io.leavesfly.stock.infrastructure.persistence.AnalysisTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 分析API控制器
 * 对应Python版本的 api/v1/endpoints/analysis.py
 * 使用 AnalysisTaskRepository 持久化任务状态
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    private final StockAnalysisPipeline pipeline;
    private final AnalysisHistoryService historyService;
    private final TaskQueueService taskQueueService;
    private final AnalysisTaskRepository taskRepository;

    public AnalysisController(StockAnalysisPipeline pipeline, AnalysisHistoryService historyService,
                              TaskQueueService taskQueueService, AnalysisTaskRepository taskRepository) {
        this.pipeline = pipeline;
        this.historyService = historyService;
        this.taskQueueService = taskQueueService;
        this.taskRepository = taskRepository;
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
        String reportLanguage = (String) request.getOrDefault("report_language", "zh");
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
            // 持久化任务记录
            AnalysisTask task = new AnalysisTask();
            task.setTaskId(taskId);
            task.setStockCode(code);
            task.setStockCodes(stockCodes != null ? String.join(",", stockCodes) : code);
            task.setTaskType("analysis");
            task.setStatus("pending");
            task.setProgress(0);
            task.setAnalysisPhase(analysisPhase);
            task.setReportLanguage(reportLanguage);
            task.setSkills(skills != null ? String.join(",", skills) : null);
            task.setCreatedAt(LocalDateTime.now());
            taskRepository.save(task);

            final String finalCode = code;
            CompletableFuture.runAsync(() -> {
                try {
                    taskRepository.updateStatus(taskId, "running", 10, "开始分析...");
                    AnalysisReport report = pipeline.analyzeSingleStock(finalCode, false, false);
                    taskRepository.updateCompleted(taskId, "completed", 100,
                            report != null ? String.valueOf(report.getId()) : null, null, LocalDateTime.now());
                } catch (Exception e) {
                    taskRepository.updateCompleted(taskId, "failed", 0, null, e.getMessage(), LocalDateTime.now());
                }
            });

            return ResponseEntity.status(202).body(Map.of(
                    "status", "accepted", "task_id", taskId, "stock_code", code));
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
        AnalysisTask task = new AnalysisTask();
        task.setTaskId(taskId);
        task.setTaskType("market_review");
        task.setStatus("pending");
        task.setProgress(0);
        task.setCreatedAt(LocalDateTime.now());
        taskRepository.save(task);

        CompletableFuture.runAsync(() -> {
            try {
                taskRepository.updateStatus(taskId, "running", 10, "开始大盘复盘...");
                pipeline.runMarketReview();
                taskRepository.updateCompleted(taskId, "completed", 100, null, null, LocalDateTime.now());
            } catch (Exception e) {
                taskRepository.updateCompleted(taskId, "failed", 0, null, e.getMessage(), LocalDateTime.now());
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
        AnalysisTask task = taskRepository.findByTaskId(taskId);
        if (task == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(taskToMap(task));
    }

    /**
     * 获取任务列表
     * GET /api/v1/analysis/tasks
     */
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit) {
        List<AnalysisTask> tasks;
        if (status != null && !status.isEmpty()) {
            tasks = taskRepository.findByStatus(status);
        } else {
            tasks = taskRepository.findRecent(limit);
        }
        List<Map<String, Object>> taskList = new ArrayList<>();
        for (AnalysisTask t : tasks) {
            taskList.add(taskToMap(t));
        }
        return ResponseEntity.ok(Map.of("tasks", taskList, "total", taskRepository.count()));
    }

    /**
     * 获取任务运行流程
     * GET /api/v1/analysis/tasks/{taskId}/flow
     */
    @GetMapping("/tasks/{taskId}/flow")
    public ResponseEntity<?> getTaskFlow(@PathVariable String taskId) {
        AnalysisTask task = taskRepository.findByTaskId(taskId);
        if (task == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("task_id", taskId, "steps", List.of(), "status", task.getStatus()));
    }

    /**
     * SSE任务实时更新流
     * GET /api/v1/analysis/tasks/stream
     */
    @GetMapping(value = "/tasks/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter taskStream() {
        SseEmitter emitter = new SseEmitter(300_000L);
        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < 60; i++) {
                    Thread.sleep(5000);
                    List<AnalysisTask> running = taskRepository.findByStatus("running");
                    List<Map<String, Object>> data = new ArrayList<>();
                    for (AnalysisTask t : running) data.add(taskToMap(t));
                    emitter.send(SseEmitter.event().name("tasks").data(Map.of("tasks", data)));
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

    /** 任务实体转Map */
    private Map<String, Object> taskToMap(AnalysisTask task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("task_id", task.getTaskId());
        map.put("stock_code", task.getStockCode());
        map.put("task_type", task.getTaskType());
        map.put("status", task.getStatus());
        map.put("progress", task.getProgress());
        map.put("message", task.getMessage());
        map.put("result", task.getResult());
        map.put("error_message", task.getErrorMessage());
        map.put("created_at", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
        map.put("started_at", task.getStartedAt() != null ? task.getStartedAt().toString() : null);
        map.put("completed_at", task.getCompletedAt() != null ? task.getCompletedAt().toString() : null);
        return map;
    }
}
