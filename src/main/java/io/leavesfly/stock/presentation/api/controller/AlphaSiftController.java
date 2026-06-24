package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.application.service.AlphaSiftScreeningEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 智能选股API控制器 (对齐 dsa-web AlphaSift)
 */
@RestController
@RequestMapping("/api/v1/alphasift")
public class AlphaSiftController {

    private final AlphaSiftScreeningEngine screeningEngine;
    private final Map<String, Map<String, Object>> tasks = new ConcurrentHashMap<>();

    public AlphaSiftController(AlphaSiftScreeningEngine screeningEngine) {
        this.screeningEngine = screeningEngine;
    }

    /** 获取AlphaSift状态 */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of("enabled", true, "version", "1.0"));
    }

    /** 启用AlphaSift */
    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> enable() {
        return ResponseEntity.ok(Map.of("enabled", true));
    }

    /** 获取热点列表 */
    @GetMapping("/hotspots")
    public ResponseEntity<Map<String, Object>> getHotspots() {
        List<Map<String, Object>> hotspots = List.of(
            Map.of("id", 1, "name", "AI人工智能", "description", "大模型相关概念股", "count", 15),
            Map.of("id", 2, "name", "新能源", "description", "光伏/储能/锂电", "count", 22),
            Map.of("id", 3, "name", "半导体", "description", "芯片/设备/材料", "count", 18)
        );
        return ResponseEntity.ok(Map.of("items", hotspots));
    }

    /** 获取热点详情 */
    @GetMapping("/hotspots/{id}")
    public ResponseEntity<Map<String, Object>> getHotspotDetail(@PathVariable Integer id) {
        return ResponseEntity.ok(Map.of("id", id, "name", "热点板块", "stocks", List.of()));
    }

    /** 获取选股策略 */
    @GetMapping("/strategies")
    public ResponseEntity<Map<String, Object>> getStrategies() {
        List<Map<String, String>> strategies = List.of(
            Map.of("id", "dual_low", "name", "双低策略", "description", "低PB+低PE"),
            Map.of("id", "value_growth", "name", "价值成长", "description", "PEG<1的成长股"),
            Map.of("id", "momentum", "name", "动量策略", "description", "强势突破股"),
            Map.of("id", "dividend", "name", "高股息", "description", "股息率>3%")
        );
        return ResponseEntity.ok(Map.of("strategies", strategies));
    }

    /** 同步选股 */
    @PostMapping("/screen")
    public ResponseEntity<Map<String, Object>> screen(@RequestBody Map<String, Object> request) {
        String market = (String) request.getOrDefault("market", "cn");
        String strategy = (String) request.getOrDefault("strategy", "dual_low");
        int maxResults = request.get("max_results") != null ? ((Number) request.get("max_results")).intValue() : 10;
        List<Map<String, Object>> candidates = screeningEngine.screen(strategy, market, maxResults);
        return ResponseEntity.ok(Map.of("candidates", candidates, "total", candidates.size()));
    }

    /** 异步选股任务 */
    @PostMapping("/screen/task")
    public ResponseEntity<Map<String, Object>> startScreenTask(@RequestBody Map<String, Object> request) {
        String taskId = UUID.randomUUID().toString().substring(0, 12);
        String market = (String) request.getOrDefault("market", "cn");
        String strategy = (String) request.getOrDefault("strategy", "dual_low");
        int maxResults = request.get("max_results") != null ? ((Number) request.get("max_results")).intValue() : 10;

        Map<String, Object> task = new ConcurrentHashMap<>();
        task.put("task_id", taskId);
        task.put("status", "pending");
        task.put("progress", 0);
        task.put("market", market);
        task.put("strategy", strategy);
        tasks.put(taskId, task);

        CompletableFuture.runAsync(() -> {
            try {
                task.put("status", "running");
                task.put("progress", 10);
                Thread.sleep(500);
                task.put("progress", 30);
                Thread.sleep(500);
                task.put("progress", 60);

                // 执行实际选股逻辑
                List<Map<String, Object>> result = screeningEngine.screen(strategy, market, maxResults);

                task.put("progress", 100);
                task.put("status", "completed");
                task.put("result", result);
                task.put("total", result.size());
            } catch (Exception e) {
                task.put("status", "failed");
                task.put("error_message", e.getMessage());
            }
        });

        return ResponseEntity.ok(Map.of("task_id", taskId, "status", "accepted"));
    }

    /** 获取选股任务状态 */
    @GetMapping("/screen/task/{taskId}")
    public ResponseEntity<?> getScreenTask(@PathVariable String taskId) {
        Map<String, Object> task = tasks.get(taskId);
        if (task == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(task);
    }
}
