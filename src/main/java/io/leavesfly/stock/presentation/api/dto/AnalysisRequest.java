package io.leavesfly.stock.presentation.api.dto;

/**
 * 分析请求 DTO
 */
public class AnalysisRequest {
    private String stocks;
    private boolean dryRun = false;

    public String getStocks() { return stocks; }
    public void setStocks(String stocks) { this.stocks = stocks; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
}
