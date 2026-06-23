package io.leavesfly.stock.service;

import io.leavesfly.stock.dataprovider.DataFetcherManager;
import io.leavesfly.stock.model.entity.AlertRule;
import io.leavesfly.stock.notification.NotificationService;
import io.leavesfly.stock.repository.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 告警服务 - 条件触发和监控
 * 
 * 对应Python版本的 src/services/alert_service.py + alert_worker.py
 * 功能:
 * 1. 管理告警规则(CRUD)
 * 2. 定期轮询检查告警条件
 * 3. 触发后通知推送
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private final AlertRuleRepository alertRepo;
    private final DataFetcherManager dataFetcher;
    private final NotificationService notificationService;

    public AlertService(AlertRuleRepository alertRepo, DataFetcherManager dataFetcher,
                       NotificationService notificationService) {
        this.alertRepo = alertRepo;
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
        return alertRepo.findById(id).map(rule -> {
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

    /**
     * 告警工作线程 - 每分钟检查一次
     * 对应Python版本的alert_worker.py
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
        
        rule.setTriggered(true);
        rule.setLastTriggeredAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(rule.getOneShot())) {
            rule.setEnabled(false);
        }
        alertRepo.save(rule);

        // 推送通知
        String title = String.format("⚠️ 告警: %s(%s)", rule.getStockName(), rule.getStockCode());
        String content = String.format("触发条件: %s %s\n阈值: %s\n备注: %s",
                rule.getAlertType(), rule.getThresholdValue(),
                rule.getThresholdValue(), rule.getNote() != null ? rule.getNote() : "");
        notificationService.sendMessage(title, content);
    }
}
