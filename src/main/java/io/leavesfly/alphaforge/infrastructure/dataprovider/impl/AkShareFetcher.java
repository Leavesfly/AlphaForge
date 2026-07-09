package io.leavesfly.alphaforge.infrastructure.dataprovider.impl;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.AdjustType;
import io.leavesfly.alphaforge.domain.model.enums.KLineFrequency;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import io.leavesfly.alphaforge.infrastructure.dataprovider.BaseDataFetcher;
import io.leavesfly.alphaforge.infrastructure.dataprovider.EastmoneyDataClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AKShare 数据源适配器。
 *
 * <p>Java 侧无 akshare 库，其 A 股行情本质与 efinance 同源（均为东方财富公开接口）。
 * 因此本适配器仅作为“akshare”这一数据源名称的入口，实际请求统一委托给
 * {@link EastmoneyDataClient}，避免与 {@link EFinanceFetcher} 重复维护 URL/解析逻辑。</p>
 */
@Component
public class AkShareFetcher implements BaseDataFetcher {

    private final EastmoneyDataClient eastmoney;

    public AkShareFetcher(EastmoneyDataClient eastmoney) {
        this.eastmoney = eastmoney;
    }

    @Override
    public String getName() {
        return "akshare";
    }

    @Override
    public String getDataFamily() {
        return "eastmoney";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /** 东财系接口有风控会封IP，限流间隔 1000ms */
    @Override
    public long getRateLimitMs() {
        return 1000;
    }

    /** 东财 push2his kline 仅支持A股K线 */
    @Override
    public Set<MarketType> getSupportedMarkets() {
        return Set.of(MarketType.A);
    }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        return getHistoryData(stockCode, startDate, endDate, KLineFrequency.DAILY, AdjustType.FRONT);
    }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate,
                                               KLineFrequency frequency, AdjustType adjust) {
        return eastmoney.fetchHistory(stockCode, startDate, endDate, frequency, adjust, getName());
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        return eastmoney.fetchRealtimeQuote(stockCode, getName());
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) {
        return getRealtimeQuote(stockCode);
    }

    @Override
    public List<Map<String, Object>> getMinuteData(String stockCode, int period, int count) {
        return eastmoney.fetchMinuteData(stockCode, period, count, getName());
    }
}
