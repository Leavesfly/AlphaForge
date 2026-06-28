package io.leavesfly.alphaforge.application.service.watchlist;

import io.leavesfly.alphaforge.domain.model.entity.watchlist.WatchlistItem;
import io.leavesfly.alphaforge.infrastructure.persistence.watchlist.WatchlistRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 自选股应用服务 — 封装校验与持久化。
 */
@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepo;

    public WatchlistService(WatchlistRepository watchlistRepo) {
        this.watchlistRepo = watchlistRepo;
    }

    public List<WatchlistItem> listAll() {
        return watchlistRepo.findAll();
    }

    public WatchlistItem add(String stockCode, String stockName, String market) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stock_code is required");
        }
        if (watchlistRepo.existsByStockCode(stockCode)) {
            return watchlistRepo.findByStockCode(stockCode);
        }
        WatchlistItem item = new WatchlistItem();
        item.setStockCode(stockCode.trim());
        item.setStockName(stockName != null && !stockName.isBlank() ? stockName : stockCode);
        item.setMarket(market != null ? market : "A");
        item.setAddedAt(LocalDateTime.now());
        watchlistRepo.insert(item);
        return item;
    }

    public void remove(String stockCode) {
        watchlistRepo.deleteByStockCode(stockCode);
    }

    public List<Map<String, String>> asStockPool() {
        return watchlistRepo.findAll().stream()
                .map(item -> Map.of(
                        "code", item.getStockCode(),
                        "name", item.getStockName() != null ? item.getStockName() : item.getStockCode(),
                        "market", item.getMarket() != null ? item.getMarket() : "A"))
                .toList();
    }
}
