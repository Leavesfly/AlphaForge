package io.leavesfly.alphaforge.application.agent;

import io.leavesfly.alphaforge.application.prompt.PromptManager;
import io.leavesfly.alphaforge.application.agent.reasoning.StructuredReasoningPromptBuilder;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.util.*;
import java.util.concurrent.*;

/**
 * 多 Agent 编排器 — 并行调度专业化子 Agent 并综合结论
 *
 * 核心流程：
 * 1. 并行启动各子 Agent（技术面/基本面/风控）
 * 2. 收集各 Agent 的分析结果
 * 3. 调用 LLM 综合各维度结论，生成最终分析报告
 *
 * 与 ReActAgent 的关系：
 * - ReActAgent：单 Agent 自主工具调用（适合对话式场景）
 * - MultiAgentOrchestrator：多 Agent 并行分析（适合深度分析场景）
 * - StockAnalysisPipeline 可根据 agentMode 配置选择使用哪种模式
 */
@Component
public class MultiAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentOrchestrator.class);

    private final LlmPort llmService;
    private final List<SubAgent> subAgents;
    private final ExecutorService executor;

    /** 可选依赖：Prompt 管理器（字段注入，避免构造函数参数限制） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PromptManager promptManager;

    /** 可选依赖：结构化推理链 Prompt 构建器 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private StructuredReasoningPromptBuilder reasoningPromptBuilder;

    public MultiAgentOrchestrator(LlmPort llmService, List<SubAgent> subAgents) {
        this.llmService = llmService;
        this.subAgents = subAgents != null ? subAgents : List.of();
        this.executor = Executors.newFixedThreadPool(
                Math.min(this.subAgents.size(), 3));
        log.info("MultiAgentOrchestrator 初始化完成，已注册 {} 个子 Agent: {}",
                this.subAgents.size(),
                this.subAgents.stream().map(SubAgent::getName).toList());
    }

    /** 优雅关闭线程池，避免应用停止时线程泄漏 */
    @PreDestroy
    public void shutdown() {
        log.info("MultiAgentOrchestrator 正在关闭线程池...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("MultiAgentOrchestrator 线程池强制关闭（仍有任务未完成）");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 执行多 Agent 并行分析
     *
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @param context   共享上下文（技术指标、行情等）
     * @param maxTimeoutSeconds 最大超时
     * @return 综合分析结果
     */
    public OrchestrationResult orchestrate(String stockCode, String stockName,
                                            Map<String, Object> context, int maxTimeoutSeconds) {
        long startTime = System.currentTimeMillis();
        log.info("[{}] 多 Agent 分析开始，共 {} 个 Agent", stockCode, subAgents.size());

        // 并行调度各子 Agent
        List<Future<SubAgent.AgentResult>> futures = new ArrayList<>();
        for (SubAgent agent : subAgents) {
            futures.add(executor.submit(() ->
                    agent.analyze(stockCode, stockName, context)));
        }

        // 收集结果
        List<SubAgent.AgentResult> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                SubAgent.AgentResult result = futures.get(i).get(maxTimeoutSeconds, TimeUnit.SECONDS);
                results.add(result);
                log.info("[{}] {} 完成 | 评分:{} 置信:{} 耗时:{}ms",
                        stockCode, result.agentName, result.score, result.confidence, result.durationMs);
            } catch (TimeoutException e) {
                log.warn("[{}] {} 分析超时", stockCode, subAgents.get(i).getName());
                results.add(SubAgent.AgentResult.empty(subAgents.get(i).getName(), subAgents.get(i).getRole()));
            } catch (Exception e) {
                log.error("[{}] {} 分析失败: {}", stockCode, subAgents.get(i).getName(), e.getMessage());
                results.add(SubAgent.AgentResult.empty(subAgents.get(i).getName(), subAgents.get(i).getRole()));
            }
        }

        // 综合各 Agent 结论
        String synthesis = synthesizeResults(stockCode, stockName, results);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[{}] 多 Agent 分析完成 | 耗时:{}ms", stockCode, duration);

        return new OrchestrationResult(results, synthesis, duration);
    }

    /** 综合各子 Agent 的分析结论 */
    private String synthesizeResults(String stockCode, String stockName,
                                      List<SubAgent.AgentResult> results) {
        // 构建 Agent 分析结果摘要
        String agentResults = buildAgentResultsText(results);

        // 优先使用外部化 Prompt 模板
        String systemPrompt = null;
        String userPrompt = null;

        if (promptManager != null) {
            systemPrompt = promptManager.getTemplate("synthesis_system");
            userPrompt = promptManager.render("synthesis_user", Map.of(
                    "stock_name", stockName,
                    "stock_code", stockCode,
                    "agent_results", agentResults
            ));
        }

        // Fallback: 使用内嵌模板
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = """
                    你是一位投资决策综合分析师，负责综合各专业 Agent 的分析结论。
                    你需要权衡各维度的分析结果，给出客观、平衡的最终结论。
                    注意：风控 Agent 的意见应适当加权，避免过度乐观。
                    """;
        }
        if (userPrompt == null || userPrompt.isEmpty()) {
            // 优先使用结构化推理链 prompt
            if (reasoningPromptBuilder != null) {
                userPrompt = reasoningPromptBuilder.buildMultiDimensionalUserPrompt(
                        stockCode, stockName, agentResults, "未知");
            } else {
                userPrompt = String.format("""
                        请综合以下各专业 Agent 的分析结论，给出 %s(%s) 的最终分析报告。

                        %s

                        请综合以上各维度分析，给出：
                        1. 综合评分（0-100）
                        2. 最终交易信号（strong_buy/buy/neutral/sell/strong_sell）
                        3. 综合置信度（高/中等/低）
                        4. 操作建议
                        5. 风险提示
                        6. 关键结论（1-3句话）

                        最终结论以 JSON 格式返回：
                        {"signal":"...","score":0-100,"confidence":"...","summary":"...","operation_advice":"...","risk_note":"..."}
                        """, stockName, stockCode, agentResults);
            }
        }

        return llmService.chat(systemPrompt, userPrompt);
    }

    /** 构建各 Agent 分析结果的文本摘要 */
    private String buildAgentResultsText(List<SubAgent.AgentResult> results) {
        StringBuilder sb = new StringBuilder();
        for (SubAgent.AgentResult r : results) {
            sb.append(String.format("## %s 分析（置信度：%s）\n", r.role, r.confidence));
            sb.append(r.analysis).append("\n");
            if (r.signal != null) sb.append("信号建议：").append(r.signal).append("\n");
            if (r.score != null) sb.append("评分建议：").append(r.score).append("\n");
            if (!r.keyFindings.isEmpty()) {
                sb.append("关键发现：\n");
                for (String f : r.keyFindings) sb.append("- ").append(f).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ===== 数据类 =====

    /**
     * 编排结果
     */
    public record OrchestrationResult(
            List<SubAgent.AgentResult> agentResults,
            String synthesis,
            long durationMs
    ) {
        /** 获取指定角色的 Agent 结果 */
        public Optional<SubAgent.AgentResult> getByRole(String role) {
            return agentResults.stream().filter(r -> r.role.equals(role)).findFirst();
        }

        /** 是否所有 Agent 都成功 */
        public boolean allSucceeded() {
            return agentResults.stream().allMatch(r -> r.analysis != null && !r.analysis.isEmpty());
        }
    }
}
