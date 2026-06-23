package io.leavesfly.stock.agent;

import io.leavesfly.stock.llm.LlmService;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 风险评估Agent - 评估交易风险
 */
@Component
public class RiskAgent implements BaseAgent {

    private final LlmService llmService;

    public RiskAgent(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String getName() { return "risk"; }

    @Override
    public String getDescription() { return "风险评估Agent - 评估交易风险和回撤预期"; }

    @Override
    public AgentOrchestrator.AgentOpinion analyze(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("股票: ").append(context.get("stock_code")).append("\n");
        Object tech = context.get("technical_analysis");
        if (tech instanceof Map) {
            prompt.append("技术分析: ").append(tech).append("\n");
        }
        prompt.append("请评估当前持仓或建仓的风险等级，并给出止损建议。");

        String response = llmService.chat(
                "你是一位风险控制专家。请评估投资风险并给出风险等级(low/medium/high/extreme)和止损建议。",
                prompt.toString());

        return new AgentOrchestrator.AgentOpinion("risk", response, "neutral", 50, 0.6);
    }
}
