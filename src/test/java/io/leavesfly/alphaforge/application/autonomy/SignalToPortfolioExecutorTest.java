package io.leavesfly.alphaforge.application.autonomy;

import io.leavesfly.alphaforge.config.AutonomyConfig;
import io.leavesfly.alphaforge.config.EnvVarProvider;
import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignal;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.service.port.OrderExecutionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SignalToPortfolioExecutor 纸面执行")
class SignalToPortfolioExecutorTest {

    private SignalToPortfolioExecutor executor;
    private OrderExecutionPort port;
    private AutonomyConfig config;

    @BeforeEach
    void setUp() {
        EnvVarProvider env = mock(EnvVarProvider.class);
        when(env.getBool(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(env.get(anyString(), anyString())).thenAnswer(inv -> {
            if ("AUTONOMY_PAPER_ACCOUNT_ID".equals(inv.getArgument(0))) return "1";
            return inv.getArgument(1);
        });
        when(env.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        config = new AutonomyConfig(env);
        config.init();
        AutonomyPolicy policy = new AutonomyPolicy(config, new AutonomyAuditLog());
        port = mock(OrderExecutionPort.class);
        TradingRiskGuard guard = new TradingRiskGuard(policy, port);
        MarketDataPort market = mock(MarketDataPort.class);
        when(market.getRealtimeQuote("600519")).thenReturn(Map.of("price", 100.0));
        when(port.getAccountEquity(1L)).thenReturn(Map.of(
                "totalAssets", 100_000.0,
                "cashBalance", 100_000.0
        ));
        when(port.getPositionQuantity(1L, "600519")).thenReturn(0);
        when(port.buy(eq(1L), eq("600519"), anyInt())).thenReturn(Map.of("ok", true));
        when(port.sell(eq(1L), eq("600519"), anyInt())).thenReturn(Map.of("ok", true));
        executor = new SignalToPortfolioExecutor(policy, guard, port, market);
    }

    @Test
    void buyOpensPosition() {
        DecisionSignal signal = new DecisionSignal();
        signal.setId(10L);
        signal.setStockCode("600519");
        signal.setAction("buy");

        Map<String, Object> result = executor.execute(signal);
        assertEquals(false, result.get("skipped"));
        assertEquals("buy", result.get("action"));
        verify(port).buy(eq(1L), eq("600519"), eq(100)); // 10% of 100k / 100 = 100 shares
    }

    @Test
    void sellClosesPosition() {
        when(port.getPositionQuantity(1L, "600519")).thenReturn(200);
        DecisionSignal signal = new DecisionSignal();
        signal.setId(11L);
        signal.setStockCode("600519");
        signal.setAction("sell");

        Map<String, Object> result = executor.execute(signal);
        assertEquals(false, result.get("skipped"));
        verify(port).sell(1L, "600519", 200);
    }

    @Test
    void holdSkipped() {
        DecisionSignal signal = new DecisionSignal();
        signal.setId(12L);
        signal.setStockCode("600519");
        signal.setAction("hold");
        Map<String, Object> result = executor.execute(signal);
        assertEquals(true, result.get("skipped"));
        verify(port, never()).buy(any(), any(), anyInt());
    }
}
