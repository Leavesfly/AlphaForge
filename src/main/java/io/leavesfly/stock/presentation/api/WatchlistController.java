package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.service.watchlist.WatchlistService;
import io.leavesfly.stock.domain.model.entity.watchlist.WatchlistItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 自选股 API
 */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public ResponseEntity<List<WatchlistItem>> list() {
        return ResponseEntity.ok(watchlistService.listAll());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> add(@RequestBody Map<String, Object> body) {
        try {
            WatchlistItem item = watchlistService.add(
                    (String) body.get("stock_code"),
                    (String) body.get("stock_name"),
                    (String) body.get("market"));
            return ResponseEntity.ok(Map.of("status", "ok", "stock_code", item.getStockCode()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Map<String, Object>> remove(@PathVariable String code) {
        watchlistService.remove(code);
        return ResponseEntity.ok(Map.of("status", "ok", "removed", code));
    }
}
