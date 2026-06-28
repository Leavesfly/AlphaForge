package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.service.alert.AlertService;
import io.leavesfly.stock.domain.model.entity.alert.AlertRule;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 告警管理 API
 * 告警规则CRUD、触发记录、通知日志
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** 获取活跃告警规则列表 */
    @GetMapping
    public ResponseEntity<List<AlertRule>> listAlerts() {
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    /** 创建告警规则 */
    @PostMapping
    public ResponseEntity<AlertRule> createAlert(@RequestBody AlertRule rule) {
        return ResponseEntity.ok(alertService.createAlert(rule));
    }

    /** 更新告警规则 */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAlert(@PathVariable Long id, @RequestBody AlertRule rule) {
        AlertRule updated = alertService.updateAlert(id, rule);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    /** 删除告警规则 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAlert(@PathVariable Long id) {
        alertService.deleteAlert(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** 启用告警规则 */
    @PostMapping("/{id}/enable")
    public ResponseEntity<Map<String, Object>> enableAlert(@PathVariable Long id) {
        alertService.enableAlert(id);
        return ResponseEntity.ok(Map.of("status", "ok", "enabled", true));
    }

    /** 禁用告警规则 */
    @PostMapping("/{id}/disable")
    public ResponseEntity<Map<String, Object>> disableAlert(@PathVariable Long id) {
        alertService.disableAlert(id);
        return ResponseEntity.ok(Map.of("status", "ok", "enabled", false));
    }

    /** 测试告警规则 */
    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testAlert(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.testAlert(id));
    }

    /** 获取指定股票的告警 */
    @GetMapping("/stock/{stockCode}")
    public ResponseEntity<List<AlertRule>> alertsByStock(@PathVariable String stockCode) {
        return ResponseEntity.ok(alertService.getAlertsByStock(stockCode));
    }

    /** 触发记录 */
    @GetMapping("/triggers")
    public ResponseEntity<List<Map<String, Object>>> triggers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(alertService.getTriggerHistory(page, pageSize));
    }

    /** 通知日志 */
    @GetMapping("/notifications")
    public ResponseEntity<List<Map<String, Object>>> notifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(alertService.getNotificationHistory(page, pageSize));
    }
}
