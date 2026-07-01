package io.leavesfly.alphaforge.application.agent.specialized;

import io.leavesfly.alphaforge.application.agent.AbstractSpecializedAgent;
import io.leavesfly.alphaforge.application.agent.LlmToolAdapter;
import io.leavesfly.alphaforge.application.agent.tools.ToolRegistry;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 技术面分析 Agent
 *
 * 专注技术指标解读、趋势判断、形态识别、量价关系分析。
 * 使用 technical_analysis、get_stock_history 等工具获取数据。
 */
@Component
public class TechnicalAgent extends AbstractSpecializedAgent {

    private static final String SYSTEM_PROMPT = """
            你是一位资深的技术分析师，精通各类技术指标和 K 线形态。

            你的职责：
            • 解读 MA/MACD/KDJ/RSI/BOLL 等技术指标
            • 判断当前趋势方向和强度
            • 识别关键形态（突破、回调、金叉死叉等）
            • 分析量价配合关系
            • 识别关键支撑位和阻力位

            分析要求：
            • 基于数据说话，避免主观臆断
            • 给出明确的技术信号（strong_buy/buy/neutral/sell/strong_sell）
            • 评分反映技术面强弱（0-100，50为中性）
            • 指出关键技术位和形态变化

            请使用 Function Calling 调用工具获取数据，最终以 JSON 格式返回分析结论。
            """;

    public TechnicalAgent(LlmPort llmService, LlmToolAdapter toolAdapter,
                         ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        super(llmService, toolAdapter, toolRegistry, objectMapper);
    }

    @Override
    public String getName() { return "technical_agent"; }

    @Override
    public String getRole() { return "technical"; }

    @Override
    public String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String getRoleDescription() { return "技术面"; }

    @Override
    protected int getMaxToolCalls() { return 3; }
}
