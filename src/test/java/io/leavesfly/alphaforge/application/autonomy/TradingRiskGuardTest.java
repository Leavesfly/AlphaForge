package io.leavesfly.alphaforge.application.autonomy;

import io.leavesfly.alphaforge.config.AutonomyConfig;
import io.leavesfly.alphaforge.config.EnvVarProvider;
import io.leavesfly.alphaforge.domain.service.port.OrderExecutionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TradingRiskGuard 风控闸")
class TradingRiskGuardTest {

    private TradingRiskGuard guard;
    private OrderExecutionPort port;

    @BeforeEach
    void setUp() {
        EnvVarProvider env = mock(EnvVarProvider.class);
        when(env.getBool(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(env.get(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(env.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        AutonomyConfig config = new AutonomyConfig(env);
        config.init();
        AutonomyPolicy policy = new AutonomyPolicy(config, new AutonomyAuditLog());
        port = mock(OrderExecutionPort.class);
        when(port.getAccountEquity(1L)).thenReturn(Map.of(
                "totalAssets", 100_000.0,
                "cashBalance", 100_000.0
        ));
        guard = new TradingRiskGuard(policy, port);
    }

    @Test
    void haltBlocksTrade() {
        guard.halt("test");
        assertTrue(guard.isHalted());
        assertThrows(IllegalStateException.class, () -> guard.assertCanTrade(1L));
    }

    @Test
    void resumeAllowsTrade() {
        guard.halt("test");
        guard.resume();
        assertFalse(guard.isHalted());
        assertDoesNotThrow(() -> guard.assertCanTrade(1L));
    }

    @Test
    void nullAccountRejected() {
        assertThrows(IllegalStateException.class, () -> guard.assertCanTrade(null));
    }

    @Test
    void drawdownTriggersHalt() {
        when(port.getAccountEquity(1L)).thenReturn(Map.of(
                "totalAssets", 100_000.0,
                "cashBalance", 100_000.0
        ));
        guard.assertCanTrade(1L); // set peak

        when(port.getAccountEquity(1L)).thenReturn(Map.of(
                "totalAssets", 80_000.0, // 20% DD > 15%
                "cashBalance", 80_000.0
        ));
        assertThrows(IllegalStateException.class, () -> guard.assertCanTrade(1L));
        assertTrue(guard.isHalted());
    }
}
