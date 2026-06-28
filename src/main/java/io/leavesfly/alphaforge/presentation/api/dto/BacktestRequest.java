package io.leavesfly.alphaforge.presentation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 回测请求 DTO。
 */
public class BacktestRequest {

    @NotBlank(message = "stock_code is required")
    private String stockCode;

    private String strategy = "ma_golden_cross";
    private String startDate;
    private String endDate;

    @Positive(message = "initial_capital must be positive")
    private double initialCapital = 100000;

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }
}
