package io.leavesfly.alphaforge.application.service.alert;

import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.model.entity.alert.AlertRule;
import io.leavesfly.alphaforge.domain.model.entity.alert.AlertTrigger;
import io.leavesfly.alphaforge.domain.model.entity.alert.AlertNotification;
import io.leavesfly.alphaforge.domain.service.port.NotificationPort;
import io.leavesfly.alphaforge.infrastructure.persistence.alert.AlertRuleRepository;
import io.leavesfly.alphaforge.infrastructure.persistence.alert.AlertTriggerRepository;
import io.leavesfly.alphaforge.infrastructure.persistence.alert.AlertNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 告警服务 - 条件触发和监控
 *
 * 功能:
 * 1. 管理告警规则(CRUD)
 * 2. 定期轮询检查告警条件
 * 3. 触发后通知推送
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private final AlertRuleRepository alertRepo;
    private final AlertTriggerRepository triggerRepo;
    private final AlertNotificationRepository notifRepo;
    private final MarketDataPort dataFetcher;
    private final NotificationPort notificationService;

    public AlertService(AlertRuleRepository alertRepo, AlertTriggerRepository triggerRepo,
                       AlertNotificationRepository notifRepo, MarketDataPort dataFetcher,
                       NotificationPort notificationService) {
        this.alertRepo = alertRepo;
        this.triggerRepo = triggerRepo;
        this.notifRepo = notifRepo;
        this.dataFetcher = dataFetcher;
        this.notificationService = notificationService;
    }

    /** 创建告警规则 */
    public AlertRule createAlert(AlertRule rule) {
        return alertRepo.save(rule);
    }

    /** 获取所有启用的告警 */
    public List<AlertRule> getActiveAlerts() {
        return alertRepo.findByEnabledTrueOrderByCreatedAtDesc();
    }

    /** 获取指定股票的告警 */
    public List<AlertRule> getAlertsByStock(String stockCode) {
        return alertRepo.findByStockCodeAndEnabledTrue(stockCode);
    }

    /** 更新告警规则 */
    public AlertRule updateAlert(Long id, AlertRule updated) {
        return alertRepo.findByIdOpt(id).map(rule -> {
            if (updated.getAlertType() != null) rule.setAlertType(updated.getAlertType());
            if (updated.getThresholdValue() != null) rule.setThresholdValue(updated.getThresholdValue());
            if (updated.getEnabled() != null) rule.setEnabled(updated.getEnabled());
            if (updated.getNotifyChannels() != null) rule.setNotifyChannels(updated.getNotifyChannels());
            if (updated.getNote() != null) rule.setNote(updated.getNote());
            return alertRepo.save(rule);
        }).orElse(null);
    }

    /** 删除告警规则 */
    public void deleteAlert(Long id) {
        alertRepo.deleteById(id);
    }

    /** 启用告警规则 */
    public void enableAlert(Long id) {
        alertRepo.findByIdOpt(id).ifPresent(rule -> {
            rule.setEnabled(true);
            alertRepo.save(rule);
        });
    }

    /** 禁用告警规则 */
    public void disableAlert(Long id) {
        alertRepo.findByIdOpt(id).ifPresent(rule -> {
            rule.setEnabled(false);
            alertRepo.save(rule);
        });
    }

    /** 测试告警规则 */
    public Map<String, Object> testAlert(Long id) {
        Optional<AlertRule> opt = alertRepo.findByIdOpt(id);
        if (opt.isEmpty()) return Map.of("status", "not_found", "triggered", false, "message", "规则不存在");
        AlertRule rule = opt.get();
        try {
            boolean triggered = evaluateAlert(rule);
            return Map.of("status", "ok", "triggered", triggered, "message", triggered ? "规则触发" : "规则未触发");
        } catch (Exception e) {
            return Map.of("status", "evaluation_error", "triggered", false, "message", e.getMessage());
        }
    }

    /** 获取触发历史 */
    public List<Map<String, Object>> getTriggerHistory(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<AlertTrigger> triggers = triggerRepo.findAll(pageSize, offset);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AlertTrigger t : triggers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("rule_id", t.getRuleId());
            m.put("target", t.getTarget());
            m.put("display_target", t.getDisplayTarget());
            m.put("status", t.getStatus());
            m.put("observed_value", t.getObservedValue());
            m.put("threshold_value", t.getThresholdValue());
            m.put("message", t.getMessage());
            m.put("triggered_at", t.getTriggeredAt());
            result.add(m);
        }
        return result;
    }

    /** 获取通知日志 */
    public List<Map<String, Object>> getNotificationHistory(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<AlertNotification> notifs = notifRepo.findAll(pageSize, offset);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AlertNotification n : notifs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("trigger_id", n.getTriggerId());
            m.put("channel", n.getChannel());
            m.put("success", n.getSuccess());
            m.put("error_code", n.getErrorCode());
            m.put("error_message", n.getErrorMessage());
            m.put("sent_at", n.getSentAt());
            result.add(m);
        }
        return result;
    }

    /**
     * 告警工作线程 - 每分钟检查一次
     */
    @Scheduled(fixedDelay = 60000)
    public void checkAlerts() {
        List<AlertRule> activeAlerts = alertRepo.findByTriggeredFalseAndEnabledTrue();
        if (activeAlerts.isEmpty()) return;

        log.debug("检查 {} 条告警规则...", activeAlerts.size());

        for (AlertRule rule : activeAlerts) {
            try {
                boolean triggered = evaluateAlert(rule);
                if (triggered) {
                    triggerAlert(rule);
                }
            } catch (Exception e) {
                log.error("告警检查失败: {} - {}", rule.getStockCode(), e.getMessage());
            }
        }
    }

    /**
     * 评估告警条件是否满足
     */
    private boolean evaluateAlert(AlertRule rule) {
        Map<String, Object> quote = dataFetcher.getRealtimeQuote(rule.getStockCode());
        if (quote == null || quote.isEmpty()) return false;

        Object priceObj = quote.get("current_price");
        if (priceObj == null) return false;
        double currentPrice = ((Number) priceObj).doubleValue();

        Object changePctObj = quote.get("change_pct");
        double changePct = changePctObj != null ? ((Number) changePctObj).doubleValue() : 0;

        Object volumeObj = quote.get("volume");
        long volume = volumeObj != null ? ((Number) volumeObj).longValue() : 0;

        switch (rule.getAlertType()) {
            case "price_above":
                return currentPrice >= rule.getThresholdValue();
            case "price_below":
                return currentPrice <= rule.getThresholdValue();
            case "change_pct_above":
                return changePct >= rule.getThresholdValue();
            case "change_pct_below":
                return changePct <= rule.getThresholdValue();
            case "volume_above":
                return volume >= rule.getThresholdValue().longValue();
            default:
                return false;
        }
    }

    /**
     * 触发告警 - 更新状态并推送通知
     */
    private void triggerAlert(AlertRule rule) {
        log.info("告警触发: {} - {} - 阈值: {}", rule.getStockCode(), rule.getAlertType(), rule.getThresholdValue());

        // 持久化触发记录
        AlertTrigger trigger = new AlertTrigger();
        trigger.setRuleId(rule.getId());
        trigger.setTarget(rule.getStockCode());
        trigger.setDisplayTarget(rule.getStockName() != null ? rule.getStockName() + "(" + rule.getStockCode() + ")" : rule.getStockCode());
        trigger.setStatus("triggered");
        trigger.setThresholdValue(rule.getThresholdValue());
        trigger.setMessage(String.format("%s %s", rule.getAlertType(), rule.getThresholdValue()));
        triggerRepo.save(trigger);

        rule.setTriggered(true);
        rule.setLastTriggeredAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(rule.getOneShot())) {
            rule.setEnabled(false);
        }
        alertRepo.save(rule);

        // 推送通知并记录
        String title = String.format("⚠️ 告警: %s(%s)", rule.getStockName(), rule.getStockCode());
        String content = String.format("触发条件: %s %s\n阈值: %s\n备注: %s",
                rule.getAlertType(), rule.getThresholdValue(),
                rule.getThresholdValue(), rule.getNote() != null ? rule.getNote() : "");
        boolean success = true;
        try {
            notificationService.sendMessage(title, content);
        } catch (Exception e) {
            success = false;
            log.warn("告警通知发送失败: {}", e.getMessage());
        }

        // 持久化通知记录
        AlertNotification notif = new AlertNotification();
        notif.setTriggerId(trigger.getId());
        notif.setChannel(rule.getNotifyChannels() != null ? rule.getNotifyChannels() : "default");
        notif.setSuccess(success);
        notifRepo.save(notif);
    }
}
