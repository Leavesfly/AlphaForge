package io.leavesfly.alphaforge.application.autonomy;

import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignal;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.service.port.OrderExecutionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将决策信号转为纸面买卖（受 AutonomyPolicy + TradingRiskGuard 约束）。
 */
@Service
public class SignalToPortfolioExecutor {

    private static final Logger log = LoggerFactory.getLogger(SignalToPortfolioExecutor.class);

    private final AutonomyPolicy policy;
    private final TradingRiskGuard riskGuard;
    private final OrderExecutionPort orderExecutionPort;
    private final MarketDataPort marketDataPort;

    public SignalToPortfolioExecutor(AutonomyPolicy policy,
                                     TradingRiskGuard riskGuard,
                                     OrderExecutionPort orderExecutionPort,
                                     MarketDataPort marketDataPort) {
        this.policy = policy;
        this.riskGuard = riskGuard;
        this.orderExecutionPort = orderExecutionPort;
        this.marketDataPort = marketDataPort;
    }

    /**
     * 执行单个信号。返回执行摘要；跳过时返回 skipped=true。
     */
    public Map<String, Object> execute(DecisionSignal signal) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signal_id", signal != null ? signal.getId() : null);

        if (signal == null || signal.getStockCode() == null) {
            result.put("skipped", true);
            result.put("reason", "invalid signal");
            return result;
        }

        Long accountId = policy.paperAccountId();
        if (accountId == null) {
            result.put("skipped", true);
            result.put("reason", "paper account not configured");
            return result;
        }

        String action = signal.getAction() != null ? signal.getAction().toLowerCase() : "hold";
        if ("hold".equals(action)) {
            result.put("skipped", true);
            result.put("reason", "hold action");
            return result;
        }

        riskGuard.assertCanTrade(accountId);

        try {
            if ("buy".equals(action)) {
                return executeBuy(accountId, signal, result);
            }
            if ("sell".equals(action)) {
                return executeSell(accountId, signal, result);
            }
            result.put("skipped", true);
            result.put("reason", "unsupported action: " + action);
            return result;
        } catch (Exception e) {
            log.warn("信号执行失败 id={}: {}", signal.getId(), e.getMessage());
            result.put("skipped", true);
            result.put("error", e.getMessage());
            policy.audit("signal_exec_fail", "signal", String.valueOf(signal.getId()),
                    "active", "active", e.getMessage());
            return result;
        }
    }

    private Map<String, Object> executeBuy(Long accountId, DecisionSignal signal,
                                           Map<String, Object> result) {
        int existing = orderExecutionPort.getPositionQuantity(accountId, signal.getStockCode());
        if (existing > 0) {
            result.put("skipped", true);
            result.put("reason", "already holding " + existing);
            return result;
        }

        Map<String, Object> equity = orderExecutionPort.getAccountEquity(accountId);
        double cash = toDouble(equity.get("cashBalance"));
        double price = resolvePrice(signal.getStockCode());
        if (price <= 0 || cash <= 0) {
            result.put("skipped", true);
            result.put("reason", "invalid price or cash");
            return result;
        }

        double budget = cash * policy.positionPct();
        int qty = ((int) (budget / price) / 100) * 100;
        if (qty < 100) {
            result.put("skipped", true);
            result.put("reason", "insufficient cash for 100 shares");
            return result;
        }

        Map<String, Object> trade = orderExecutionPort.buy(accountId, signal.getStockCode(), qty);
        result.put("skipped", false);
        result.put("action", "buy");
        result.put("quantity", qty);
        result.put("trade", trade);
        policy.audit("signal_exec_buy", "signal", String.valueOf(signal.getId()),
                "active", "executed",
                signal.getStockCode() + " x" + qty);
        return result;
    }

    private Map<String, Object> executeSell(Long accountId, DecisionSignal signal,
                                            Map<String, Object> result) {
        int qty = orderExecutionPort.getPositionQuantity(accountId, signal.getStockCode());
        if (qty <= 0) {
            result.put("skipped", true);
            result.put("reason", "no position");
            return result;
        }
        Map<String, Object> trade = orderExecutionPort.sell(accountId, signal.getStockCode(), qty);
        result.put("skipped", false);
        result.put("action", "sell");
        result.put("quantity", qty);
        result.put("trade", trade);
        policy.audit("signal_exec_sell", "signal", String.valueOf(signal.getId()),
                "active", "executed",
                signal.getStockCode() + " x" + qty);
        return result;
    }

    private double resolvePrice(String stockCode) {
        try {
            Map<String, Object> quote = marketDataPort.getRealtimeQuote(stockCode);
            if (quote != null) {
                Object p = quote.get("price");
                if (p == null) p = quote.get("current_price");
                if (p instanceof Number n) return n.doubleValue();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return 0;
    }
}
