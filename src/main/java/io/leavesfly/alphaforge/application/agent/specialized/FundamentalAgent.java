package io.leavesfly.alphaforge.application.agent.specialized;

import io.leavesfly.alphaforge.application.agent.AbstractSpecializedAgent;
import io.leavesfly.alphaforge.application.agent.LlmToolAdapter;
import io.leavesfly.alphaforge.application.agent.tools.ToolRegistry;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import org.springframework.stereotype.Component;

/**
 * 基本面分析 Agent
 *
 * 专注财务数据分析、估值评估、行业地位判断。
 * 使用 get_financials、search_news、get_research 等工具获取数据。
 */
@Component
public class FundamentalAgent extends AbstractSpecializedAgent {

    private static final String SYSTEM_PROMPT = """
            你是一位资深的基本面分析师，精通财务报表分析和估值模型。

            你的职责：
            • 分析营收/利润/ROE/负债率等核心财务指标
            • 评估 PE/PB/PS 等估值水平的合理性
            • 判断行业地位和竞争格局
            • 识别财务风险信号（如应收账款激增、商誉减值等）
            • 评估成长性与盈利质量

            分析要求：
            • 结合行业均值和历史数据评估
            • 评分反映基本面优劣（0-100，50为中性）
            • 关注财务数据的趋势变化
            • 可不直接给出交易信号，而是评估基本面强弱

            请使用 Function Calling 调用工具获取数据，最终以 JSON 格式返回分析结论。
            """;

    public FundamentalAgent(LlmPort llmService, LlmToolAdapter toolAdapter,
                            ToolRegistry toolRegistry) {
        super(llmService, toolAdapter, toolRegistry);
    }

    @Override
    public String getName() { return "fundamental_agent"; }

    @Override
    public String getRole() { return "fundamental"; }

    @Override
    public String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String getRoleDescription() { return "基本面"; }

    @Override
    protected int getMaxToolCalls() { return 3; }
}
