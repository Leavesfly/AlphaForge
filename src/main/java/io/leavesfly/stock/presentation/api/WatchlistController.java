package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.domain.model.entity.WatchlistItem;
import io.leavesfly.stock.infrastructure.persistence.WatchlistRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 自选股 API
 */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistRepository watchlistRepo;

    public WatchlistController(WatchlistRepository watchlistRepo) {
        this.watchlistRepo = watchlistRepo;
    }

    /** 获取自选股列表 */
    @GetMapping
    public ResponseEntity<List<WatchlistItem>> list() {
        return ResponseEntity.ok(watchlistRepo.findAll());
    }

    /** 添加自选股 */
    @PostMapping
    public ResponseEntity<Map<String, Object>> add(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("stock_code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock_code is required"));
        }
        WatchlistItem item = new WatchlistItem();
        item.setStockCode(code);
        item.setStockName((String) body.getOrDefault("stock_name", code));
        item.setMarket((String) body.getOrDefault("market", "A"));
        item.setAddedAt(LocalDateTime.now());
        watchlistRepo.insert(item);
        return ResponseEntity.ok(Map.of("status", "ok", "stock_code", code));
    }

    /** 删除自选股 */
    @DeleteMapping("/{code}")
    public ResponseEntity<Map<String, Object>> remove(@PathVariable String code) {
        watchlistRepo.deleteByStockCode(code);
        return ResponseEntity.ok(Map.of("status", "ok", "removed", code));
    }
}
