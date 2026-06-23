package io.leavesfly.stock.agent;

import io.leavesfly.stock.llm.LlmService;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 决策Agent - 综合各方面信息做出最终投资决策
 */
@Component
public class DecisionAgent implements BaseAgent {

    private final LlmService llmService;

    public DecisionAgent(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String getName() { return "decision"; }

    @Override
    public String getDescription() { return "决策Agent - 综合信息做出最终投资决策建议"; }

    @Override
    public AgentOrchestrator.AgentOpinion analyze(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("股票: ").append(context.get("stock_code")).append(" ").append(context.get("stock_name")).append("\n\n");
        prompt.append("请综合以下信息做出最终投资决策:\n");
        
        Object tech = context.get("technical_analysis");
        if (tech != null) prompt.append("技术面: ").append(tech).append("\n");
        Object quote = context.get("realtime_quote");
        if (quote != null) prompt.append("当前行情: ").append(quote).append("\n");
        
        prompt.append("\n请给出明确的买卖建议和仓位管理建议。");

        String response = llmService.chat(
                "你是一位资深投资经理。请综合技术面、消息面和风险因素，给出明确的投资决策。" +
                "包含: 信号(strong_buy/buy/neutral/sell/strong_sell)、评分(0-100)、置信度(0-1)、操作建议。",
                prompt.toString());

        return parseResponse(response);
    }

    private AgentOrchestrator.AgentOpinion parseResponse(String response) {
        String signal = "neutral";
        int score = 50;
        double confidence = 0.6;

        String lower = response.toLowerCase();
        if (lower.contains("strong_buy") || lower.contains("强烈买入")) {
            signal = "strong_buy"; score = 85; confidence = 0.8;
        } else if (lower.contains("buy") || lower.contains("买入") || lower.contains("建仓")) {
            signal = "buy"; score = 70; confidence = 0.7;
        } else if (lower.contains("strong_sell") || lower.contains("强烈卖出")) {
            signal = "strong_sell"; score = 15; confidence = 0.8;
        } else if (lower.contains("sell") || lower.contains("卖出") || lower.contains("清仓")) {
            signal = "sell"; score = 30; confidence = 0.7;
        }
        return new AgentOrchestrator.AgentOpinion("decision", response, signal, score, confidence);
    }
}
