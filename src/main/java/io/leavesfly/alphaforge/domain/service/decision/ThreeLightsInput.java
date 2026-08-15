package io.leavesfly.alphaforge.domain.service.decision;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 三灯引擎输入 — 由应用层组装，domain 保持纯净。
 *
 * <p>可空字段缺失时对应维度诚实降级（灰灯/跳过检查），不猜测填充。</p>
 */
public class ThreeLightsInput {

    private final String stockCode;
    private final String stockName;
    /** 历史日 K（时间升序），评估仅使用已完成的 K 线，无前视 */
    private final List<StockDailyData> history;
    /** 基准（指数）历史，可空：缺失时相对强度权重并入动量并标注降级 */
    private final List<StockDailyData> benchmarkHistory;
    /** 是否 ST/*ST，可空 */
    private final Boolean isSt;
    /** 每股净资产，可空：< 0 视为资不抵债（硬伤） */
    private final Double netAssetPerShare;
    /** 最近若干季 EPS（时间升序，至少 4 个用于连续亏损判断），可空 */
    private final List<Double> epsRecent;
    /** 估值分位（0~1，PE/PB 分位均值），可空：缺失时价灯灰 */
    private final Double valuationPercentile;
    /** 估值口径说明（如"近似口径"），可空 */
    private final String valuationNote;
    /** 事件风险（近 30 天 high/medium），可空 */
    private final List<RiskEvent> riskEvents;
    /** 持仓成本价，可空：存在时只改操作建议不改灯色 */
    private final Double positionCost;
    /** 持仓股数，可空 */
    private final Double positionShares;
    /** 持仓成本来源（manual 手动输入 / portfolio 登记持仓），默认 manual */
    private final String positionSource;
    /** 风险画像仓位乘数（0.5/1.0/1.5），默认 1.0 */
    private final double riskMultiplier;
    /** 可用资金（元），可空：缺失时不给建议仓位 */
    private final Double capitalYuan;
    /** 最小交易单位（A 股 100，其余 1） */
    private final int lotSize;
    /** 单票建议仓位市值占资金上限（0~1，默认 1.0 = 仅受市值≤资金约束） */
    private final double maxPositionPct;
    /** 市场环境上下文（应用层经 MarketAnalysisService 组装后透传，可空） */
    private final Map<String, Object> marketContext;

    private ThreeLightsInput(Builder b) {
        this.stockCode = b.stockCode;
        this.stockName = b.stockName;
        this.history = b.history;
        this.benchmarkHistory = b.benchmarkHistory;
        this.isSt = b.isSt;
        this.netAssetPerShare = b.netAssetPerShare;
        this.epsRecent = b.epsRecent;
        this.valuationPercentile = b.valuationPercentile;
        this.valuationNote = b.valuationNote;
        this.riskEvents = b.riskEvents;
        this.positionCost = b.positionCost;
        this.positionShares = b.positionShares;
        this.positionSource = b.positionSource;
        this.riskMultiplier = b.riskMultiplier;
        this.capitalYuan = b.capitalYuan;
        this.lotSize = b.lotSize;
        this.maxPositionPct = b.maxPositionPct;
        this.marketContext = b.marketContext;
    }

    public static Builder builder(String stockCode) {
        return new Builder(stockCode);
    }

    public String getStockCode() { return stockCode; }
    public String getStockName() { return stockName; }
    public List<StockDailyData> getHistory() { return history; }
    public List<StockDailyData> getBenchmarkHistory() { return benchmarkHistory; }
    public Boolean getSt() { return isSt; }
    public Double getNetAssetPerShare() { return netAssetPerShare; }
    public List<Double> getEpsRecent() { return epsRecent; }
    public Double getValuationPercentile() { return valuationPercentile; }
    public String getValuationNote() { return valuationNote; }
    public List<RiskEvent> getRiskEvents() { return riskEvents; }
    public Double getPositionCost() { return positionCost; }
    public Double getPositionShares() { return positionShares; }
        public String getPositionSource() { return positionSource; }
    public double getRiskMultiplier() { return riskMultiplier; }
    public Double getCapitalYuan() { return capitalYuan; }
    public int getLotSize() { return lotSize; }
    public double getMaxPositionPct() { return maxPositionPct; }
    public Map<String, Object> getMarketContext() { return marketContext; }

    /**
     * 事件风险 — 近 30 天内 high 亮红、medium 亮黄（利好不加分）。
     */
    public static class RiskEvent {
        private final LocalDate date;
        /** high / medium */
        private final String level;
        private final String note;

        public RiskEvent(LocalDate date, String level, String note) {
            this.date = date;
            this.level = level;
            this.note = note;
        }

        public LocalDate getDate() { return date; }
        public String getLevel() { return level; }
        public String getNote() { return note; }
    }

    public static class Builder {
        private final String stockCode;
        private String stockName;
        private List<StockDailyData> history = List.of();
        private List<StockDailyData> benchmarkHistory;
        private Boolean isSt;
        private Double netAssetPerShare;
        private List<Double> epsRecent;
        private Double valuationPercentile;
        private String valuationNote;
        private List<RiskEvent> riskEvents;
        private Double positionCost;
        private Double positionShares;
                private String positionSource = "manual";
        private double riskMultiplier = 1.0;
        private Double capitalYuan;
        private int lotSize = 1;
        private double maxPositionPct = 1.0;
        private Map<String, Object> marketContext;

        private Builder(String stockCode) {
            this.stockCode = stockCode;
        }

        public Builder stockName(String stockName) { this.stockName = stockName; return this; }
        public Builder history(List<StockDailyData> history) { this.history = history; return this; }
        public Builder benchmarkHistory(List<StockDailyData> benchmarkHistory) { this.benchmarkHistory = benchmarkHistory; return this; }
        public Builder isSt(Boolean isSt) { this.isSt = isSt; return this; }
        public Builder netAssetPerShare(Double netAssetPerShare) { this.netAssetPerShare = netAssetPerShare; return this; }
        public Builder epsRecent(List<Double> epsRecent) { this.epsRecent = epsRecent; return this; }
        public Builder valuationPercentile(Double valuationPercentile) { this.valuationPercentile = valuationPercentile; return this; }
        public Builder valuationNote(String valuationNote) { this.valuationNote = valuationNote; return this; }
        public Builder riskEvents(List<RiskEvent> riskEvents) { this.riskEvents = riskEvents; return this; }
        public Builder positionCost(Double positionCost) { this.positionCost = positionCost; return this; }
        public Builder positionShares(Double positionShares) { this.positionShares = positionShares; return this; }
                public Builder positionSource(String positionSource) { this.positionSource = positionSource; return this; }
        public Builder riskMultiplier(double riskMultiplier) { this.riskMultiplier = riskMultiplier; return this; }
        public Builder capitalYuan(Double capitalYuan) { this.capitalYuan = capitalYuan; return this; }
        public Builder lotSize(int lotSize) { this.lotSize = lotSize; return this; }
        public Builder maxPositionPct(double maxPositionPct) { this.maxPositionPct = maxPositionPct; return this; }
        public Builder marketContext(Map<String, Object> marketContext) { this.marketContext = marketContext; return this; }

        public ThreeLightsInput build() {
            return new ThreeLightsInput(this);
        }
    }
}
