package io.leavesfly.stock.infrastructure.persistence;

import io.leavesfly.stock.domain.model.entity.BacktestRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 回测记录数据访问层
 */
@Mapper
public interface BacktestRepository {

    void insert(BacktestRecord record);

    void update(BacktestRecord record);

    BacktestRecord findById(@Param("id") Long id);

    List<BacktestRecord> findAll();

    void deleteById(@Param("id") Long id);

    List<BacktestRecord> findByStockCodeOrderByCreatedAtDesc(@Param("stockCode") String stockCode);

    List<BacktestRecord> findByStrategyNameOrderByCreatedAtDesc(@Param("strategyName") String strategyName);

    List<BacktestRecord> findTop20ByOrderByCreatedAtDesc();

    default BacktestRecord save(BacktestRecord record) {
        if (record.getId() == null) {
            if (record.getCreatedAt() == null) {
                record.setCreatedAt(java.time.LocalDateTime.now());
            }
            insert(record);
        } else {
            update(record);
        }
        return record;
    }

    default Optional<BacktestRecord> findByIdOpt(Long id) {
        return Optional.ofNullable(findById(id));
    }
}
