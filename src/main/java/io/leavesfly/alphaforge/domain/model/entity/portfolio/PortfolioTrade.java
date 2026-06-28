package io.leavesfly.alphaforge.domain.model.entity.portfolio;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易记录实体
 * 对应表 portfolio_trades
 */
public class PortfolioTrade {

    /** 主键ID */
    private Long id;
    /** 关联账户ID */
    private Long accountId;
    /** 股票代码 */
    private String symbol;
    /** 交易日期 */
    private LocalDate tradeDate;
    /** 买卖方向(buy/sell) */
    private String side;
    /** 成交数量 */
    private Double quantity;
    /** 成交价格 */
    private Double price;
    /** 手续费 */
    private Double fee;
    /** 印花税 */
    private Double tax;
    /** 市场(A/US/HK) */
    private String market;
    /** 币种 */
    private String currency;
    /** 交易唯一标识(防重复导入) */
    private String tradeUid;
    /** 备注 */
    private String note;
    /** 创建时间 */
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
