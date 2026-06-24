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

    List<DecisionSignal> findTop20ByOrderByCreatedAtDesc();

    List<DecisionSignal> findByStockCodeAndCreatedAtAfter(@Param("stockCode") String stockCode, @Param("after") LocalDateTime after);

    List<DecisionSignal> findByCreatedAtAfter(@Param("after") LocalDateTime after);

    /** 多条件筛选查询 */
    List<DecisionSignal> findFiltered(@Param("market") String market,
                                       @Param("stockCode") String stockCode,
                                       @Param("action") String action,
                                       @Param("marketPhase") String marketPhase,
                                       @Param("sourceType") String sourceType,
                                       @Param("status") String status,
                                       @Param("createdFrom") LocalDateTime createdFrom,
                                       @Param("createdTo") LocalDateTime createdTo,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    int countFiltered(@Param("market") String market,
                      @Param("stockCode") String stockCode,
                      @Param("action") String action,
                      @Param("marketPhase") String marketPhase,
                      @Param("sourceType") String sourceType,
                      @Param("status") String status,
                      @Param("createdFrom") LocalDateTime createdFrom,
                      @Param("createdTo") LocalDateTime createdTo);

    /** 获取某只股票最新N条信号 */
    List<DecisionSignal> findByLatestStockCode(@Param("stockCode") String stockCode, @Param("limit") int limit);

    /** 更新信号状态 */
    void updateStatus(@Param("id") Long id, @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);

    default DecisionSignal save(DecisionSignal signal) {
        LocalDateTime now = LocalDateTime.now();
        if (signal.getId() == null) {
            if (signal.getCreatedAt() == null) signal.setCreatedAt(now);
            signal.setUpdatedAt(now);
            insert(signal);
        } else {
            signal.setUpdatedAt(now);
            update(signal);
        }
        return signal;
    }

    default Optional<DecisionSignal> findByIdOpt(Long id) {
        return Optional.ofNullable(findById(id));
    }
}
