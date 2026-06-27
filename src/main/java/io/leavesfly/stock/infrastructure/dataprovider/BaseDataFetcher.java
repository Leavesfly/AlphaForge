package io.leavesfly.stock.infrastructure.dataprovider;

import io.leavesfly.stock.domain.model.entity.StockDailyData;
import io.leavesfly.stock.domain.model.enums.MarketType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据获取器基础接口
 * 所有数据源适配器都需要实现此接口
 */
public interface BaseDataFetcher {

    /**
     * 获取数据源名称
     */
    String getName();

    /**
     * 获取数据源优先级(越小越高)
     */
    int getPriority();

    /**
     * 检查数据源是否可用
     */
    boolean isAvailable();

    /**
     * 获取最小请求间隔(毫秒) — 差异化限流
     *
     * 按封 IP 风险分级：
     * - 不封 IP 的源(腾讯/通达信): 100ms
     * - 低风险的源(新浪): 200ms
     * - 东财系(有风控): 1000ms + 随机抖动
     */
    default long getRateLimitMs() {
        return 200;
    }

    /**
     * 获取该数据源支持的市场类型 — 能力感知路由
     *
     * 默认支持所有市场，仅支持特定市场的数据源应重写此方法
     */
    default Set<MarketType> getSupportedMarkets() {
        return Set.of(MarketType.A, MarketType.HK, MarketType.US, MarketType.JP, MarketType.KR);
    }

    /**
     * 获取股票历史日K线数据
     *
     * @param stockCode 股票代码
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 日K线数据列表
     */
    List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate);

    /**
     * 获取实时行情数据
     *
     * @param stockCode 股票代码
     * @return 实时行情数据(key-value形式)
     */
    Map<String, Object> getRealtimeQuote(String stockCode);

    /**
     * 获取股票基本信息
     *
     * @param stockCode 股票代码
     * @return 基本信息(名称、行业、市值等)
     */
    Map<String, Object> getStockInfo(String stockCode);

    /**
     * 获取板块/指数列表
     *
     * @return 板块列表
     */
    default List<Map<String, Object>> getBoardList() {
        return List.of();
    }

    /**
     * 获取股票所属板块
     *
     * @param stockCode 股票代码
     * @return 所属板块列表
     */
    default List<String> getStockBoards(String stockCode) {
        return List.of();
    }

    /**
     * 批量获取实时行情
     *
     * @param stockCodes 股票代码列表
     * @return 批量行情数据
     */
    default Map<String, Map<String, Object>> getBatchRealtimeQuotes(List<String> stockCodes) {
        // 默认逐个获取
        Map<String, Map<String, Object>> result = new java.util.LinkedHashMap<>();
        for (String code : stockCodes) {
            try {
                Map<String, Object> quote = getRealtimeQuote(code);
                if (quote != null && !quote.isEmpty()) {
                    result.put(code, quote);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /**
     * 获取分钟级K线数据
     *
     * @param stockCode 股票代码
     * @param period    周期(1/5/15/30/60分钟)
     * @param count     数据条数
     * @return K线数据列表
     */
    default List<Map<String, Object>> getMinuteData(String stockCode, int period, int count) {
        return List.of();
    }

    // ========== 资金面数据 ==========

    /**
     * 获取日级资金流数据（主力/大单/中单/小单净流入）
     *
     * @param stockCode 股票代码
     * @param days      返回最近天数
     * @return 每日资金流数据 [{date, main_net, big_net, mid_net, small_net, ...}]
     */
    default List<Map<String, Object>> getFundFlow(String stockCode, int days) {
        return List.of();
    }

    // ========== 基本面数据 ==========

    /**
     * 获取财报三表数据
     *
     * @param stockCode      股票代码
     * @param statementType 报表类型: "balance" / "income" / "cashflow"
     * @return 财务数据列表，每行一个科目
     */
    default List<Map<String, Object>> getFinancialStatements(String stockCode, String statementType) {
        return List.of();
    }

    /**
     * 获取关键财务指标（ROE/ROA/EPS/毛利率/资产负债率等）
     *
     * @param stockCode 股票代码
     * @return 关键指标列表，每期一行
     */
    default List<Map<String, Object>> getKeyIndicators(String stockCode) {
        return List.of();
    }
}
