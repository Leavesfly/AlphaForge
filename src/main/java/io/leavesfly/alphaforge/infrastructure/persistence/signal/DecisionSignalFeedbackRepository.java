package io.leavesfly.alphaforge.infrastructure.persistence.signal;

import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignalFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 决策信号反馈数据访问层
 */
@Mapper
public interface DecisionSignalFeedbackRepository {

    void insert(DecisionSignalFeedback feedback);

    void update(DecisionSignalFeedback feedback);

    DecisionSignalFeedback findBySignalId(@Param("signalId") Long signalId);

    void deleteBySignalId(@Param("signalId") Long signalId);

    default Optional<DecisionSignalFeedback> findBySignalIdOpt(Long signalId) {
        return Optional.ofNullable(findBySignalId(signalId));
    }

    default DecisionSignalFeedback saveOrUpdate(DecisionSignalFeedback feedback) {
        LocalDateTime now = LocalDateTime.now();
        DecisionSignalFeedback existing = findBySignalId(feedback.getSignalId());
        if (existing == null) {
            if (feedback.getCreatedAt() == null) feedback.setCreatedAt(now);
            feedback.setUpdatedAt(now);
            insert(feedback);
        } else {
            feedback.setId(existing.getId());
            feedback.setCreatedAt(existing.getCreatedAt());
            feedback.setUpdatedAt(now);
            update(feedback);
        }
        return feedback;
    }
}
