package io.leavesfly.stock.infrastructure.persistence;

import io.leavesfly.stock.domain.model.entity.StockDailyData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票日K线数据缓存访问层
 */
@Mapper
public interface StockDailyDataRepository {

    void insert(StockDailyData data);

    void batchInsert(@Param("list") List<StockDailyData> dataList);

    List<StockDailyData> findByStockCodeAndDateRange(
            @Param("stockCode") String stockCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    StockDailyData findLatest(@Param("stockCode") String stockCode);

    LocalDate findMaxTradeDate(@Param("stockCode") String stockCode);

    void deleteByStockCodeAndDateRange(
            @Param("stockCode") String stockCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    int countByStockCode(@Param("stockCode") String stockCode);
}
