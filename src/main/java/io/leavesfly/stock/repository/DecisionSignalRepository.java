package io.leavesfly.stock.repository;

import io.leavesfly.stock.model.entity.DecisionSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 决策信号数据访问层
 */
@Repository
public interface DecisionSignalRepository extends JpaRepository<DecisionSignal, Long> {
    List<DecisionSignal> findByStockCodeOrderByCreatedAtDesc(String stockCode);
    List<DecisionSignal> findByStatusOrderByCreatedAtDesc(String status);
    List<DecisionSignal> findBySignalTypeAndStatusOrderByCreatedAtDesc(String signalType, String status);
    List<DecisionSignal> findTop20ByOrderByCreatedAtDesc();
    List<DecisionSignal> findByStockCodeAndCreatedAtAfter(String stockCode, LocalDateTime after);
    List<DecisionSignal> findByCreatedAtAfter(LocalDateTime after);
}
