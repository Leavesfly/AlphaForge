package io.leavesfly.alphaforge.application.service.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisTask;
import io.leavesfly.alphaforge.infrastructure.persistence.analysis.AnalysisTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 异步分析任务服务 — 对接 analysis_tasks 表。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final AnalysisTaskRepository taskRepo;
    private final StockAnalysisPipeline pipeline;
    private final ObjectMapper objectMapper;

    public TaskService(AnalysisTaskRepository taskRepo,
                       StockAnalysisPipeline pipeline,
                       ObjectMapper objectMapper) {
        this.taskRepo = taskRepo;
        this.pipeline = pipeline;
        this.objectMapper = objectMapper;
    }

    public AnalysisTask submitAnalysis(String stockCode, boolean dryRun) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stock_code is required");
        }
        AnalysisTask task = new AnalysisTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setStockCode(stockCode.trim());
        task.setTaskType("analysis");
        task.setStatus("pending");
        task.setProgress(0);
        task.setMessage("任务已创建");
        task.setCreatedAt(LocalDateTime.now());
        taskRepo.save(task);

        CompletableFuture.runAsync(() -> executeAnalysis(task.getTaskId(), stockCode.trim(), dryRun));
        return task;
    }

    public Map<String, Object> getTask(String taskId) {
        AnalysisTask task = taskRepo.findByTaskId(taskId);
        if (task == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task_id", task.getTaskId());
        m.put("stock_code", task.getStockCode());
        m.put("status", task.getStatus());
        m.put("progress", task.getProgress());
        m.put("message", task.getMessage());
        m.put("result", task.getResult());
        m.put("error_message", task.getErrorMessage());
        m.put("created_at", task.getCreatedAt());
        m.put("started_at", task.getStartedAt());
        m.put("completed_at", task.getCompletedAt());
        return m;
    }

    private void executeAnalysis(String taskId, String stockCode, boolean dryRun) {
        try {
            taskRepo.updateStatus(taskId, "running", 10, "开始分析");
            pipeline.setProgressCallback((progress, message) ->
                    taskRepo.updateStatus(taskId, "running", Math.min(99, progress), message));

            AnalysisReport report = pipeline.analyzeSingleStock(stockCode, dryRun, false);
            pipeline.setProgressCallback(null);

            Map<String, Object> summary = new LinkedHashMap<>();
            if (report != null) {
                summary.put("report_id", report.getId());
                summary.put("stock_code", report.getStockCode());
                summary.put("signal", report.getSignal());
                summary.put("score", report.getTotalScore());
            }
            taskRepo.updateCompleted(taskId, "completed", 100,
                    objectMapper.writeValueAsString(summary), null, LocalDateTime.now());
        } catch (Exception e) {
            log.error("分析任务失败: {} - {}", taskId, e.getMessage());
            pipeline.setProgressCallback(null);
            taskRepo.updateCompleted(taskId, "failed", 100, null, e.getMessage(), LocalDateTime.now());
        }
    }
}
