package io.leavesfly.alphaforge.application.agent;

import java.util.List;
import java.util.Map;

/**
 * 专业化子 Agent 接口
 *
 * 每个 SubAgent 专注一个分析维度（技术面/基本面/风控/舆情），
 * 由 MultiAgentOrchestrator 编排调度，最终综合各 Agent 结论。
 */
public interface SubAgent {

    /** Agent 名称（如 "technical_agent"） */
    String getName();

    /** Agent 角色（technical / fundamental / risk / sentiment） */
    String getRole();

    /** 专业化 System Prompt */
    String getSystemPrompt();

    /**
     * 执行专业化分析
     *
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @param context   分析上下文（含技术指标、行情、新闻等）
     * @return 分析结果
     */
    AgentResult analyze(String stockCode, String stockName, Map<String, Object> context);

    // ===== 数据类 =====

    /**
     * 子 Agent 分析结果
     */
    class AgentResult {
        public final String agentName;
        public final String role;
        public final String analysis;       // 分析文本
        public final String signal;         // 信号建议（可为 null，表示该维度不直接给信号）
        public final Integer score;          // 评分建议（0-100，可为 null）
        public final String confidence;     // 置信度（高/中等/低）
        public final List<String> keyFindings; // 关键发现
        public final long durationMs;

        public AgentResult(String agentName, String role, String analysis,
                           String signal, Integer score, String confidence,
                           List<String> keyFindings, long durationMs) {
            this.agentName = agentName;
            this.role = role;
            this.analysis = analysis;
            this.signal = signal;
            this.score = score;
            this.confidence = confidence;
            this.keyFindings = keyFindings != null ? keyFindings : List.of();
            this.durationMs = durationMs;
        }

        /** 空结果（Agent 执行失败时） */
        public static AgentResult empty(String agentName, String role) {
            return new AgentResult(agentName, role, "", null, null, "低", List.of(), 0);
        }
    }
}
