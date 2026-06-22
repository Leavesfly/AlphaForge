package com.stock.agent;

import com.stock.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent编排器 - 多智能体协作系统
 * 
 * 对应Python版本的 src/agent/orchestrator.py
 * 支持模式: quick/standard/full/specialist
 * 协调多个专业Agent完成综合分析
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private final LlmService llmService;
    private final List<BaseAgent> agents;

    public AgentOrchestrator(LlmService llmService, List<BaseAgent> agents) {
        this.llmService = llmService;
        this.agents = agents;
        log.info("Agent编排器初始化完成, 注册 {} 个Agent", agents.size());
    }

    /**
     * 执行多Agent分析
     *
     * @param context 分析上下文
     * @param mode    分析模式(quick/standard/full/specialist)
     * @return 综合分析结果
     */
    public Map<String, Object> orchestrate(Map<String, Object> context, String mode) {
        log.info("开始Agent编排, 模式: {}, 股票: {}", mode, context.get("stock_code"));
        
        Map<String, Object> results = new LinkedHashMap<>();
        List<AgentOpinion> opinions = new ArrayList<>();

        switch (mode.toLowerCase()) {
            case "quick":
                // 快速模式: 仅技术分析Agent
                opinions.add(executeAgent("technical", context));
                break;
            case "standard":
                // 标准模式: 技术 + 情报 + 决策
                opinions.add(executeAgent("technical", context));
                opinions.add(executeAgent("intel", context));
                opinions.add(executeAgent("decision", context));
                break;
            case "full":
                // 完整模式: 所有Agent
                for (BaseAgent agent : agents) {
                    opinions.add(executeAgent(agent.getName(), context));
                }
                break;
            case "specialist":
                // 专家模式: 根据上下文自动选择
                opinions.addAll(selectAndExecuteAgents(context));
                break;
            default:
                opinions.add(executeAgent("technical", context));
                opinions.add(executeAgent("decision", context));
                break;
        }

        // 综合各Agent意见
        results.put("opinions", opinions);
        results.put("consensus", buildConsensus(opinions));
        results.put("mode", mode);
        
        return results;
    }

    /**
     * 执行单个Agent
     */
    private AgentOpinion executeAgent(String agentName, Map<String, Object> context) {
        BaseAgent agent = findAgent(agentName);
        if (agent == null) {
            log.warn("Agent未找到: {}", agentName);
            return new AgentOpinion(agentName, "未找到Agent", "neutral", 50, 0.0);
        }

        try {
            long start = System.currentTimeMillis();
            AgentOpinion opinion = agent.analyze(context);
            opinion.setDurationMs(System.currentTimeMillis() - start);
            log.info("Agent {} 分析完成, 信号: {}, 评分: {}, 耗时: {}ms", 
                    agentName, opinion.getSignal(), opinion.getScore(), opinion.getDurationMs());
            return opinion;
        } catch (Exception e) {
            log.error("Agent {} 执行失败: {}", agentName, e.getMessage());
            return new AgentOpinion(agentName, "执行失败: " + e.getMessage(), "neutral", 50, 0.0);
        }
    }

    /**
     * 根据上下文智能选择Agent
     */
    private List<AgentOpinion> selectAndExecuteAgents(Map<String, Object> context) {
        List<AgentOpinion> opinions = new ArrayList<>();
        // 始终执行技术分析
        opinions.add(executeAgent("technical", context));
        // 始终执行风险评估
        opinions.add(executeAgent("risk", context));
        // 根据是否有新闻决定是否执行情报分析
        Object news = context.get("news");
        if (news instanceof List && !((List<?>) news).isEmpty()) {
            opinions.add(executeAgent("intel", context));
        }
        // 最终决策
        opinions.add(executeAgent("decision", context));
        return opinions;
    }

    /**
     * 构建共识结论
     */
    private Map<String, Object> buildConsensus(List<AgentOpinion> opinions) {
        Map<String, Object> consensus = new LinkedHashMap<>();
        
        if (opinions.isEmpty()) {
            consensus.put("signal", "neutral");
            consensus.put("score", 50);
            consensus.put("confidence", 0.0);
            return consensus;
        }

        // 加权平均评分
        double totalScore = 0;
        double totalConfidence = 0;
        for (AgentOpinion op : opinions) {
            totalScore += op.getScore();
            totalConfidence += op.getConfidence();
        }
        int avgScore = (int) (totalScore / opinions.size());
        double avgConfidence = totalConfidence / opinions.size();

        // 信号投票
        Map<String, Integer> signalVotes = new HashMap<>();
        for (AgentOpinion op : opinions) {
            signalVotes.merge(op.getSignal(), 1, Integer::sum);
        }
        String majoritySignal = signalVotes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("neutral");

        consensus.put("signal", majoritySignal);
        consensus.put("score", avgScore);
        consensus.put("confidence", avgConfidence);
        consensus.put("agent_count", opinions.size());
        
        return consensus;
    }

    private BaseAgent findAgent(String name) {
        return agents.stream()
                .filter(a -> a.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Agent意见数据类
     */
    public static class AgentOpinion {
        private String agentName;
        private String reasoning;
        private String signal;
        private int score;
        private double confidence;
        private long durationMs;

        public AgentOpinion() {}
        public AgentOpinion(String agentName, String reasoning, String signal, int score, double confidence) {
            this.agentName = agentName;
            this.reasoning = reasoning;
            this.signal = signal;
            this.score = score;
            this.confidence = confidence;
        }

        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }
        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
        public String getSignal() { return signal; }
        public void setSignal(String signal) { this.signal = signal; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    /**
     * 完整分析入口(供Pipeline调用)
     */
    @SuppressWarnings("unchecked")
    public AnalysisOutput runAnalysis(Map<String, Object> context) {
        String mode = "standard";
        Map<String, Object> result = orchestrate(context, mode);
        List<AgentOpinion> opinions = (List<AgentOpinion>) result.getOrDefault("opinions", List.of());
        return new AnalysisOutput(mode, opinions);
    }

    /**
     * 分析输出(编排结果)
     */
    public static class AnalysisOutput {
        private final String mode;
        private final List<AgentOpinion> opinions;

        public AnalysisOutput(String mode, List<AgentOpinion> opinions) {
            this.mode = mode;
            this.opinions = opinions != null ? opinions : List.of();
        }

        public String getMode() { return mode; }
        public int getOpinionCount() { return opinions.size(); }

        public String getConsensusSignal() {
            if (opinions.isEmpty()) return "neutral";
            Map<String, Integer> votes = new java.util.LinkedHashMap<>();
            for (AgentOpinion op : opinions) {
                votes.merge(op.getSignal(), 1, Integer::sum);
            }
            return votes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("neutral");
        }

        public int getConsensusScore() {
            if (opinions.isEmpty()) return 50;
            return (int) opinions.stream().mapToInt(AgentOpinion::getScore).average().orElse(50);
        }

        public String getFullReport() {
            StringBuilder sb = new StringBuilder();
            for (AgentOpinion op : opinions) {
                sb.append("### ").append(op.getAgentName()).append("\n");
                sb.append(op.getReasoning()).append("\n\n");
            }
            return sb.toString();
        }

        public String getSummary() {
            return String.format("综合%d个Agent意见，共识信号: %s，评分: %d/100",
                    opinions.size(), getConsensusSignal(), getConsensusScore());
        }

        public String getAdvice() {
            return opinions.isEmpty() ? "暂无建议" : opinions.get(0).getReasoning();
        }

        public String getConfidence() {
            if (opinions.isEmpty()) return "低";
            double avgConf = opinions.stream().mapToDouble(AgentOpinion::getConfidence).average().orElse(0.5);
            return avgConf >= 0.7 ? "高" : avgConf >= 0.4 ? "中等" : "低";
        }

        public String getRiskAssessment() {
            return opinions.stream()
                    .filter(op -> "risk".equals(op.getAgentName()))
                    .findFirst()
                    .map(AgentOpinion::getReasoning)
                    .orElse("未评估");
        }
    }
}
