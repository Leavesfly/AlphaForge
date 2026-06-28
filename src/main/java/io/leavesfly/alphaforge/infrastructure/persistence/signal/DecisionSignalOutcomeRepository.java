package io.leavesfly.alphaforge.infrastructure.persistence.signal;

import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignalOutcome;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 决策信号结果评估数据访问层
 */
@Mapper
public interface DecisionSignalOutcomeRepository {

    void insert(DecisionSignalOutcome outcome);

    DecisionSignalOutcome findById(@Param("id") Long id);

    List<DecisionSignalOutcome> findBySignalId(@Param("signalId") Long signalId);

    List<DecisionSignalOutcome> findAll(@Param("signalId") Long signalId,
                                        @Param("horizon") String horizon,
                                        @Param("engineVersion") String engineVersion,
                                        @Param("evalStatus") String evalStatus,
                                        @Param("outcome") String outcome,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    int countAll(@Param("signalId") Long signalId,
                 @Param("horizon") String horizon,
                 @Param("evalStatus") String evalStatus,
                 @Param("outcome") String outcome);

    void deleteBySignalId(@Param("signalId") Long signalId);

    /** 统计: 按outcome分组计数 */
    List<Map<String, Object>> getOutcomeStats();

    /** 统计: 按horizon分组 */
    List<Map<String, Object>> getStatsByHorizon(@Param("horizons") List<String> horizons,
                                                 @Param("engineVersion") String engineVersion);

    default DecisionSignalOutcome save(DecisionSignalOutcome outcome) {
        if (outcome.getCreatedAt() == null) outcome.setCreatedAt(LocalDateTime.now());
        insert(outcome);
        return outcome;
    }
}
