package io.leavesfly.alphaforge.application.agent;

import io.leavesfly.alphaforge.application.agent.skills.SkillsLoader;
import io.leavesfly.alphaforge.application.agent.tools.ToolRegistry;
import io.leavesfly.alphaforge.application.service.feedback.SignalLearningService;
import io.leavesfly.alphaforge.application.prompt.PromptManager;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ReAct Agent - 统一的推理-行动循环引擎
 *
 * 将 LLM 自主调用工具的能力封装为统一的 Agent 接口，
 * 供 ChatController（AI对话）和 StockAnalysisPipeline（定时分析）共用。
 *
 * 核心能力：
 * - buildSystemPrompt(): 动态构建包含工具列表+技能摘要的system prompt
 * - execute(): 执行工具调用循环（委托给LlmToolAdapter），供流式对话使用
 * - analyze(): 执行完整分析（构建prompt→工具调用循环→返回结果），供Pipeline使用
 *
 * 设计理念：
 * - LLM自主调用工具获取数据，而非外部预获取后注入
 * - 统一的工具+技能系统，消除AgentOrchestrator+专业Agent的割裂
 * - Pipeline和ChatController共享同一套Agent流程
 */
@Component
public class ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);

    private final LlmPort llmService;
    private final LlmToolAdapter toolAdapter;
    private final ToolRegistry toolRegistry;
    private final SkillsLoader skillsLoader;
    private final SignalLearningService signalLearningService;
    private final PromptManager promptManager;

    public ReActAgent(LlmPort llmService, LlmToolAdapter toolAdapter,
                      ToolRegistry toolRegistry, SkillsLoader skillsLoader,
                      SignalLearningService signalLearningService,
                      PromptManager promptManager) {
        this.llmService = llmService;
        this.toolAdapter = toolAdapter;
        this.toolRegistry = toolRegistry;
        this.skillsLoader = skillsLoader;
        this.signalLearningService = signalLearningService;
        this.promptManager = promptManager;
    }

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一位专业的AI股票分析助手。你可以帮用户：
            • 分析个股（技术面/基本面/舆情）
            • 解读行情与市场动向
            • 运行策略回测
            • 设置价格告警
            • 回答投资相关问题
            请用中文回复，回答要专业、简洁、有数据支撑。

            当需要获取实时数据时，你可以通过 Function Calling 调用工具。
            系统已将工具定义注册为原生 Function Calling，你只需根据需要选择调用即可。

            可用工具：
            %s

            每次只调用必要的工具，收到工具结果后再给出最终分析。

            ## 技能（Skills）
            以下技能扩展了你的分析能力。当用户任务与某个技能描述匹配时，
            先调用 skills(action='invoke', name='技能名') 获取执行指令，再按指令调用其他工具完成分析。

            %s

            ## 技能使用规则
            1. 当用户任务与某个技能的 description 高度匹配时，主动调用 skills(action='invoke', name='技能名')
            2. 加载技能后，严格按照技能指令中的步骤执行
            3. 如果没有匹配的技能，直接使用工具回答用户问题
            4. 遇到无法处理的任务时，可使用 skills(action='install', repo='owner/repo') 从GitHub安装新技能
            5. 可使用 skills(action='remove', name='技能名') 删除不需要的已安装技能
            """;

    /**
     * 构建System Prompt（工具列表+技能摘要动态注入）
     */
    public String buildSystemPrompt() {
        // 优先使用外部 Prompt 模板（PromptManager），无模板时 fallback 到内嵌模板
        if (promptManager != null) {
            String template = promptManager.render("react_agent_system", Map.of(
                    "tools", toolRegistry.getToolSummaryText(),
                    "skills", skillsLoader.buildSkillsSummary()
            ));
            if (template != null && !template.isEmpty()) return template;
        }
        return String.format(SYSTEM_PROMPT_TEMPLATE,
                toolRegistry.getToolSummaryText(),
                skillsLoader.buildSkillsSummary());
    }

    /**
     * 执行工具调用循环（供ChatController流式对话使用）
     *
     * 调用方根据返回的 ToolCallSession.getFinalResponse() 决定：
     * - 非null：有工具调用，分块发送最终回复
     * - null：无工具调用，走真正的流式API
     *
     * @param messages     消息历史（会被修改）
     * @param maxToolCalls 最大工具调用次数
     * @param callback     工具调用回调（可为null）
     * @return 工具调用会话
     */
    public LlmToolAdapter.ToolCallSession execute(
            List<Map<String, String>> messages, int maxToolCalls,
            LlmToolAdapter.ToolCallCallback callback) {
        return toolAdapter.executeToolLoop(messages, maxToolCalls, callback);
    }

    /**
     * 执行完整分析（LLM自主调用工具获取数据并分析）
     *
     * 供 StockAnalysisPipeline 使用。LLM会自主调用 get_stock_history、
     * technical_analysis、search_news 等工具获取数据，然后给出综合分析。
     *
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @param context   预获取的上下文数据（可选，作为辅助信息提供给LLM）
     * @return ReAct执行结果
     */
    public ReactResult analyze(String stockCode, String stockName, Map<String, Object> context) {
        log.info("ReactAgent开始分析: {}({})", stockCode, stockName);

        String systemPrompt = buildSystemPrompt();
        String userMessage = buildAnalysisPrompt(stockCode, stockName, context);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));

        long start = System.currentTimeMillis();
        LlmToolAdapter.ToolCallResult result = toolAdapter.chatWithTools(messages, 5, null);
        long duration = System.currentTimeMillis() - start;

        log.info("ReactAgent分析完成: {} 工具调用:{}次 耗时:{}ms",
                stockCode, result.getTotalToolCalls(), duration);

        return new ReactResult(
                result.getResponse(),
                result.getToolCallLog(),
                result.getTotalToolCalls(),
                duration
        );
    }

    /** 构建分析请求prompt */
    private String buildAnalysisPrompt(String stockCode, String stockName, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("请对股票 %s(%s) 进行全面分析。\n", stockCode, stockName));

        // 附加预获取的上下文数据（作为辅助信息）
        if (context != null && !context.isEmpty()) {
            sb.append("\n以下是系统预获取的数据，你可以参考，也可以调用工具获取更多数据：\n");
            appendContextData(sb, "技术分析", context.get("technical_analysis"));
            appendContextData(sb, "实时行情", context.get("realtime_quote"));
            appendContextData(sb, "历史数据", context.get("history_data"));
            appendContextData(sb, "上次分析结论（供参考，请根据最新数据独立判断）", context.get("last_analysis_summary"));
            Object news = context.get("news");
            if (news instanceof List<?> newsList && !newsList.isEmpty()) {
                sb.append("相关新闻:\n");
                for (Object item : newsList) {
                    if (item instanceof Map<?, ?> newsItem) {
                        sb.append("- ").append(newsItem.get("title")).append("\n");
                    }
                }
            }
        }

        // 注入学习提示（统一入口：合并信号反馈 Few-shot + 经验记忆）
        Object techObj = context != null ? context.get("technical_analysis") : null;
        Map<String, Object> techContext = null;
        if (techObj instanceof Map<?, ?> techMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) techMap;
            techContext = casted;
        }
        String learningPrompt = signalLearningService.buildLearningPrompt(stockCode, techContext);
        if (learningPrompt != null && !learningPrompt.isEmpty()) {
            sb.append(learningPrompt);
        }

        sb.append("""

                请使用可用工具获取所需数据，然后给出综合分析报告。
                最终分析结论必须以 JSON 格式返回，结构如下：

                {"signal":"strong_buy/buy/neutral/sell/strong_sell","score":0-100,"confidence":"高/中等/低","summary":"一句话总结","operation_advice":"操作建议","risk_note":"风险提示","target_price":null,"stop_loss_price":null}

                注意：score 必须是根据分析数据得出的真实评分（0-100），不要固定使用 70 或 30。
                请先调用工具获取数据，再给出分析结论。""");

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendContextData(StringBuilder sb, String label, Object data) {
        if (data == null) return;
        if (data instanceof Map<?, ?> map && map.isEmpty()) return;
        sb.append(label).append(": ").append(data).append("\n");
    }

    /**
     * ReAct执行结果
     *
     * @param response      LLM最终回复
     * @param toolCallLog   工具调用日志
     * @param totalToolCalls 工具调用次数
     * @param durationMs    总耗时（毫秒）
     */
    public record ReactResult(String response, List<String> toolCallLog,
                               int totalToolCalls, long durationMs) {}
}
