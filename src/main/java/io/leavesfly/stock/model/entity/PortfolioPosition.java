package io.leavesfly.stock.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 投资组合持仓实体
 * 对应Python版本的 portfolio_repo.py
 */
@Entity
@Table(name = "portfolio_positions")
public class PortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 股票代码 */
    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    /** 股票名称 */
    @Column(name = "stock_name", length = 50)
    private String stockName;

    /** 市场 */
    @Column(name = "market", length = 10)
    private String market;

    /** 持仓数量(股) */
    @Column(name = "quantity")
    private Integer quantity;

    /** 成本价 */
    @Column(name = "cost_price")
    private Double costPrice;

    /** 当前价 */
    @Column(name = "current_price")
    private Double currentPrice;

    /** 盈亏金额 */
    @Column(name = "profit_loss")
    private Double profitLoss;

    /** 盈亏比例(%) */
    @Column(name = "profit_loss_pct")
    private Double profitLossPct;

    /** 持仓市值 */
    @Column(name = "market_value")
    private Double marketValue;

    /** 仓位占比(%) */
    @Column(name = "position_pct")
    private Double positionPct;

    /** 买入日期 */
    @Column(name = "buy_date")
    private LocalDateTime buyDate;

    /** 止损价 */
    @Column(name = "stop_loss_price")
    private Double stopLossPrice;

    /** 目标价 */
    @Column(name = "target_price")
    private Double targetPrice;

    /** 标签(逗号分隔) */
    @Column(name = "tags", length = 200)
    private String tags;

    /** 备注 */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Double getCostPrice() { return costPrice; }
    public void setCostPrice(Double costPrice) { this.costPrice = costPrice; }
    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }
    public Double getProfitLoss() { return profitLoss; }
    public void setProfitLoss(Double profitLoss) { this.profitLoss = profitLoss; }
    public Double getProfitLossPct() { return profitLossPct; }
    public void setProfitLossPct(Double profitLossPct) { this.profitLossPct = profitLossPct; }
    public Double getMarketValue() { return marketValue; }
    public void setMarketValue(Double marketValue) { this.marketValue = marketValue; }
    public Double getPositionPct() { return positionPct; }
    public void setPositionPct(Double positionPct) { this.positionPct = positionPct; }
    public LocalDateTime getBuyDate() { return buyDate; }
    public void setBuyDate(LocalDateTime buyDate) { this.buyDate = buyDate; }
    public Double getStopLossPrice() { return stopLossPrice; }
    public void setStopLossPrice(Double stopLossPrice) { this.stopLossPrice = stopLossPrice; }
    public Double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(Double targetPrice) { this.targetPrice = targetPrice; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
