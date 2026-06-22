package com.stock.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent编排器测试
 * 验证: 意见聚合、共识投票、输出结构
 */
class AgentOrchestratorTest {

    @Test
    @DisplayName("AnalysisOutput: 空意见返回中性")
    void testEmptyOpinions() {
        AgentOrchestrator.AnalysisOutput output = new AgentOrchestrator.AnalysisOutput("balanced", List.of());
        assertEquals("neutral", output.getConsensusSignal());
        assertEquals(50, output.getConsensusScore());
        assertEquals("低", output.getConfidence());
    }

    @Test
    @DisplayName("AnalysisOutput: 单Agent意见直接采用")
    void testSingleOpinion() {
        var opinion = new AgentOrchestrator.AgentOpinion("tech", "MA金叉+放量", "buy", 75, 0.8);
        AgentOrchestrator.AnalysisOutput output = new AgentOrchestrator.AnalysisOutput("sniper", List.of(opinion));
        assertEquals("buy", output.getConsensusSignal());
        assertEquals(75, output.getConsensusScore());
        assertEquals("高", output.getConfidence());
    }

    @Test
    @DisplayName("AnalysisOutput: 多Agent投票多数胜出")
    void testMajorityVoting() {
        var opinions = List.of(
                new AgentOrchestrator.AgentOpinion("tech", "看涨", "buy", 70, 0.7),
                new AgentOrchestrator.AgentOpinion("fundamental", "基本面好", "buy", 65, 0.6),
                new AgentOrchestrator.AgentOpinion("risk", "风险可控", "neutral", 50, 0.5)
        );
        var output = new AgentOrchestrator.AnalysisOutput("balanced", opinions);
        assertEquals("buy", output.getConsensusSignal()); // 2票buy > 1票neutral
        assertEquals(61, output.getConsensusScore()); // avg(70,65,50)
    }

    @Test
    @DisplayName("AnalysisOutput: 评分平均值")
    void testScoreAverage() {
        var opinions = List.of(
                new AgentOrchestrator.AgentOpinion("a", "r1", "buy", 80, 0.9),
                new AgentOrchestrator.AgentOpinion("b", "r2", "buy", 60, 0.7)
        );
        var output = new AgentOrchestrator.AnalysisOutput("sniper", opinions);
        assertEquals(70, output.getConsensusScore()); // (80+60)/2
    }

    @Test
    @DisplayName("AnalysisOutput: 报告格式")
    void testFullReportFormat() {
        var opinion = new AgentOrchestrator.AgentOpinion("tech", "技术面向好", "buy", 75, 0.8);
        var output = new AgentOrchestrator.AnalysisOutput("sniper", List.of(opinion));
        String report = output.getFullReport();
        assertTrue(report.contains("### tech"));
        assertTrue(report.contains("技术面向好"));
    }

    @Test
    @DisplayName("AnalysisOutput: 摘要格式")
    void testSummaryFormat() {
        var opinions = List.of(
                new AgentOrchestrator.AgentOpinion("a", "r", "buy", 80, 0.8),
                new AgentOrchestrator.AgentOpinion("b", "r", "buy", 70, 0.7)
        );
        var output = new AgentOrchestrator.AnalysisOutput("balanced", opinions);
        String summary = output.getSummary();
        assertTrue(summary.contains("2个Agent"));
        assertTrue(summary.contains("buy"));
    }

    @Test
    @DisplayName("AnalysisOutput: 置信度分级")
    void testConfidenceLevels() {
        // 高置信
        var highConf = new AgentOrchestrator.AnalysisOutput("s", List.of(
                new AgentOrchestrator.AgentOpinion("a", "r", "buy", 80, 0.9)));
        assertEquals("高", highConf.getConfidence());

        // 中等置信
        var medConf = new AgentOrchestrator.AnalysisOutput("s", List.of(
                new AgentOrchestrator.AgentOpinion("a", "r", "buy", 60, 0.5)));
        assertEquals("中等", medConf.getConfidence());

        // 低置信
        var lowConf = new AgentOrchestrator.AnalysisOutput("s", List.of(
                new AgentOrchestrator.AgentOpinion("a", "r", "neutral", 50, 0.2)));
        assertEquals("低", lowConf.getConfidence());
    }
}
