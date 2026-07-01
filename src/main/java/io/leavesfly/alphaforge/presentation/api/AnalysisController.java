package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.service.report.AnalysisHistoryService;
import io.leavesfly.alphaforge.application.service.market.MarketAnalysisService;
import io.leavesfly.alphaforge.application.service.task.TaskService;
import io.leavesfly.alphaforge.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 分析 API
 */
@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final StockAnalysisPipeline pipeline;
    private final AnalysisHistoryService historyService;
    private final MarketAnalysisService marketService;
    private final TaskService taskService;

    public AnalysisController(StockAnalysisPipeline pipeline, AnalysisHistoryService historyService,
                             MarketAnalysisService marketService,
                             TaskService taskService) {
        this.pipeline = pipeline;
        this.historyService = historyService;
        this.marketService = marketService;
        this.taskService = taskService;
    }

    /** 触发股票分析（异步，返回 task_id） */
    @PostMapping("/analysis/run")
    public ResponseEntity<Map<String, Object>> runAnalysis(@RequestBody Map<String, Object> body) {
        String stockCode = (String) body.get("stock_code");
        if (stockCode == null || stockCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock_code is required"));
        }
        boolean dryRun = Boolean.TRUE.equals(body.get("dry_run"));
        try {
            AnalysisTask task = taskService.submitAnalysis(stockCode.trim(), dryRun);
            return ResponseEntity.ok(Map.of(
                    "task_id", task.getTaskId(),
                    "status", task.getStatus(),
                    "stock_code", stockCode,
                    "message", "分析任务已提交"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 查询分析任务状态 */
    @GetMapping("/analysis/tasks/{taskId}")
    public ResponseEntity<?> getAnalysisTask(@PathVariable String taskId) {
        Map<String, Object> task = taskService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    @GetMapping("/history")
    public ResponseEntity<List<AnalysisReport>> history(
            @RequestParam(required = false) String stockCode,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(historyService.getRecentReports(stockCode, limit));
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<?> historyDetail(@PathVariable Long id) {
        return historyService.getReportById(id)
                .map(r -> ResponseEntity.ok((Object) r))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stocks/{code}/quote")
    public ResponseEntity<Map<String, Object>> quote(@PathVariable String code) {
        Map<String, Object> quote = marketService.getQuote(code);
        if (quote == null || quote.isEmpty()) {
            quote = Map.of("stock_code", code, "status", "no_data");
        }
        return ResponseEntity.ok(quote);
    }

    @GetMapping("/market/overview")
    public ResponseEntity<Map<String, Object>> marketOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", marketService.getMarketOverview());
        result.put("light", marketService.getMarketLight());
        return ResponseEntity.ok(result);
    }
}
