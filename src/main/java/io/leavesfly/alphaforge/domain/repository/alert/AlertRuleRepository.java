package io.leavesfly.alphaforge.domain.repository.alert;

import io.leavesfly.alphaforge.domain.model.entity.alert.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 告警规则数据访问层
 */
@Mapper
public interface AlertRuleRepository {

    void insert(AlertRule rule);

    void update(AlertRule rule);

    AlertRule findById(@Param("id") Long id);

    List<AlertRule> findAll();

    void deleteById(@Param("id") Long id);

    List<AlertRule> findByStockCodeAndEnabledTrue(@Param("stockCode") String stockCode);

    List<AlertRule> findByEnabledTrueOrderByCreatedAtDesc();

    List<AlertRule> findByAlertTypeOrderByCreatedAtDesc(@Param("alertType") String alertType);

    List<AlertRule> findByTriggeredFalseAndEnabledTrue();

    List<AlertRule> findByEnabled(@Param("enabled") Boolean enabled);

    long countByEnabledTrue();

    default AlertRule save(AlertRule rule) {
        if (rule.getId() == null) {
            if (rule.getCreatedAt() == null) {
                rule.setCreatedAt(java.time.LocalDateTime.now());
            }
            insert(rule);
        } else {
            update(rule);
        }
        return rule;
    }

    default Optional<AlertRule> findByIdOpt(Long id) {
        AlertRule result = findById(id);
        return Optional.ofNullable(result);
    }
}
