package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.service.AlphaSiftScreeningEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /** 执行智能选股 */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestBody Map<String, Object> body) {
        String strategy = (String) body.getOrDefault("strategy", "value_growth");
        String market = (String) body.getOrDefault("market", "A");
        int maxResults = body.containsKey("max_results") ? ((Number) body.get("max_results")).intValue() : 10;

        List<Map<String, Object>> results = screeningEngine.screen(strategy, market, maxResults);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "strategy", strategy,
                "market", market,
                "total_scanned", 20,
                "results", results));
    }
}
