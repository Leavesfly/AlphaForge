package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.agent.kernel.NextStepAdvisor;
import io.leavesfly.alphaforge.application.service.decision.DecisionScoreService;
import io.leavesfly.alphaforge.domain.service.decision.DecisionTestBars;
import io.leavesfly.alphaforge.domain.service.decision.LightsResult;
import io.leavesfly.alphaforge.domain.service.decision.ThreeLightsEngine;
import io.leavesfly.alphaforge.domain.service.decision.ThreeLightsInput;
import io.leavesfly.alphaforge.presentation.api.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DecisionController 买点三灯 API 测试")
class DecisionControllerTest {

    @Mock
    private DecisionScoreService decisionScoreService;

    private DecisionController controller;

    @BeforeEach
    void setUp() {
        controller = new DecisionController(decisionScoreService, new NextStepAdvisor());
    }

    private LightsResult sampleResult() {
        return ThreeLightsEngine.evaluate(ThreeLightsInput.builder("600519")
                .stockName("贵州茅台")
                .history(DecisionTestBars.alternating(300, 100, 0.008, -0.004))
                .build());
    }

    @Test
    @DisplayName("GET /decision/score 委托服务并包装 ApiResponse")
    void scoreDelegatesAndWraps() {
        LightsResult result = sampleResult();
        when(decisionScoreService.score(eq("600519"), eq(null))).thenReturn(result);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.score("600519", null);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals("600519", response.getBody().getData().get("stockCode"));
        assertEquals("trend_entry", response.getBody().getData().get("verdict"));
        assertNotNull(response.getBody().getData().get("lights"));
        // 链式引导：trend_entry 附模拟盘跟踪/止损告警建议
        Object nextSteps = response.getBody().getData().get("next_steps");
        assertTrue(nextSteps instanceof List<?>);
        List<?> steps = (List<?>) nextSteps;
        assertTrue(!steps.isEmpty());
        assertTrue(steps.get(0) instanceof Map<?, ?> first
                && "paper_trading_track".equals(first.get("action")));
    }

    @Test
    @DisplayName("cost 参数透传触发持仓视角")
    void costParamPassedThrough() {
        LightsResult result = sampleResult();
        when(decisionScoreService.score("600519", 150.0)).thenReturn(result);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.score("600519", 150.0);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("trend_entry", response.getBody().getData().get("verdict"));
    }
}
