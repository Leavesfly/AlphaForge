package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.autonomy.AutonomyAuditLog;
import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.application.autonomy.TradingRiskGuard;
import io.leavesfly.alphaforge.application.service.portfolio.PaperTradingService;
import io.leavesfly.alphaforge.config.AutonomyConfig;
import io.leavesfly.alphaforge.config.EnvVarProvider;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioAccount;
import io.leavesfly.alphaforge.domain.service.port.OrderExecutionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AutonomyControlTool Chat 自主控制")
class AutonomyControlToolTest {

    private AutonomyControlTool tool;
    private AutonomyConfig config;
    private TradingRiskGuard riskGuard;
    private PaperTradingService paperTrading;

    @BeforeEach
    void setUp() {
        EnvVarProvider env = mock(EnvVarProvider.class);
        when(env.getBool(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(env.get(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(env.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        config = new AutonomyConfig(env);
        config.init();
        AutonomyPolicy policy = new AutonomyPolicy(config, new AutonomyAuditLog());
        OrderExecutionPort port = mock(OrderExecutionPort.class);
        when(port.getAccountEquity(any())).thenReturn(Map.of("totalAssets", 100000.0, "cashBalance", 100000.0));
        riskGuard = new TradingRiskGuard(policy, port);
        paperTrading = mock(PaperTradingService.class);
        tool = new AutonomyControlTool(config, policy, riskGuard, paperTrading);
    }

    @Test
    void statusShowsDefaults() throws Exception {
        String out = tool.execute(Map.of("action", "status"));
        assertTrue(out.contains("enabled: false"));
        assertTrue(out.contains("halted: false"));
    }

    @Test
    void setFlagsAutoEnablesParent() throws Exception {
        String out = tool.execute(Map.of(
                "action", "set_flags",
                "auto_execute_signals", true
        ));
        assertTrue(config.isEnabled());
        assertTrue(config.isAutoExecuteSignals());
        assertTrue(out.contains("auto_execute_signals=true"));
    }

    @Test
    void haltAndResume() throws Exception {
        tool.execute(Map.of("action", "halt", "reason", "test"));
        assertTrue(riskGuard.isHalted());
        tool.execute(Map.of("action", "resume"));
        assertFalse(riskGuard.isHalted());
    }

    @Test
    void bindByName() throws Exception {
        PortfolioAccount acc = new PortfolioAccount();
        acc.setId(7L);
        acc.setName("L4纸面");
        acc.setCashBalance(1_000_000.0);
        acc.setMarket("cn");
        when(paperTrading.getPaperAccounts()).thenReturn(List.of(acc));

        String out = tool.execute(Map.of("action", "bind_account", "account_name", "L4"));
        assertEquals(7L, config.getPaperAccountId());
        assertTrue(out.contains("#7"));
    }

    @Test
    void auditAfterAction() throws Exception {
        tool.execute(Map.of("action", "set_flags", "enabled", true));
        String audit = tool.execute(Map.of("action", "audit", "limit", 5));
        assertTrue(audit.contains("chat_set_flags") || audit.contains("自主审计"));
    }

    @Test
    void unknownActionFails() {
        assertThrows(ToolException.class, () -> tool.execute(Map.of("action", "boom")));
    }
}
