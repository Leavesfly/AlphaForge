package io.leavesfly.stock.api.controller;

import io.leavesfly.stock.model.entity.AlertRule;
import io.leavesfly.stock.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 告警API控制器
 * 对应Python版本的 api/v1/endpoints/alerts.py
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

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
