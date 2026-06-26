package io.leavesfly.stock.application.backtest;

import java.time.LocalDate;

/**
 * 单笔回测成交记录。
 */
public class BacktestTrade {

    private LocalDate tradeDate;
    private String side;
    private double price;
    private int shares;
    private double commission;
    private double stampTax;
    private double slippageCost;
    private double amount;
    private String reason;
    private int barIndex;

    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getShares() { return shares; }
    public void setShares(int shares) { this.shares = shares; }
    public double getCommission() { return commission; }
    public void setCommission(double commission) { this.commission = commission; }
    public double getStampTax() { return stampTax; }
    public void setStampTax(double stampTax) { this.stampTax = stampTax; }
    public double getSlippageCost() { return slippageCost; }
    public void setSlippageCost(double slippageCost) { this.slippageCost = slippageCost; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public int getBarIndex() { return barIndex; }
    public void setBarIndex(int barIndex) { this.barIndex = barIndex; }
}
