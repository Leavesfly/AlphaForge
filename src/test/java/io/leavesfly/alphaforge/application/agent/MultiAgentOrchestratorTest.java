package io.leavesfly.alphaforge.application.agent;

import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MultiAgentOrchestrator 单元测试
 */
class MultiAgentOrchestratorTest {

    @Test
    @DisplayName("无子 Agent 时返回空结果")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testNoSubAgents() {
        LlmPort llmService = mock(LlmPort.class);
        when(llmService.chat(anyString(), anyString())).thenReturn("综合分析结果");

        MultiAgentOrchestrator orchestrator = new MultiAgentOrchestrator(llmService, List.of());

        MultiAgentOrchestrator.OrchestrationResult result =
                orchestrator.orchestrate("600519", "贵州茅台", Map.of(), 10);

        assertNotNull(result);
        assertTrue(result.agentResults().isEmpty());
        assertEquals("综合分析结果", result.synthesis());
    }

    @Test
    @DisplayName("单个子 Agent 正常分析")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testSingleAgent() {
        LlmPort llmService = mock(LlmPort.class);
        when(llmService.chat(anyString(), anyString())).thenReturn("综合结论");

        SubAgent agent = mock(SubAgent.class);
        when(agent.getName()).thenReturn("TechnicalAgent");
        when(agent.getRole()).thenReturn("技术面");
        when(agent.analyze(anyString(), anyString(), anyMap())).thenReturn(
                new SubAgent.AgentResult("TechnicalAgent", "技术面", "技术分析内容",
                        "buy", 75, "高", List.of("MA金叉"), 100L));

        MultiAgentOrchestrator orchestrator = new MultiAgentOrchestrator(llmService, List.of(agent));

        MultiAgentOrchestrator.OrchestrationResult result =
                orchestrator.orchestrate("600519", "贵州茅台", Map.of(), 10);

        assertNotNull(result);
        assertEquals(1, result.agentResults().size());
        assertEquals("TechnicalAgent", result.agentResults().get(0).agentName);
        assertEquals("综合结论", result.synthesis());
        assertTrue(result.allSucceeded());
    }

    @Test
    @DisplayName("子 Agent 超时时返回空结果")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testAgentTimeout() {
        LlmPort llmService = mock(LlmPort.class);
        when(llmService.chat(anyString(), anyString())).thenReturn("综合结论");

        SubAgent agent = mock(SubAgent.class);
        when(agent.getName()).thenReturn("SlowAgent");
        when(agent.getRole()).thenReturn("技术面");
        when(agent.analyze(anyString(), anyString(), anyMap())).thenAnswer(invocation -> {
            Thread.sleep(5000); // 模拟超时
            return new SubAgent.AgentResult("SlowAgent", "技术面", "内容",
                    "buy", 70, "中等", List.of(), 5000L);
        });

        MultiAgentOrchestrator orchestrator = new MultiAgentOrchestrator(llmService, List.of(agent));

        MultiAgentOrchestrator.OrchestrationResult result =
                orchestrator.orchestrate("600519", "贵州茅台", Map.of(), 2);

        assertNotNull(result);
        assertEquals(1, result.agentResults().size());
        // 超时的 Agent 结果应为空
        assertTrue(result.agentResults().get(0).analysis == null
                || result.agentResults().get(0).analysis.isEmpty());
        assertFalse(result.allSucceeded());
    }

    @Test
    @DisplayName("getByRole 查找指定角色的结果")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testGetByRole() {
        LlmPort llmService = mock(LlmPort.class);
        when(llmService.chat(anyString(), anyString())).thenReturn("结论");

        SubAgent agent = mock(SubAgent.class);
        when(agent.getName()).thenReturn("RiskAgent");
        when(agent.getRole()).thenReturn("风控");
        when(agent.analyze(anyString(), anyString(), anyMap())).thenReturn(
                new SubAgent.AgentResult("RiskAgent", "风控", "风控分析",
                        "neutral", 50, "中等", List.of("波动率高"), 50L));

        MultiAgentOrchestrator orchestrator = new MultiAgentOrchestrator(llmService, List.of(agent));

        MultiAgentOrchestrator.OrchestrationResult result =
                orchestrator.orchestrate("000001", "平安银行", Map.of(), 10);

        Optional<SubAgent.AgentResult> riskResult = result.getByRole("风控");
        assertTrue(riskResult.isPresent());
        assertEquals("风控分析", riskResult.get().analysis);

        Optional<SubAgent.AgentResult> notFound = result.getByRole("不存在");
        assertTrue(notFound.isEmpty());
    }
}
