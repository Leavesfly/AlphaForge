package io.leavesfly.alphaforge.application.agent.kernel;

import io.leavesfly.alphaforge.application.agent.LlmToolAdapter;
import io.leavesfly.alphaforge.application.agent.ReActAgent;
import io.leavesfly.alphaforge.application.pipeline.DiagnosticContext;
import io.leavesfly.alphaforge.application.service.AgentAnalysisService;
import io.leavesfly.alphaforge.application.strategy.generator.StrategyRefineLoop;
import io.leavesfly.alphaforge.application.autonomy.SignalToPortfolioExecutor;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisResult;
import io.leavesfly.alphaforge.domain.model.entity.signal.DecisionSignal;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 内核 — 认知轨的唯一大脑。
 *
 * <p>统一入口 {@link #run(AgentTask)}：规划(Planner) → 治理预检(Guardrail) →
 * 分派执行(Executor/委托) → 评审(Critic)。
 *
 * <p>阶段 1：打通 {@code STOCK_ANALYSIS}（委托既有 AgentAnalysisService，验证链路）
 * 与 {@code CHAT}（复用 ReActAgent 工具循环）。其余认知型任务在后续阶段接入，
 * 未接入前返回明确的未实现结果，不影响功能轨。
 */
@Component
public class AgentKernel {

    private static final Logger log = LoggerFactory.getLogger(AgentKernel.class);

    private final Planner planner;
    private final AgentGuardrail guardrail;
    private final Critic critic;
    private final AgentAnalysisService agentAnalysisService;
    private final ReActAgent reactAgent;
    private final LlmPort llmPort;

    /** 可选依赖：策略生成/优化闭环（未启用时为 null） */
    private final StrategyRefineLoop strategyRefineLoop;

    /** 可选依赖：信号纸面执行器（未启用时为 null） */
    private final SignalToPortfolioExecutor signalToPortfolioExecutor;

    @Autowired
    public AgentKernel(Planner planner,
                       AgentGuardrail guardrail,
                       Critic critic,
                       AgentAnalysisService agentAnalysisService,
                       ReActAgent reactAgent,
                       LlmPort llmPort,
                       ObjectProvider<StrategyRefineLoop> strategyRefineLoop,
                       ObjectProvider<SignalToPortfolioExecutor> signalToPortfolioExecutor) {
        this.planner = planner;
        this.guardrail = guardrail;
        this.critic = critic;
        this.agentAnalysisService = agentAnalysisService;
        this.reactAgent = reactAgent;
        this.llmPort = llmPort;
        this.strategyRefineLoop = strategyRefineLoop.getIfAvailable();
        this.signalToPortfolioExecutor = signalToPortfolioExecutor.getIfAvailable();
    }

    /** 测试用构造器：无策略精修/信号执行等可选协作者 */
    public AgentKernel(Planner planner,
                       AgentGuardrail guardrail,
                       Critic critic,
                       AgentAnalysisService agentAnalysisService,
                       ReActAgent reactAgent,
                       LlmPort llmPort) {
        this.planner = planner;
        this.guardrail = guardrail;
        this.critic = critic;
        this.agentAnalysisService = agentAnalysisService;
        this.reactAgent = reactAgent;
        this.llmPort = llmPort;
        this.strategyRefineLoop = null;
        this.signalToPortfolioExecutor = null;
    }

    /**
     * 运行一个认知型任务。
     */
    public AgentResult run(AgentTask task) {
        if (task == null) {
            throw new IllegalArgumentException("AgentTask 不能为空");
        }
        long start = System.currentTimeMillis();
        AgentContext context = new AgentContext(task);

        AgentPlan plan = planner.plan(task, context);
        context.trace("planned: " + plan.getSteps());
        log.info("[kernel] task={} plan={} steps", task.getType(), plan.getSteps().size());

        AgentResult draft = dispatch(task, context, plan, start);
        return critic.review(context, draft);
    }

    // ==================== 分派 ====================

    private AgentResult dispatch(AgentTask task, AgentContext context, AgentPlan plan, long start) {
        try {
            return switch (task.getType()) {
                case STOCK_ANALYSIS -> runStockAnalysis(task, context, plan, start);
                case CHAT -> runChat(task, context, plan, start);
                case STRATEGY_GENERATE -> runStrategyGenerate(task, context, plan, start);
                case AUTONOMY_DECISION -> runAutonomyDecision(task, context, plan, start);
                default -> AgentResult.fail(task.getType(),
                        "任务类型暂未接入内核（后续阶段实现）: " + task.getType());
            };
        } catch (AgentGuardrailException e) {
            log.warn("[kernel] Guardrail 拦截 task={}: {}", task.getType(), e.getMessage());
            return AgentResult.fail(task.getType(), "治理拦截: " + e.getMessage());
        } catch (Exception e) {
            log.error("[kernel] 任务执行失败 task={}: {}", task.getType(), e.getMessage(), e);
            return AgentResult.fail(task.getType(), "执行失败: " + e.getMessage());
        }
    }

    /** STOCK_ANALYSIS：委托既有 AgentAnalysisService（阶段 1 验证链路）。 */
    @SuppressWarnings("unchecked")
    private AgentResult runStockAnalysis(AgentTask task, AgentContext context,
                                         AgentPlan plan, long start) {
        enforceGuardrail(task, plan);

        String stockCode = task.inputString("stockCode");
        String stockName = task.inputString("stockName");
        if (stockCode == null || stockCode.isBlank()) {
            return AgentResult.fail(task.getType(), "缺少 stockCode");
        }

        Map<String, Object> analysisContext = task.input("context") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) task.input("context"))
                : new LinkedHashMap<>();

        DiagnosticContext diag = task.input("diagnosticContext") instanceof DiagnosticContext d
                ? d : new DiagnosticContext(stockCode);
        AnalysisResult result = agentAnalysisService.analyze(stockCode, stockName, analysisContext, diag);
        context.trace("stock_analysis done: signal=" + (result != null ? result.signal : null));

        long duration = System.currentTimeMillis() - start;
        return AgentResult.ok(task.getType())
                .output(result != null ? result.fullReport : "")
                .data("analysisResult", result)
                .data("diagnostics", diag.getRecords())
                .durationMs(duration)
                .build();
    }

    /** CHAT：复用 ReActAgent 工具循环；无工具调用时回退到普通对话。 */
    private AgentResult runChat(AgentTask task, AgentContext context,
                                AgentPlan plan, long start) {
        enforceGuardrail(task, plan);

        List<Map<String, String>> messages = buildChatMessages(task);
        int maxToolCalls = task.getPolicy().getMaxToolCalls();

        LlmToolAdapter.ToolCallSession session = reactAgent.execute(messages, maxToolCalls, null);
        String output = session.getFinalResponse();
        if (output == null) {
            // 无工具调用：走普通对话获取回复
            output = llmPort.chatWithMessages(messages);
        }
        context.trace("chat done: toolCalls=" + session.getTotalToolCalls());

        long duration = System.currentTimeMillis() - start;
        return AgentResult.ok(task.getType())
                .output(output)
                .toolCallLog(session.getToolCallLog())
                .toolCalls(session.getTotalToolCalls())
                .durationMs(duration)
                .build();
    }

    /**
     * CHAT 流式执行：复用 ReAct 工具循环，将工具事件/文本增量回调给监听器，
     * 返回完整回复文本（供调用方持久化）。表现层通过 {@link StreamListener} 桥接 SSE，
     * 内核不感知具体传输协议。仅支持 CHAT 类任务。
     */
    public String runChatStreaming(AgentTask task, StreamListener listener) {
        if (task == null) {
            throw new IllegalArgumentException("AgentTask 不能为空");
        }
        List<Map<String, String>> messages = buildChatMessages(task);
        int maxToolCalls = task.getPolicy().getMaxToolCalls();
        StringBuilder full = new StringBuilder();

        LlmToolAdapter.ToolCallSession session = reactAgent.execute(messages, maxToolCalls,
                (toolName, args, result, durationMs) -> listener.onToolCall(toolName, args, result, durationMs));

        String response = session.getFinalResponse();
        if (response != null) {
            // 有工具调用：分块回放最终回复，模拟流式
            full.append(response);
            int chunkSize = 8;
            for (int i = 0; i < response.length(); i += chunkSize) {
                int end = Math.min(i + chunkSize, response.length());
                listener.onChunk(response.substring(i, end));
            }
        } else {
            // 无工具调用：走真正的流式 API 逐字输出
            llmPort.streamChatWithMessages(messages, chunk -> {
                full.append(chunk);
                listener.onChunk(chunk);
            });
        }
        return full.toString();
    }

    /** STRATEGY_GENERATE：委托 StrategyRefineLoop 执行生成→回测→改写闭环。 */
    private AgentResult runStrategyGenerate(AgentTask task, AgentContext context,
                                            AgentPlan plan, long start) {
        enforceGuardrail(task, plan);

        if (strategyRefineLoop == null) {
            return AgentResult.fail(task.getType(), "StrategyRefineLoop 未启用");
        }
        String description = task.inputString("description");
        if (description == null || description.isBlank()) {
            return AgentResult.fail(task.getType(), "缺少 description");
        }
        String stockCode = task.inputString("stockCode");
        if (stockCode == null || stockCode.isBlank()) {
            stockCode = "600519";
        }
        int maxRounds = task.input("maxRounds") instanceof Number n ? n.intValue() : 3;
        boolean promoteToTesting = !(task.input("promoteToTesting") instanceof Boolean b) || b;

        Map<String, Object> refineResult = strategyRefineLoop.run(description, stockCode, maxRounds, promoteToTesting);
        context.trace("strategy_generate done");

        long duration = System.currentTimeMillis() - start;
        return AgentResult.ok(task.getType())
                .output("策略 refine 完成")
                .data("refineResult", refineResult)
                .durationMs(duration)
                .build();
    }

    /** AUTONOMY_DECISION：研判后纸面执行信号（execute_signal 为状态变更步骤，必经 Guardrail）。 */
    private AgentResult runAutonomyDecision(AgentTask task, AgentContext context,
                                            AgentPlan plan, long start) {
        // 计划含 mutating 的 execute_signal 步骤；默认策略下将被 Guardrail 拦截
        enforceGuardrail(task, plan);

        if (signalToPortfolioExecutor == null) {
            return AgentResult.fail(task.getType(), "SignalToPortfolioExecutor 未启用");
        }
        if (!(task.input("signal") instanceof DecisionSignal signal)) {
            return AgentResult.fail(task.getType(), "缺少 signal");
        }

        Map<String, Object> execResult = signalToPortfolioExecutor.execute(signal);
        context.trace("autonomy_decision executed");

        long duration = System.currentTimeMillis() - start;
        return AgentResult.ok(task.getType())
                .output("信号执行完成")
                .data("execResult", execResult)
                .durationMs(duration)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> buildChatMessages(AgentTask task) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", reactAgent.buildSystemPrompt()));

        // 可选：注入历史消息（inputs["history"]: List<Map<String,String>>）
        Object history = task.input("history");
        if (history instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m && m.get("role") != null && m.get("content") != null) {
                    messages.add(Map.of("role", String.valueOf(m.get("role")),
                            "content", String.valueOf(m.get("content"))));
                }
            }
        }

        messages.add(Map.of("role", "user", "content", task.getGoal() != null ? task.getGoal() : ""));
        return messages;
    }

    /** 对计划中的每个步骤执行治理预检（只读步骤为 no-op）。 */
    private void enforceGuardrail(AgentTask task, AgentPlan plan) {
        for (PlanStep step : plan.getSteps()) {
            guardrail.assertStepAllowed(task, step);
        }
    }

    /**
     * 流式监听器：由表现层实现以桥接 SSE 等传输协议。
     * 内核仅回调工具事件与文本增量，不感知具体协议。
     */
    public interface StreamListener {
        void onToolCall(String toolName, Map<String, Object> args, String result, long durationMs);
        void onChunk(String chunk);
    }
}
