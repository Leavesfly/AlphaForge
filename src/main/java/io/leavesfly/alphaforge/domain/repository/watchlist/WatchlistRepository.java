package io.leavesfly.alphaforge.domain.repository.watchlist;

import io.leavesfly.alphaforge.domain.model.entity.watchlist.WatchlistItem;
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
