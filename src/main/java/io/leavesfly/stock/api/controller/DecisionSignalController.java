package io.leavesfly.stock.api.controller;

import io.leavesfly.stock.model.entity.DecisionSignal;
import io.leavesfly.stock.service.DecisionSignalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 决策信号API控制器
 * 对应Python版本的 api/v1/endpoints/decision_signals.py
 */
@RestController
@RequestMapping("/api/v1/decision-signals")
public class DecisionSignalController {

    private final DecisionSignalService signalService;

    public DecisionSignalController(DecisionSignalService signalService) {
        this.signalService = signalService;
    }

    @GetMapping
    public ResponseEntity<List<DecisionSignal>> getSignals(@RequestParam(required = false) String stockCode,
                                                           @RequestParam(required = false) String status) {
        if (stockCode != null && !stockCode.isEmpty()) return ResponseEntity.ok(signalService.getSignalsByStock(stockCode));
        if ("active".equals(status)) return ResponseEntity.ok(signalService.getActiveSignals());
        return ResponseEntity.ok(signalService.getRecentSignals());
    }

    @PostMapping
    public ResponseEntity<DecisionSignal> createSignal(@RequestBody DecisionSignal signal) {
        return ResponseEntity.ok(signalService.createSignal(signal));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<?> executeSignal(@PathVariable Long id) {
        DecisionSignal s = signalService.executeSignal(id);
        return s != null ? ResponseEntity.ok(s) : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelSignal(@PathVariable Long id) {
        DecisionSignal s = signalService.cancelSignal(id);
        return s != null ? ResponseEntity.ok(s) : ResponseEntity.notFound().build();
    }
}
