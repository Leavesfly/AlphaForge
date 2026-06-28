package io.leavesfly.alphaforge.domain.model.entity.watchlist;

import java.time.LocalDateTime;

/**
 * 自选股实体
 * 对应 watchlist 表
 */
public class WatchlistItem {

    /** 主键ID */
    private Long id;
    /** 股票代码 */
    private String stockCode;
    /** 股票名称 */
    private String stockName;
    /** 市场(A/US/HK) */
    private String market;
    /** 添加时间 */
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
