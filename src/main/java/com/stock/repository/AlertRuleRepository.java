package com.stock.repository;

import com.stock.model.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 告警规则数据访问层
 */
@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByStockCodeAndEnabledTrue(String stockCode);
    List<AlertRule> findByEnabledTrueOrderByCreatedAtDesc();
    List<AlertRule> findByAlertTypeOrderByCreatedAtDesc(String alertType);
    List<AlertRule> findByTriggeredFalseAndEnabledTrue();
    List<AlertRule> findByEnabled(Boolean enabled);
    long countByEnabledTrue();
}
