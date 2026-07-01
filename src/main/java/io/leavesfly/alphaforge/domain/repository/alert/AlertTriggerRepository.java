package io.leavesfly.alphaforge.domain.repository.alert;

import io.leavesfly.alphaforge.domain.model.entity.alert.AlertTrigger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警触发记录数据访问层
 */
@Mapper
public interface AlertTriggerRepository {

    void insert(AlertTrigger trigger);

    AlertTrigger findById(@Param("id") Long id);

    List<AlertTrigger> findByRuleId(@Param("ruleId") Long ruleId);

    List<AlertTrigger> findAll(@Param("limit") int limit, @Param("offset") int offset);

    int count();

    List<AlertTrigger> findByTarget(@Param("target") String target);

    List<AlertTrigger> findByStatus(@Param("status") String status);

    default AlertTrigger save(AlertTrigger trigger) {
        if (trigger.getTriggeredAt() == null) trigger.setTriggeredAt(LocalDateTime.now());
        if (trigger.getStatus() == null) trigger.setStatus("triggered");
        insert(trigger);
        return trigger;
    }
}
