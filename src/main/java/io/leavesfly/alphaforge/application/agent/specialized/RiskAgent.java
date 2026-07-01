package io.leavesfly.alphaforge.application.agent.specialized;

import io.leavesfly.alphaforge.application.agent.AbstractSpecializedAgent;
import io.leavesfly.alphaforge.application.agent.LlmToolAdapter;
import io.leavesfly.alphaforge.application.agent.tools.ToolRegistry;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 风险评估 Agent
 *
 * 独立于信号生成方，专注风险识别与量化。
 * 评估下行风险、波动率、流动性、系统性风险等。
 */
@Component
public class RiskAgent extends AbstractSpecializedAgent {

    private static final String SYSTEM_PROMPT = """
            你是一位独立的量化风控专家，不参与信号生成，专注风险识别。

            你的职责：
            • 评估最大潜在回撤风险
            • 分析波动率和 Beta 系数
            • 识别流动性风险（成交量、换手率）
    • 评估系统性风险（大盘环境、行业风险）
            • 识别黑天鹅风险信号（异常事件、监管变化等）
            • 建议止损位和仓位控制

            分析要求：
            • 风险评分越低表示风险越高（0-100，50为中性风险）
            • 明确指出主要风险因素
            • 给出止损价位建议
            • 评估当前市场环境下的风险收益比

            你独立于其他 Agent，不受其信号影响。请使用 Function Calling 调用工具获取数据，
            最终以 JSON 格式返回风险评估结论。
            """;

    public RiskAgent(LlmPort llmService, LlmToolAdapter toolAdapter,
                     ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        super(llmService, toolAdapter, toolRegistry, objectMapper);
    }

    @Override
    public String getName() { return "risk_agent"; }

    @Override
    public String getRole() { return "risk"; }

    @Override
    public String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String getRoleDescription() { return "风控"; }

    @Override
    protected int getMaxToolCalls() { return 2; }
}
