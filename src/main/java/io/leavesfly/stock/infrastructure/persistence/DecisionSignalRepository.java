package io.leavesfly.stock.infrastructure.persistence;

import io.leavesfly.stock.domain.model.entity.DecisionSignal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 决策信号数据访问层
 */
@Mapper
public interface DecisionSignalRepository {

    void insert(DecisionSignal signal);

    void update(DecisionSignal signal);

    DecisionSignal findById(@Param("id") Long id);

    List<DecisionSignal> findAll();

    void deleteById(@Param("id") Long id);

    List<DecisionSignal> findByStockCodeOrderByCreatedAtDesc(@Param("stockCode") String stockCode);

    List<DecisionSignal> findByStatusOrderByCreatedAtDesc(@Param("status") String status);

    List<DecisionSignal> findBySignalTypeAndStatusOrderByCreatedAtDesc(@Param("signalType") String signalType, @Param("status") String status);

    List<DecisionSignal> findTop20ByOrderByCreatedAtDesc();

    List<DecisionSignal> findByStockCodeAndCreatedAtAfter(@Param("stockCode") String stockCode, @Param("after") LocalDateTime after);

    List<DecisionSignal> findByCreatedAtAfter(@Param("after") LocalDateTime after);

    default DecisionSignal save(DecisionSignal signal) {
        if (signal.getId() == null) {
            if (signal.getCreatedAt() == null) {
                signal.setCreatedAt(LocalDateTime.now());
            }
            insert(signal);
        } else {
            update(signal);
        }
        return signal;
    }

    default Optional<DecisionSignal> findByIdOpt(Long id) {
        return Optional.ofNullable(findById(id));
    }
}
