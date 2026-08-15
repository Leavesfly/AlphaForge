package io.leavesfly.alphaforge.application.agent.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NextStepAdvisor 链式引导规则测试")
class NextStepAdvisorTest {

    private final NextStepAdvisor advisor = new NextStepAdvisor();

    private AgentResult decisionResult(String verdict) {
        return AgentResult.ok(AgentTaskType.DECISION_SCORE)
                .data("decisionResult", Map.of("verdict", verdict))
                .build();
    }

    // ===== advise(AgentResult) =====

    @Test
    @DisplayName("null 结果返回空建议")
    void nullResultReturnsEmpty() {
        assertTrue(advisor.advise(null).isEmpty());
    }

    @Test
    @DisplayName("失败结果给数据源体检修复建议")
    void failureSuggestsDoctor() {
        List<NextStep> steps = advisor.advise(AgentResult.fail(AgentTaskType.DECISION_SCORE, "行情源超时"));
        assertEquals(1, steps.size());
        assertEquals("check_datasource", steps.get(0).action());
        assertEquals("loop-monitor", steps.get(0).endpoint());
    }

    @Test
    @DisplayName("DECISION_SCORE trend_entry 建议模拟盘与止损告警")
    void decisionScoreTrendEntry() {
        List<NextStep> steps = advisor.advise(decisionResult("trend_entry"));
        assertEquals(2, steps.size());
        assertEquals("paper_trading_track", steps.get(0).action());
        assertEquals("set_stop_loss_alert", steps.get(1).action());
    }

    @Test
    @DisplayName("STRATEGY_GENERATE 建议参数寻优")
    void strategyGenerateSuggestsOptimize() {
        List<NextStep> steps = advisor.advise(AgentResult.ok(AgentTaskType.STRATEGY_GENERATE).build());
        assertEquals(1, steps.size());
        assertEquals("optimize_strategy", steps.get(0).action());
    }

    @Test
    @DisplayName("STRATEGY_OPTIMIZE 建议回测验证")
    void strategyOptimizeSuggestsBacktest() {
        List<NextStep> steps = advisor.advise(AgentResult.ok(AgentTaskType.STRATEGY_OPTIMIZE).build());
        assertEquals(1, steps.size());
        assertEquals("backtest_verify", steps.get(0).action());
    }

    @Test
    @DisplayName("NL_SCREENING 建议对 Top 候选逐只三灯评估")
    void screeningSuggestsDecisionLoop() {
        List<NextStep> steps = advisor.advise(AgentResult.ok(AgentTaskType.NL_SCREENING).build());
        assertEquals(1, steps.size());
        assertEquals("decision_top_candidates", steps.get(0).action());
        assertEquals("research/decision", steps.get(0).endpoint());
    }

    @Test
    @DisplayName("STOCK_ANALYSIS 低分加回测建议")
    void lowScoreAnalysisSuggestsBacktest() {
        AgentResult result = AgentResult.ok(AgentTaskType.STOCK_ANALYSIS)
                .data("analysisResult", Map.of("totalScore", 45))
                .build();
        List<NextStep> steps = advisor.advise(result);
        assertEquals(2, steps.size());
        assertEquals("backtest_strategy", steps.get(0).action());
        assertEquals("decision_score", steps.get(1).action());
    }

    @Test
    @DisplayName("STOCK_ANALYSIS 高分仅建议三灯评估")
    void highScoreAnalysisSkipsBacktest() {
        AgentResult result = AgentResult.ok(AgentTaskType.STOCK_ANALYSIS)
                .data("analysisResult", Map.of("totalScore", 80))
                .build();
        List<NextStep> steps = advisor.advise(result);
        assertEquals(1, steps.size());
        assertEquals("decision_score", steps.get(0).action());
    }

    @Test
    @DisplayName("CHAT 无固定建议（建议走工具维度 adviseForChatTools）")
    void chatHasNoFixedSteps() {
        assertTrue(advisor.advise(AgentResult.ok(AgentTaskType.CHAT).build()).isEmpty());
    }

    // ===== adviseForDecision 七态 =====

    @Test
    @DisplayName("七态矩阵建议全覆盖（含 null/未知态容错）")
    void verdictMatrixMapping() {
        assertEquals(2, advisor.adviseForDecision("trend_entry", "600519").size());
        assertEquals(2, advisor.adviseForDecision("trend_only", "600519").size());
        assertEquals(2, advisor.adviseForDecision("wait_pullback", null).size());
        assertEquals(1, advisor.adviseForDecision("left_watch", null).size());
        assertEquals(1, advisor.adviseForDecision("reduce_risk", null).size());
        assertEquals(1, advisor.adviseForDecision("avoid", null).size());
        assertEquals(1, advisor.adviseForDecision("unrated", null).size());
        assertTrue(advisor.adviseForDecision(null, null).isEmpty());
        assertTrue(advisor.adviseForDecision("unknown", null).isEmpty());
    }

    @Test
    @DisplayName("reduce_risk 建议查看持仓（减仓纪律而非预测下跌）")
    void reduceRiskSuggestsReview() {
        List<NextStep> steps = advisor.adviseForDecision("reduce_risk", "600519");
        assertEquals("review_positions", steps.get(0).action());
        assertEquals("mine/paper-trading", steps.get(0).endpoint());
    }

    // ===== adviseForChatTools =====

    @Test
    @DisplayName("工具维度：decision_score 建议打开决策台")
    void chatToolDecisionScore() {
        List<NextStep> steps = advisor.adviseForChatTools(List.of("decision_score"));
        assertEquals(1, steps.size());
        assertEquals("open_decision_desk", steps.get(0).action());
    }

    @Test
    @DisplayName("工具维度：generate_strategy 建议参数寻优")
    void chatToolStrategy() {
        List<NextStep> steps = advisor.adviseForChatTools(List.of("generate_strategy", "other"));
        assertEquals(1, steps.size());
        assertEquals("optimize_strategy", steps.get(0).action());
    }

    @Test
    @DisplayName("工具维度：get_positions 建议逐只三灯体检")
    void chatToolPositions() {
        List<NextStep> steps = advisor.adviseForChatTools(List.of("get_positions"));
        assertEquals(1, steps.size());
        assertEquals("open_decision_desk", steps.get(0).action());
    }

    @Test
    @DisplayName("无工具调用或未匹配工具返回空建议")
    void noToolsNoSteps() {
        assertTrue(advisor.adviseForChatTools(null).isEmpty());
        assertTrue(advisor.adviseForChatTools(List.of()).isEmpty());
        assertTrue(advisor.adviseForChatTools(List.of("unknown_tool")).isEmpty());
    }

    // ===== NextStep / AgentResult 契约 =====

    @Test
    @DisplayName("NextStep.toMap 输出 action/label/endpoint/reason 四键契约")
    void nextStepToMapContract() {
        Map<String, Object> map = new NextStep("act", "标签", "page/x", "理由").toMap();
        assertEquals(4, map.size());
        assertEquals("act", map.get("action"));
        assertEquals("标签", map.get("label"));
        assertEquals("page/x", map.get("endpoint"));
        assertEquals("理由", map.get("reason"));
    }

    @Test
    @DisplayName("AgentResult.nextSteps 默认空、Builder 可填充、Map 形态下发")
    void agentResultNextStepsBuilder() {
        assertTrue(AgentResult.ok(AgentTaskType.CHAT).build().getNextSteps().isEmpty());

        AgentResult result = AgentResult.ok(AgentTaskType.DECISION_SCORE)
                .nextSteps(List.of(new NextStep("a", "b", "c", "d")))
                .build();
        assertEquals(1, result.getNextSteps().size());
        assertEquals("a", result.getNextSteps().get(0).action());
        assertEquals(1, result.nextStepsAsMaps().size());
        assertEquals("a", result.nextStepsAsMaps().get(0).get("action"));
    }
}
