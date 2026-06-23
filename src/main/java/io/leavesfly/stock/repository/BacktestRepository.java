package io.leavesfly.stock.repository;

import io.leavesfly.stock.model.entity.BacktestRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 回测记录数据访问层
 */
@Repository
public interface BacktestRepository extends JpaRepository<BacktestRecord, Long> {
    List<BacktestRecord> findByStockCodeOrderByCreatedAtDesc(String stockCode);
    List<BacktestRecord> findByStrategyNameOrderByCreatedAtDesc(String strategyName);
    List<BacktestRecord> findTop20ByOrderByCreatedAtDesc();
}
