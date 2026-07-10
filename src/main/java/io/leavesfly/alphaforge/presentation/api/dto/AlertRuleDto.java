package io.leavesfly.alphaforge.presentation.api.dto;

import io.leavesfly.alphaforge.domain.model.entity.alert.AlertRule;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警规则响应 DTO：隔离 API 出参与领域实体。
 */
public record AlertRuleDto(Long id, String name, String stockCode, String stockName, String alertType,
                           String targetScope, String target, String severity, Double thresholdValue,
                           String conditionExpr, String parameters, Boolean enabled, Boolean triggered,
                           LocalDateTime lastTriggeredAt, Boolean oneShot, String notifyChannels, String source,
                           String note, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static AlertRuleDto from(AlertRule r) {
        return new AlertRuleDto(r.getId(), r.getName(), r.getStockCode(), r.getStockName(), r.getAlertType(),
                r.getTargetScope(), r.getTarget(), r.getSeverity(), r.getThresholdValue(), r.getConditionExpr(),
                r.getParameters(), r.getEnabled(), r.getTriggered(), r.getLastTriggeredAt(), r.getOneShot(),
                r.getNotifyChannels(), r.getSource(), r.getNote(), r.getCreatedAt(), r.getUpdatedAt());
    }

    public static List<AlertRuleDto> from(List<AlertRule> list) {
        return list == null ? List.of() : list.stream().map(AlertRuleDto::from).toList();
    }
}
