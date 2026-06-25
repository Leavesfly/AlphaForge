package io.leavesfly.stock.infrastructure.dataprovider;

import io.leavesfly.stock.domain.model.entity.StockDailyData;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
}
