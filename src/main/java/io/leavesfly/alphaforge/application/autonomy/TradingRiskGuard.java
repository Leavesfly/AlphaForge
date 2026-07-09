package io.leavesfly.alphaforge.application.autonomy;

import io.leavesfly.alphaforge.domain.service.port.OrderExecutionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 交易风控闸 — 全局 halt / 最大回撤 / 日亏损。
 */
@Component
public class TradingRiskGuard {

    private static final Logger log = LoggerFactory.getLogger(TradingRiskGuard.class);

    private final AutonomyPolicy policy;
    private final OrderExecutionPort orderExecutionPort;

    private final AtomicBoolean halted = new AtomicBoolean(false);
    private final AtomicReference<String> haltReason = new AtomicReference<>("");
    private final AtomicReference<Double> peakEquity = new AtomicReference<>(null);
    private final AtomicReference<LocalDate> dayAnchor = new AtomicReference<>(null);
    private final AtomicReference<Double> dayStartEquity = new AtomicReference<>(null);

    public TradingRiskGuard(AutonomyPolicy policy, OrderExecutionPort orderExecutionPort) {
        this.policy = policy;
        this.orderExecutionPort = orderExecutionPort;
    }

    public void halt(String reason) {
        halted.set(true);
        haltReason.set(reason != null ? reason : "manual halt");
        policy.audit("risk_halt", "trading", "global", "running", "halted", haltReason.get());
        log.warn("TradingRiskGuard HALTED: {}", haltReason.get());
    }

    public void resume() {
        halted.set(false);
        String prev = haltReason.getAndSet("");
        policy.audit("risk_resume", "trading", "global", "halted", "running", prev);
        log.info("TradingRiskGuard RESUMED");
    }

    public boolean isHalted() { return halted.get(); }
    public String getHaltReason() { return haltReason.get(); }

    /**
     * 下单前检查；不通过抛 IllegalStateException。
     */
    public void assertCanTrade(Long accountId) {
        if (halted.get()) {
            throw new IllegalStateException("交易已熔断: " + haltReason.get());
        }
        if (accountId == null) {
            throw new IllegalStateException("未配置纸面账户 autonomy.paper-account-id");
        }

        Map<String, Object> equity = orderExecutionPort.getAccountEquity(accountId);
        double totalAssets = toDouble(equity.get("totalAssets"));
        if (totalAssets <= 0) {
            totalAssets = toDouble(equity.get("cashBalance"));
        }

        // 峰值回撤
        Double peak = peakEquity.get();
        if (peak == null || totalAssets > peak) {
            peakEquity.set(totalAssets);
            peak = totalAssets;
        }
        if (peak > 0) {
            double ddPct = (peak - totalAssets) / peak * 100.0;
            if (ddPct >= policy.maxDrawdownHaltPct()) {
                halt(String.format("最大回撤 %.2f%% ≥ 阈值 %.2f%%", ddPct, policy.maxDrawdownHaltPct()));
                throw new IllegalStateException("交易已熔断: " + haltReason.get());
            }
        }

        // 日亏损
        LocalDate today = LocalDate.now();
        if (!today.equals(dayAnchor.get())) {
            dayAnchor.set(today);
            dayStartEquity.set(totalAssets);
        }
        Double start = dayStartEquity.get();
        if (start != null && start > 0) {
            double dayLossPct = (start - totalAssets) / start * 100.0;
            if (dayLossPct >= policy.dailyLossHaltPct()) {
                halt(String.format("日亏损 %.2f%% ≥ 阈值 %.2f%%", dayLossPct, policy.dailyLossHaltPct()));
                throw new IllegalStateException("交易已熔断: " + haltReason.get());
            }
        }
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return 0;
    }
}
