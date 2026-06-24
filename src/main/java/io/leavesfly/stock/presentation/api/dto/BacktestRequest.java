package io.leavesfly.stock.presentation.api.dto;

import java.time.LocalDate;

/**
 * 回测请求 DTO
 */
public class BacktestRequest {
    private String stockCode;
    private String strategy = "ma_golden_cross";
    private int days = 180;
    private double initialCapital = 100000;

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public int getDays() { return days; }
    public void setDays(int days) { this.days = days; }
    public double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }
}
