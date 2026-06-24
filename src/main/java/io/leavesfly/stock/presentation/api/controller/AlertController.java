package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.domain.model.entity.AlertRule;
import io.leavesfly.stock.application.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 告警API控制器 (对齐 dsa-web)
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** 获取告警规则列表 */
    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> listRules(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false, name = "alert_type") String alertType,
            @RequestParam(required = false, name = "target_scope") String targetScope,
            @RequestParam(required = false) String target,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20", name = "page_size") int pageSize) {
        List<AlertRule> all = alertService.getActiveAlerts();
        // 简单过滤
        if (enabled != null) {
            boolean en = enabled;
            all.removeIf(r -> !en == Boolean.TRUE.equals(r.getEnabled()));
        }
        int total = all.size();
        int start = (page - 1) * pageSize;
        List<AlertRule> items = start < total ? all.subList(start, Math.min(start + pageSize, total)) : List.of();
        return ResponseEntity.ok(Map.of("items", items, "total", total, "page", page));
    }

    /** 创建告警规则 */
    @PostMapping("/rules")
    public ResponseEntity<AlertRule> createRule(@RequestBody AlertRule rule) {
        return ResponseEntity.ok(alertService.createAlert(rule));
    }

    /** 删除告警规则 */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable Long id) {
        alertService.deleteAlert(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    /** 启用规则 */
    @PostMapping("/rules/{id}/enable")
    public ResponseEntity<Map<String, Object>> enableRule(@PathVariable Long id) {
        alertService.enableAlert(id);
        return ResponseEntity.ok(Map.of("status", "enabled", "id", id, "enabled", true));
    }

    /** 禁用规则 */
    @PostMapping("/rules/{id}/disable")
    public ResponseEntity<Map<String, Object>> disableRule(@PathVariable Long id) {
        alertService.disableAlert(id);
        return ResponseEntity.ok(Map.of("status", "disabled", "id", id, "enabled", false));
    }

    /** 测试规则 */
    @PostMapping("/rules/{id}/test")
    public ResponseEntity<Map<String, Object>> testRule(@PathVariable Long id) {
        Map<String, Object> result = alertService.testAlert(id);
        return ResponseEntity.ok(result);
    }

    /** 获取触发历史 */
    @GetMapping("/triggers")
    public ResponseEntity<Map<String, Object>> listTriggers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20", name = "page_size") int pageSize) {
        List<Map<String, Object>> triggers = alertService.getTriggerHistory(page, pageSize);
        return ResponseEntity.ok(Map.of("items", triggers, "total", triggers.size(), "page", page));
    }

    /** 获取通知日志 */
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> listNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20", name = "page_size") int pageSize) {
        List<Map<String, Object>> notifications = alertService.getNotificationHistory(page, pageSize);
        return ResponseEntity.ok(Map.of("items", notifications, "total", notifications.size(), "page", page));
    }

    // 保持旧端点兼容
    @GetMapping
    public ResponseEntity<List<AlertRule>> getAlerts(@RequestParam(required = false) String stockCode) {
        if (stockCode != null && !stockCode.isEmpty()) {
            return ResponseEntity.ok(alertService.getAlertsByStock(stockCode));
        }
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    @PostMapping
    public ResponseEntity<AlertRule> createAlert(@RequestBody AlertRule rule) {
        return ResponseEntity.ok(alertService.createAlert(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAlert(@PathVariable Long id, @RequestBody AlertRule rule) {
        AlertRule updated = alertService.updateAlert(id, rule);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAlert(@PathVariable Long id) {
        alertService.deleteAlert(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
