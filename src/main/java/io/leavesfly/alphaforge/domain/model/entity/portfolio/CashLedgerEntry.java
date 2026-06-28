package io.leavesfly.alphaforge.domain.model.entity.portfolio;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 现金流水实体
 * 对应表 cash_ledger
 */
public class CashLedgerEntry {

    /** 主键ID */
    private Long id;
    /** 关联账户ID */
    private Long accountId;
    /** 业务日期 */
    private LocalDate eventDate;
    /** 方向(in入金/out出金) */
    private String direction;
    /** 金额 */
    private Double amount;
    /** 币种 */
    private String currency;
    /** 备注 */
    private String note;
    /** 创建时间 */
    private LocalDateTime createdAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
