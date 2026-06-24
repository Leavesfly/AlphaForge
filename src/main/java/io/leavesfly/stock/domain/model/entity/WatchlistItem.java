package io.leavesfly.stock.domain.model.entity;

import java.time.LocalDateTime;

/**
 * 自选股实体
 * 对应 watchlist 表
 */
public class WatchlistItem {

    private Long id;
    private String stockCode;
    private String stockName;
    private String market;
    private LocalDateTime addedAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}
