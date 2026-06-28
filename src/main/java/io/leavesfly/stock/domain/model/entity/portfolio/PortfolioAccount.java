package io.leavesfly.stock.domain.model.entity.portfolio;

import java.time.LocalDateTime;

/**
 * 投资账户实体
 * 对应表 portfolio_accounts
 */
public class PortfolioAccount {

    /** 主键ID */
    private Long id;
    /** 账户名称 */
    private String name;
    /** 券商名称 */
    private String broker;
    /** 市场(A/US/HK) */
    private String market;
    /** 基础币种 */
    private String baseCurrency;
    /** 当前现金余额 */
    private Double cashBalance;
    /** 当前融资负债 */
    private Double loanBalance;
    /** 融资额度上限 */
    private Double loanLimit;
    /** 账户所有者ID */
    private String ownerId;
    /** 是否激活 */
    private Boolean isActive;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBroker() { return broker; }
    public void setBroker(String broker) { this.broker = broker; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }
    public Double getCashBalance() { return cashBalance; }
    public void setCashBalance(Double cashBalance) { this.cashBalance = cashBalance; }
    public Double getLoanBalance() { return loanBalance; }
    public void setLoanBalance(Double loanBalance) { this.loanBalance = loanBalance; }
    public Double getLoanLimit() { return loanLimit; }
    public void setLoanLimit(Double loanLimit) { this.loanLimit = loanLimit; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
