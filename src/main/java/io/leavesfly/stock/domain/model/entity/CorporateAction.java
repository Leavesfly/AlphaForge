package io.leavesfly.stock.domain.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公司行动实体
 * 对应表 corporate_actions
 */
public class CorporateAction {

    private Long id;
    private Long accountId;
    private String symbol;
    private LocalDate effectiveDate;
    private String actionType;
    private String market;
    private String currency;
    private Double cashDividendPerShare;
    private Double splitRatio;
    private String note;
    private LocalDateTime createdAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Double getCashDividendPerShare() { return cashDividendPerShare; }
    public void setCashDividendPerShare(Double cashDividendPerShare) { this.cashDividendPerShare = cashDividendPerShare; }
    public Double getSplitRatio() { return splitRatio; }
    public void setSplitRatio(Double splitRatio) { this.splitRatio = splitRatio; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
