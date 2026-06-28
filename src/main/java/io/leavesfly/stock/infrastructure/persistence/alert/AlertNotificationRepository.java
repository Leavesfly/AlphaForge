package io.leavesfly.stock.infrastructure.persistence.alert;

import io.leavesfly.stock.domain.model.entity.alert.AlertNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警通知记录数据访问层
 */
@Mapper
public interface AlertNotificationRepository {

    void insert(AlertNotification notification);

    AlertNotification findById(@Param("id") Long id);

    List<AlertNotification> findByTriggerId(@Param("triggerId") Long triggerId);

    List<AlertNotification> findAll(@Param("limit") int limit, @Param("offset") int offset);

    int count();

    List<AlertNotification> findByChannel(@Param("channel") String channel);

    default AlertNotification save(AlertNotification notification) {
        if (notification.getSentAt() == null) notification.setSentAt(LocalDateTime.now());
        insert(notification);
        return notification;
    }
}
