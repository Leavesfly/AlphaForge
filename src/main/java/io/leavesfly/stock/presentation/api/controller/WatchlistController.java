package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.domain.model.entity.WatchlistItem;
import io.leavesfly.stock.infrastructure.persistence.WatchlistRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自选股API控制器
 * 使用 WatchlistRepository 持久化
 */
@RestController
@RequestMapping("/api/v1/stocks")
public class WatchlistController {

    private final WatchlistRepository watchlistRepo;

    public WatchlistController(WatchlistRepository watchlistRepo) {
        this.watchlistRepo = watchlistRepo;
    }

    /** 获取自选股列表 */
    @GetMapping("/watchlist")
    public ResponseEntity<Map<String, Object>> getWatchlist() {
        List<WatchlistItem> items = watchlistRepo.findAll();
        List<Map<String, Object>> list = items.stream().map(this::itemToMap).collect(Collectors.toList());
        List<String> codes = items.stream().map(WatchlistItem::getStockCode).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("stock_codes", codes, "items", list, "total", items.size()));
    }

    /** 添加自选股 */
    @PostMapping("/watchlist/add")
    public ResponseEntity<Map<String, Object>> addToWatchlist(@RequestBody Map<String, String> request) {
        String code = request.get("stock_code");
        String name = request.getOrDefault("stock_name", "");
        String market = request.getOrDefault("market", "");
        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock_code不能为空"));
        }
        if (!watchlistRepo.existsByStockCode(code)) {
            WatchlistItem item = new WatchlistItem();
            item.setStockCode(code);
            item.setStockName(name);
            item.setMarket(market);
            item.setAddedAt(LocalDateTime.now());
            watchlistRepo.insert(item);
        }
        List<String> codes = watchlistRepo.findAll().stream().map(WatchlistItem::getStockCode).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("stock_codes", codes, "total", codes.size()));
    }

    /** 移除自选股 */
    @PostMapping("/watchlist/remove")
    public ResponseEntity<Map<String, Object>> removeFromWatchlist(@RequestBody Map<String, String> request) {
        String code = request.get("stock_code");
        if (code != null && !code.isEmpty()) {
            watchlistRepo.deleteByStockCode(code);
        }
        List<String> codes = watchlistRepo.findAll().stream().map(WatchlistItem::getStockCode).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("stock_codes", codes, "total", codes.size()));
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

    private Map<String, Object> itemToMap(WatchlistItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stock_code", item.getStockCode());
        map.put("stock_name", item.getStockName());
        map.put("market", item.getMarket());
        map.put("added_at", item.getAddedAt() != null ? item.getAddedAt().toString() : null);
        return map;
    }
}
