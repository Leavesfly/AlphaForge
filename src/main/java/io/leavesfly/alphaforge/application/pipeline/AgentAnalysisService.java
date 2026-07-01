package io.leavesfly.alphaforge.application.pipeline;

import io.leavesfly.alphaforge.application.agent.ReActAgent;
import io.leavesfly.alphaforge.application.agent.MultiAgentOrchestrator;
import io.leavesfly.alphaforge.application.agent.debate.AgentDebateOrchestrator;
import io.leavesfly.alphaforge.application.agent.debate.DebateResult;
import io.leavesfly.alphaforge.application.evaluation.LlmAnalysisQuality;
import io.leavesfly.alphaforge.application.evaluation.LlmAnalysisQualityAssessor;
import io.leavesfly.alphaforge.application.prompt.PromptManager;
import io.leavesfly.alphaforge.application.service.signal.SignalExtractionService;
import io.leavesfly.alphaforge.config.SchedulerAuthConfig;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 分析服务 — 从 StockAnalysisPipeline 提取的 Agent/LLM 分析逻辑
 *
 * 职责：
 * - Agent 模式分析（debate/multi/react 三选一，含降级策略）
 * - 传统 LLM 直接分析（作为 Agent 模式的降级方案）
 * - LLM 分析质量自动评估
 *
 * 设计原则：
 * - Pipeline 只负责编排，不关心具体分析实现
 * - 降级链 debate → multi → react → llm 全部封装在此类中
 */
@Service
public class AgentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AgentAnalysisService.class);

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一位专业的股票分析师，拥有丰富的技术分析和基本面分析经验。
            请根据提供的股票数据进行全面分析，包括：
            1. 技术面分析：趋势判断、关键技术指标解读、形态识别
            2. 基本面评估：估值水平、行业地位
            3. 消息面影响：重要新闻对股价的潜在影响
            4. 综合评分和交易信号
            5. 风险评估和操作建议

            请给出明确的交易信号和评分。
            分析要客观、专业，注重数据支撑。
            """;

    private final SchedulerAuthConfig schedulerAuthConfig;
    private final LlmPort llmService;
    private final ReActAgent reactAgent;
    private final SignalExtractionService signalExtractionService;
    private final PromptManager promptManager;

    /** 可选依赖：多 Agent 编排器 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MultiAgentOrchestrator multiAgentOrchestrator;

    /** 可选依赖：Agent 辩论编排器 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentDebateOrchestrator debateOrchestrator;

    /** 可选依赖：LLM 分析质量评估器 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LlmAnalysisQualityAssessor analysisQualityAssessor;

    public AgentAnalysisService(SchedulerAuthConfig schedulerAuthConfig, LlmPort llmService,
                                  ReActAgent reactAgent,
                                  SignalExtractionService signalExtractionService,
                                  PromptManager promptManager) {
        this.schedulerAuthConfig = schedulerAuthConfig;
        this.llmService = llmService;
        this.reactAgent = reactAgent;
        this.signalExtractionService = signalExtractionService;
        this.promptManager = promptManager;
    }

    /**
     * 使用 Agent 模式进行分析（含降级策略）
     *
     * 降级链：debate → multi → react → llm
     *
     * @param stockCode  股票代码
     * @param stockName  股票名称
     * @param context    分析上下文
     * @param diag       诊断上下文（可为 null）
     * @return 分析结果
     */
    public AnalysisResult analyze(
            String stockCode, String stockName,
            Map<String, Object> context,
            DiagnosticContext diag) {
        String agentMode = schedulerAuthConfig.getAgentMode();

        // 1. 辩论模式：多 Agent 独立分析 → 交叉质询 → 裁判裁决
        if (debateOrchestrator != null && "debate".equals(agentMode)) {
            try {
                AnalysisResult result = analyzeWithDebate(stockCode, stockName, context, diag);
                if (result != null) return result;
            } catch (Exception e) {
                log.warn("[{}] 辩论模式分析失败，降级到多Agent: {}", stockCode, e.getMessage());
            }
        }

        // 2. 多 Agent 模式
        if (multiAgentOrchestrator != null && ("multi".equals(agentMode) || "full".equals(agentMode) || "debate".equals(agentMode))) {
            try {
                AnalysisResult result = analyzeWithMultiAgent(stockCode, stockName, context, diag);
                if (result != null) return result;
            } catch (Exception e) {
                log.warn("[{}] 多Agent分析失败，降级到ReactAgent: {}", stockCode, e.getMessage());
            }
        }

        // 3. ReactAgent 模式
        try {
            ReActAgent.ReactResult reactResult = reactAgent.analyze(stockCode, stockName, context);
            if (diag != null) {
                diag.record("agent_mode", "react");
                diag.record("agent_tool_calls", reactResult.totalToolCalls());
                diag.record("agent_duration_ms", reactResult.durationMs());
            }
            return reactResultToAnalysisResult(reactResult, stockCode, stockName);
        } catch (Exception e) {
            log.warn("[{}] ReactAgent分析失败，降级到传统LLM: {}", stockCode, e.getMessage());
        }

        // 4. 传统 LLM 直接分析（最终降级）
        return analyzeWithLlm(stockCode, stockName, context);
    }

    /**
     * 辩论模式分析
     */
    private AnalysisResult analyzeWithDebate(
            String stockCode, String stockName,
            Map<String, Object> context,
            DiagnosticContext diag) {
        DebateResult debateResult = debateOrchestrator.orchestrateWithDebate(
                stockCode, stockName, context, 120);
        if (diag != null) {
            diag.record("agent_mode", "debate");
            diag.record("agent_count", debateResult.getInitialResults().size());
            diag.record("debate_enabled", debateResult.isDebateEnabled());
            diag.record("debate_duration_ms", debateResult.getTotalDurationMs());
            if (debateResult.getVerdict() != null) {
                diag.record("debate_consensus", debateResult.getVerdict().getConsensusLevel());
                diag.record("debate_signal_adjusted", debateResult.getVerdict().isSignalDowngraded());
            }
        }
        AnalysisResult result = new AnalysisResult();
        result.stockCode = stockCode;
        result.stockName = stockName;
        result.fullReport = debateResult.getFinalAnalysis();
        result.source = "debate_agent";
        signalExtractionService.extractFromLlmResponse(result, debateResult.getFinalAnalysis());
        // 辩论裁决覆盖信号和评分
        if (debateResult.getVerdict() != null) {
            result.signal = debateResult.getVerdict().getFinalSignal();
            result.score = debateResult.getVerdict().getFinalScore();
            result.confidence = debateResult.getVerdict().getConfidence();
        }
        return result;
    }

    /**
     * 多 Agent 模式分析
     */
    private AnalysisResult analyzeWithMultiAgent(
            String stockCode, String stockName,
            Map<String, Object> context,
            DiagnosticContext diag) {
        MultiAgentOrchestrator.OrchestrationResult orcResult =
                multiAgentOrchestrator.orchestrate(stockCode, stockName, context, 120);
        if (diag != null) {
            diag.record("agent_mode", "multi_agent");
            diag.record("agent_count", orcResult.agentResults().size());
            diag.record("agent_duration_ms", orcResult.durationMs());
        }
        AnalysisResult result = new AnalysisResult();
        result.stockCode = stockCode;
        result.stockName = stockName;
        result.fullReport = orcResult.synthesis();
        result.source = "multi_agent";
        signalExtractionService.extractFromLlmResponse(result, orcResult.synthesis());
        return result;
    }

    /**
     * ReactAgent 结果转换为标准 AnalysisResult
     */
    private AnalysisResult reactResultToAnalysisResult(
            ReActAgent.ReactResult reactResult, String stockCode, String stockName) {
        AnalysisResult result = new AnalysisResult();
        result.stockCode = stockCode;
        result.stockName = stockName;
        result.fullReport = reactResult.response();
        result.source = "react_agent";
        signalExtractionService.extractFromLlmResponse(result, reactResult.response());
        return result;
    }

    /**
     * 传统 LLM 直接分析（最终降级方案）
     */
    private AnalysisResult analyzeWithLlm(
            String stockCode, String stockName, Map<String, Object> context) {
        // 优先使用结构化输出（JSON Mode），fallback 到传统 analyzeStock
        String response;
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", (Object) "你是一位专业的股票分析师。请以JSON格式返回分析结论。"));
            messages.add(Map.of("role", "user", "content", (Object) buildLlmUserPrompt(stockCode, stockName, context)));
            response = llmService.chatForStructuredOutput(messages, null);
            if (response == null || response.isEmpty() || "{}".equals(response)) {
                response = llmService.chat(buildSystemPrompt(), buildAnalysisPrompt(context));
            }
        } catch (Exception e) {
            log.debug("[{}] 结构化输出失败，回退到传统模式: {}", stockCode, e.getMessage());
            response = llmService.chat(buildSystemPrompt(), buildAnalysisPrompt(context));
        }

        AnalysisResult result = new AnalysisResult();
        result.stockCode = stockCode;
        result.stockName = stockName;
        result.fullReport = response;
        result.source = "llm_structured";
        signalExtractionService.extractFromLlmResponse(result, response);

        // LLM 分析质量自动评估
        if (analysisQualityAssessor != null) {
            try {
                LlmAnalysisQuality quality = analysisQualityAssessor.assess(response, context);
                if (quality.hasHallucinations()) {
                    log.warn("[{}] LLM 分析存在 {} 处数据幻觉", stockCode, quality.getHallucinations().size());
                }
                if (quality.hasLogicalContradictions()) {
                    log.warn("[{}] LLM 分析存在 {} 处逻辑矛盾", stockCode, quality.getLogicalContradictions().size());
                }
                result.qualityScore = quality.getOverallScore();
            } catch (Exception e) {
                log.debug("[{}] LLM 质量评估失败: {}", stockCode, e.getMessage());
            }
        }
        return result;
    }

    /** 为结构化输出构建用户提示 */
    private String buildLlmUserPrompt(String stockCode, String stockName, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("请分析股票 %s(%s)。\n", stockName, stockCode));
        if (context != null) {
            Object tech = context.get("technical_analysis");
            if (tech != null) sb.append("技术分析:").append(tech).append("\n");
            Object quote = context.get("realtime_quote");
            if (quote != null) sb.append("实时行情:").append(quote).append("\n");
        }
        sb.append("请以JSON返回: {\"signal\":\"...\",\"score\":0-100,\"confidence\":\"...\",\"summary\":\"...\",\"operation_advice\":\"...\",\"risk_note\":\"...\"}");
        return sb.toString();
    }

    /** 构建系统提示词（原 StockAnalysisPromptService.buildSystemPrompt） */
    private String buildSystemPrompt() {
        if (promptManager == null) return DEFAULT_SYSTEM_PROMPT;
        return promptManager.getTemplateOrDefault("stock_analysis_system", DEFAULT_SYSTEM_PROMPT);
    }

    /** 构建分析用户提示词（原 StockAnalysisPromptService.buildAnalysisPrompt） */
    private String buildAnalysisPrompt(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下股票:\n\n");
        prompt.append("## 基本信息\n");
        prompt.append("- 股票代码: ").append(context.get("stock_code")).append("\n");
        prompt.append("- 股票名称: ").append(context.get("stock_name")).append("\n");
        prompt.append("- 市场: ").append(context.get("market")).append("\n");
        prompt.append("- 分析日期: ").append(context.get("analysis_date")).append("\n\n");

        prompt.append("## 历史行情数据\n");
        prompt.append(context.get("history_data")).append("\n");

        Object quote = context.get("realtime_quote");
        if (quote instanceof Map && !((Map<?, ?>) quote).isEmpty()) {
            prompt.append("## 实时行情\n");
            ((Map<?, ?>) quote).forEach((k, v) ->
                    prompt.append("- ").append(k).append(": ").append(v).append("\n"));
            prompt.append("\n");
        }

        Object tech = context.get("technical_analysis");
        if (tech instanceof Map && !((Map<?, ?>) tech).isEmpty()) {
            prompt.append("## 技术指标\n");
            ((Map<?, ?>) tech).forEach((k, v) ->
                    prompt.append("- ").append(k).append(": ").append(v).append("\n"));
            prompt.append("\n");
        }

        Object news = context.get("news");
        if (news instanceof List && !((List<?>) news).isEmpty()) {
            prompt.append("## 相关新闻\n");
            for (Object item : (List<?>) news) {
                if (item instanceof Map) {
                    Map<?, ?> newsItem = (Map<?, ?>) item;
                    prompt.append("- ").append(newsItem.get("title")).append("\n");
                }
            }
            prompt.append("\n");
        }

        prompt.append("请给出完整的分析报告，包括综合评分、交易信号和操作建议。");
        return prompt.toString();
    }
}
