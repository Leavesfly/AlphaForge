package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.service.DecisionSignalOutcomeService;
import io.leavesfly.stock.application.service.DecisionSignalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 决策信号 API
 */
@RestController
@RequestMapping("/api/v1/decision-signals")
public class SignalController {

    private final DecisionSignalService signalService;
    private final DecisionSignalOutcomeService outcomeService;

    public SignalController(DecisionSignalService signalService,
                            DecisionSignalOutcomeService outcomeService) {
        this.signalService = signalService;
        this.outcomeService = outcomeService;
    }

    /** 决策信号列表 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> signals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(signalService.listSignals(
                null, stockCode, action, null, null, status, null, null, page, pageSize));
    }

    /** 信号详情 */
    @GetMapping("/{id}")
    public ResponseEntity<?> signalDetail(@PathVariable Long id) {
        return signalService.getSignalById(id)
                .map(s -> ResponseEntity.ok((Object) s))
                .orElse(ResponseEntity.notFound().build());
    }

    /** 信号反馈 */
    @PostMapping("/{id}/feedback")
    public ResponseEntity<Map<String, Object>> signalFeedback(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("feedback_value", body.getOrDefault("feedback", body.get("feedback_value")));
        request.put("reason_code", body.get("reason_code"));
        request.put("note", body.get("note"));
        request.put("source", body.getOrDefault("source", "web"));
        Map<String, Object> saved = outcomeService.saveFeedback(id, request);
        signalService.updateSignalStatus(id, "verified");
        return ResponseEntity.ok(saved);
    }
}
