package io.leavesfly.stock.presentation.api.dto;

/**
 * 单股分析请求 DTO
 */
public class SingleAnalysisRequest {
    private String stockCode;

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
}
