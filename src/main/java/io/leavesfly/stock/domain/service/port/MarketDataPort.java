package io.leavesfly.stock.domain.service.port;

import io.leavesfly.stock.domain.model.entity.market.StockDailyData;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 行情数据端口（依赖倒置）
 *
 * application 层通过此端口获取股票行情/历史/基本面/资金面数据，
 * 具体实现由 infrastructure.dataprovider 提供（多数据源管理器，含熔断/限流/缓存）。
 */
public interface MarketDataPort {

    /** 获取股票历史日K线数据 */
    List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate);

    /** 获取实时行情数据 */
    Map<String, Object> getRealtimeQuote(String stockCode);

    /** 获取股票基本信息(名称、行业、市值等) */
    Map<String, Object> getStockInfo(String stockCode);

    /** 获取股票所属板块 */
    List<String> getStockBoards(String stockCode);

    /** 获取分钟级K线数据 */
    List<Map<String, Object>> getMinuteData(String stockCode, int period, int count);

    /** 获取日级资金流数据 */
    List<Map<String, Object>> getFundFlow(String stockCode, int days);

    /** 获取财报三表数据 */
    List<Map<String, Object>> getFinancialStatements(String stockCode, String statementType);

    /** 获取关键财务指标 */
    List<Map<String, Object>> getKeyIndicators(String stockCode);

    // ========== 信号层 ==========

    /** 获取龙虎榜数据 */
    List<Map<String, Object>> getDragonTigerList(String stockCode, int days);

    /** 获取北向资金流向 */
    List<Map<String, Object>> getNorthboundFlow(int days);

    /** 获取个股板块归属详情 */
    List<Map<String, Object>> getStockBoardsDetail(String stockCode);

    // ========== 杠杆与筹码 ==========

    /** 获取融资融券明细 */
    List<Map<String, Object>> getMarginTrading(String stockCode, int days);

    /** 获取股东户数变化 */
    List<Map<String, Object>> getShareholderCount(String stockCode);

    /** 获取分红送转历史 */
    List<Map<String, Object>> getDividendHistory(String stockCode);

    // ========== 研报与公告 ==========

    /** 获取个股研报列表 */
    List<Map<String, Object>> getResearchReports(String stockCode, int count);

    /** 获取机构一致预期EPS */
    List<Map<String, Object>> getConsensusEPS(String stockCode);

    /** 获取个股公告列表 */
    List<Map<String, Object>> getAnnouncements(String stockCode, int count);
}
