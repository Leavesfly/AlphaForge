package io.leavesfly.alphaforge.application.service.alert;

import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.model.entity.alert.AlertRule;
import io.leavesfly.alphaforge.domain.service.port.NotificationPort;
import io.leavesfly.alphaforge.domain.repository.alert.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 告警后台工作线程
 * 定时扫描所有告警规则，触发满足条件的告警
 */
@Service
public class AlertWorker {

    private static final Logger log = LoggerFactory.getLogger(AlertWorker.class);

    private final AlertRuleRepository alertRuleRepo;
    private final AlertIndicators indicators;
    private final MarketDataPort dataFetcher;
    private final NotificationPort notificationService;

    public AlertWorker(AlertRuleRepository alertRuleRepo, AlertIndicators indicators,
                       MarketDataPort dataFetcher, NotificationPort notificationService) {
        this.alertRuleRepo = alertRuleRepo;
        this.indicators = indicators;
        this.dataFetcher = dataFetcher;
        this.notificationService = notificationService;
    }

    /**
     * 每5分钟扫描一次告警规则
     */
    @Scheduled(fixedRate = 300000)
    public void scanAlerts() {
        List<AlertRule> rules = alertRuleRepo.findByEnabled(true);
        if (rules.isEmpty()) return;

        log.debug("告警扫描: {} 条规则", rules.size());
        for (AlertRule rule : rules) {
            try {
                boolean triggered = evaluateRule(rule);
                if (triggered) {
                    fireAlert(rule);
                }
            } catch (Exception e) {
                log.warn("告警规则 {} 评估失败: {}", rule.getId(), e.getMessage());
            }
        }
    }

    /**
     * 评估单条规则
     */
    private boolean evaluateRule(AlertRule rule) {
        Map<String, Object> quote = dataFetcher.getRealtimeQuote(rule.getStockCode());
        if (quote == null || quote.isEmpty()) return false;

        double currentPrice = extractPrice(quote);
        if (currentPrice <= 0) return false;

        String condition = rule.getAlertType();
        double threshold = rule.getThresholdValue() != null ? rule.getThresholdValue() : 0;

        switch (condition) {
            case "price_above": return currentPrice > threshold;
            case "price_below": return currentPrice < threshold;
            case "change_pct_above": return extractChangePct(quote) > threshold;
            case "change_pct_below": return extractChangePct(quote) < threshold;
            case "volume_ratio_above": return indicators.volumeRatio(rule.getStockCode()) > threshold;
            case "macd_golden_cross": return indicators.isMacdGoldenCross(rule.getStockCode());
            case "macd_death_cross": return indicators.isMacdDeathCross(rule.getStockCode());
            case "rsi_overbought": return indicators.rsi(rule.getStockCode(), 14) > threshold;
            case "rsi_oversold": return indicators.rsi(rule.getStockCode(), 14) < threshold;
            case "ma_breakout": return indicators.isMaBreakout(rule.getStockCode(), (int) threshold);
            default:
                log.warn("未知告警条件: {}", condition);
                return false;
        }
    }

    /**
     * 触发告警通知
     */
    private void fireAlert(AlertRule rule) {
        if (rule.getLastTriggeredAt() != null &&
                rule.getLastTriggeredAt().plusMinutes(30).isAfter(LocalDateTime.now())) {
            return;
        }
        String title = String.format("⚠️ 告警: %s(%s) - %s",
                rule.getStockName(), rule.getStockCode(), rule.getAlertType());
        String content = String.format("条件: %s 阈值: %.2f\n触发时间: %s",
                rule.getAlertType(), rule.getThresholdValue() != null ? rule.getThresholdValue() : 0.0,
                LocalDateTime.now());
        notificationService.sendMessage(title, content);
        rule.setLastTriggeredAt(LocalDateTime.now());
        alertRuleRepo.save(rule);
        log.info("告警触发: {} - {}", rule.getStockCode(), rule.getAlertType());
    }

    private double extractPrice(Map<String, Object> quote) {
        Object p = quote.get("current_price");
        return p instanceof Number ? ((Number) p).doubleValue() : 0;
    }

    private double extractChangePct(Map<String, Object> quote) {
        Object p = quote.get("change_pct");
        return p instanceof Number ? ((Number) p).doubleValue() : 0;
    }
}
