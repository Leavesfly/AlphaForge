package io.leavesfly.alphaforge.application.strategy.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.leavesfly.alphaforge.application.autonomy.AutonomyAuditLog;
import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.config.AutonomyConfig;
import io.leavesfly.alphaforge.config.EnvVarProvider;
import io.leavesfly.alphaforge.domain.model.entity.strategy.CustomStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("StrategyParamWriteBackService 参数写回")
class StrategyParamWriteBackServiceTest {

    private StrategyLifecycleService lifecycle;
    private StrategyParamWriteBackService writeBack;
    private ObjectMapper yamlMapper;

    @BeforeEach
    void setUp() {
        EnvVarProvider env = mock(EnvVarProvider.class);
        when(env.getBool(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(env.get(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(env.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        AutonomyConfig config = new AutonomyConfig(env);
        config.init();
        AutonomyPolicy policy = new AutonomyPolicy(config, new AutonomyAuditLog());

        lifecycle = mock(StrategyLifecycleService.class);
        yamlMapper = new ObjectMapper(new YAMLFactory());
        writeBack = new StrategyParamWriteBackService(lifecycle, yamlMapper, policy);
    }

    @Test
    void mergesParamsAndUpdates() throws Exception {
        CustomStrategy existing = new CustomStrategy();
        existing.setStrategyId("custom_ma");
        existing.setLabel("自定义均线");
        existing.setDescription("desc");
        existing.setLifecycleState("TESTING");
        existing.setYamlContent("""
                id: custom_ma
                label: 自定义均线
                backtest:
                  parameters:
                    fast_period: 5
                    slow_period: 20
                """);

        when(lifecycle.findById("custom_ma")).thenReturn(existing);
        ArgumentCaptor<String> yamlCap = ArgumentCaptor.forClass(String.class);
        when(lifecycle.update(eq("custom_ma"), yamlCap.capture(), any(), any(), eq("auto-opt")))
                .thenAnswer(inv -> {
                    CustomStrategy u = new CustomStrategy();
                    u.setStrategyId("custom_ma");
                    u.setLifecycleState("TESTING");
                    u.setYamlContent(inv.getArgument(1));
                    return u;
                });

        CustomStrategy updated = writeBack.apply("custom_ma",
                Map.of("fast_period", 8, "slow_period", 30), "auto-opt");

        assertNotNull(updated);
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = yamlMapper.readValue(yamlCap.getValue(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> bt = (Map<String, Object>) raw.get("backtest");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) bt.get("parameters");
        assertEquals(8, ((Number) params.get("fast_period")).intValue());
        assertEquals(30, ((Number) params.get("slow_period")).intValue());
        verify(lifecycle, never()).transition(anyString(), any());
    }

    @Test
    void publishedDemotesBeforeUpdate() {
        CustomStrategy existing = new CustomStrategy();
        existing.setStrategyId("custom_ma");
        existing.setLabel("自定义均线");
        existing.setLifecycleState("PUBLISHED");
        existing.setYamlContent("""
                id: custom_ma
                backtest:
                  parameters:
                    fast_period: 5
                """);

        when(lifecycle.findById("custom_ma")).thenReturn(existing);
        when(lifecycle.transition("custom_ma", StrategyLifecycleState.TESTING))
                .thenAnswer(inv -> {
                    existing.setLifecycleState("TESTING");
                    return existing;
                });
        when(lifecycle.update(eq("custom_ma"), anyString(), any(), any(), anyString()))
                .thenAnswer(inv -> {
                    CustomStrategy u = new CustomStrategy();
                    u.setStrategyId("custom_ma");
                    u.setLifecycleState("TESTING");
                    u.setYamlContent(inv.getArgument(1));
                    return u;
                });

        writeBack.apply("custom_ma", Map.of("fast_period", 10), "auto-opt");
        verify(lifecycle).transition("custom_ma", StrategyLifecycleState.TESTING);
        verify(lifecycle).update(eq("custom_ma"), anyString(), any(), any(), eq("auto-opt"));
    }

    @Test
    void builtinReturnsNull() {
        when(lifecycle.findById("ma_golden_cross")).thenReturn(null);
        assertNull(writeBack.apply("ma_golden_cross", Map.of("fast_period", 5), "auto-opt"));
    }
}
