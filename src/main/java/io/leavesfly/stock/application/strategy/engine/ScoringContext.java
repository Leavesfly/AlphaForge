package io.leavesfly.stock.application.strategy.engine;

import io.leavesfly.stock.domain.model.entity.market.StockDailyData;

import java.util.List;
import java.util.Map;

/**
 * 综合分析评分的输入上下文。
 *
 * 封装 CompositeScoringEngine 求值所需的四类数据，
 * 由 AnalysisPostProcessor 在 LLM 分析后组装并传入。
 */
public class ScoringContext {

    private final List<StockDailyData> history;
    private final Map<String, Object> technical;
    private final Map<String, Object> quote;
    private final Map<String, Object> marketContext;

    public ScoringContext(List<StockDailyData> history,
                          Map<String, Object> technical,
                          Map<String, Object> quote,
                          Map<String, Object> marketContext) {
        this.history = history;
        this.technical = technical != null ? technical : Map.of();
        this.quote = quote != null ? quote : Map.of();
        this.marketContext = marketContext != null ? marketContext : Map.of();
    }

    /** 工厂方法，便于调用方一行构建上下文 */
    public static ScoringContext of(List<StockDailyData> history,
                                    Map<String, Object> technical,
                                    Map<String, Object> quote,
                                    Map<String, Object> marketContext) {
        return new ScoringContext(history, technical, quote, marketContext);
    }

    public List<StockDailyData> getHistory() { return history; }
    public Map<String, Object> getTechnical() { return technical; }
    public Map<String, Object> getQuote() { return quote; }
    public Map<String, Object> getMarketContext() { return marketContext; }

    public int size() { return history != null ? history.size() : 0; }

    /** 指定索引的收盘价 */
    public double close(int index) {
        return history.get(index).getClosePrice();
    }

    /** 指定索引的成交量 */
    public long volume(int index) {
        Long vol = history.get(index).getVolume();
        return vol != null ? vol : 0;
    }

    /** 指定索引的涨跌幅（%） */
    public Double changePct(int index) {
        return history.get(index).getChangePct();
    }
}
