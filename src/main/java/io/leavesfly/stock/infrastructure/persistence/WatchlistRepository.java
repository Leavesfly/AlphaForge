package io.leavesfly.stock.infrastructure.persistence;

import io.leavesfly.stock.domain.model.entity.WatchlistItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自选股数据访问层
 */
@Mapper
public interface WatchlistRepository {

    void insert(WatchlistItem item);

    void deleteByStockCode(@Param("stockCode") String stockCode);

    WatchlistItem findByStockCode(@Param("stockCode") String stockCode);

    List<WatchlistItem> findAll();

    int count();

    boolean existsByStockCode(@Param("stockCode") String stockCode);
}
