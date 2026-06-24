package io.leavesfly.stock.domain.model.entity;

import java.time.LocalDateTime;

/**
 * 投资组合持仓实体
 * 对应Python版本的 portfolio_repo.py
 */
public class PortfolioPosition {

    private Long id;

    /** 股票代码 */
    private String stockCode;

    /** 股票名称 */
    private String stockName;

    /** 市场 */
    private String market;

    /** 持仓数量(股) */
    private Integer quantity;

    /** 成本价 */
    private Double costPrice;

    /** 当前价 */
    private Double currentPrice;

    /** 盈亏金额 */
    private Double profitLoss;

    /** 盈亏比例(%) */
    private Double profitLossPct;

    /** 持仓市值 */
    private Double marketValue;

    /** 仓位占比(%) */
    private Double positionPct;

    /** 买入日期 */
    private LocalDateTime buyDate;

    /** 止损价 */
    private Double stopLossPrice;

    /** 目标价 */
    private Double targetPrice;

    /** 标签(逗号分隔) */
    private String tags;

    /** 备注 */
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

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
