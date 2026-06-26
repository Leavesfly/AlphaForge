package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.service.AlphaSiftScreeningEngine;
import io.leavesfly.stock.domain.model.entity.AlphaSiftTask;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能选股 API
 */
@RestController
@RequestMapping("/api/v1/screening")
public class ScreeningController {

    private final AlphaSiftScreeningEngine screeningEngine;

    public ScreeningController(AlphaSiftScreeningEngine screeningEngine) {
        this.screeningEngine = screeningEngine;
    }

    /** 执行智能选股（异步，返回 task_id） */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestBody Map<String, Object> body) {
        String strategy = (String) body.getOrDefault("strategy", "value_growth");
        String market = (String) body.getOrDefault("market", "A");
        int maxResults = body.containsKey("max_results") ? ((Number) body.get("max_results")).intValue() : 10;
        boolean sync = Boolean.TRUE.equals(body.get("sync"));

        if (sync) {
            List<Map<String, Object>> results = screeningEngine.screen(strategy, market, maxResults);
            return ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "strategy", strategy,
                    "market", market,
                    "total_scanned", results.size(),
                    "results", results));
        }

        AlphaSiftTask task = screeningEngine.submitScreening(strategy, market, maxResults);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("task_id", task.getTaskId());
        resp.put("status", task.getStatus());
        resp.put("strategy", strategy);
        resp.put("market", market);
        resp.put("message", "选股任务已提交");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTask(@PathVariable String taskId) {
        Map<String, Object> task = screeningEngine.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }
}
