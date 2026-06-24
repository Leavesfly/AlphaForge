package io.leavesfly.stock.domain.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易记录实体
 * 对应表 portfolio_trades
 */
public class PortfolioTrade {

    private Long id;
    private Long accountId;
    private String symbol;
    private LocalDate tradeDate;
    private String side;
    private Double quantity;
    private Double price;
    private Double fee;
    private Double tax;
    private String market;
    private String currency;
    private String tradeUid;
    private String note;
    private LocalDateTime createdAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Double getFee() { return fee; }
    public void setFee(Double fee) { this.fee = fee; }
    public Double getTax() { return tax; }
    public void setTax(Double tax) { this.tax = tax; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getTradeUid() { return tradeUid; }
    public void setTradeUid(String tradeUid) { this.tradeUid = tradeUid; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
