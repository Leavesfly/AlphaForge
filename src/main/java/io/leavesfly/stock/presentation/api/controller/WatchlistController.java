package io.leavesfly.stock.presentation.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 股票/自选股API控制器
 */
@RestController
@RequestMapping("/api/v1/stocks")
public class WatchlistController {

    /** 自选股列表(内存实现) */
    private final List<String> watchlist = new CopyOnWriteArrayList<>();

    public WatchlistController() {
    }

    /** 获取自选股列表 */
    @GetMapping("/watchlist")
    public ResponseEntity<Map<String, Object>> getWatchlist() {
        return ResponseEntity.ok(Map.of("stock_codes", new ArrayList<>(watchlist)));
    }

    /** 添加自选股 */
    @PostMapping("/watchlist/add")
    public ResponseEntity<Map<String, Object>> addToWatchlist(@RequestBody Map<String, String> request) {
        String code = request.get("stock_code");
        if (code != null && !code.isEmpty() && !watchlist.contains(code)) {
            watchlist.add(code);
        }
        return ResponseEntity.ok(Map.of("stock_codes", new ArrayList<>(watchlist)));
    }

    /** 移除自选股 */
    @PostMapping("/watchlist/remove")
    public ResponseEntity<Map<String, Object>> removeFromWatchlist(@RequestBody Map<String, String> request) {
        String code = request.get("stock_code");
        if (code != null) watchlist.remove(code);
        return ResponseEntity.ok(Map.of("stock_codes", new ArrayList<>(watchlist)));
    }

    /** 股票自动补全搜索 */
    @GetMapping("/autocomplete")
    public ResponseEntity<List<Map<String, String>>> autocomplete(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        // 简化实现: 返回输入作为候选
        List<Map<String, String>> results = List.of(
            Map.of("code", q.trim(), "name", "")
        );
        return ResponseEntity.ok(results);
    }
}
