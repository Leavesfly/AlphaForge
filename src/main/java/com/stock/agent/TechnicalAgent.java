package com.stock.agent;

import com.stock.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 技术分析Agent
 * 专注于K线形态、技术指标解读
 */
@Component
public class TechnicalAgent implements BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(TechnicalAgent.class);
    private final LlmService llmService;

    public TechnicalAgent(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String getName() { return "technical"; }

    @Override
    public String getDescription() { return "技术分析Agent - 专注K线形态和技术指标解读"; }

    @Override
    public AgentOrchestrator.AgentOpinion analyze(Map<String, Object> context) {
        String prompt = buildPrompt(context);
        String response = llmService.chat(
                "你是一位专业的技术分析师。请根据提供的技术指标数据给出明确的技术面分析结论。" +
                "你的回答必须包含: 1.趋势判断 2.关键指标解读 3.信号(buy/sell/neutral) 4.评分(0-100) 5.置信度(0-1)",
                prompt
        );
        return parseResponse(response);
    }

    private String buildPrompt(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("股票: ").append(context.get("stock_code")).append(" ").append(context.get("stock_name")).append("\n\n");
        
        Object tech = context.get("technical_analysis");
        if (tech instanceof Map) {
            sb.append("技术指标:\n");
            ((Map<?, ?>) tech).forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        
        Object history = context.get("history_data");
        if (history != null) {
            sb.append("\n历史行情:\n").append(history);
        }
        return sb.toString();
    }

    private AgentOrchestrator.AgentOpinion parseResponse(String response) {
        // 简单解析LLM回复
        String signal = "neutral";
        int score = 50;
        double confidence = 0.5;

        String lower = response.toLowerCase();
        if (lower.contains("strong_buy") || lower.contains("强烈买入")) {
            signal = "strong_buy"; score = 85; confidence = 0.8;
        } else if (lower.contains("buy") || lower.contains("买入")) {
            signal = "buy"; score = 70; confidence = 0.7;
        } else if (lower.contains("strong_sell") || lower.contains("强烈卖出")) {
            signal = "strong_sell"; score = 15; confidence = 0.8;
        } else if (lower.contains("sell") || lower.contains("卖出")) {
            signal = "sell"; score = 30; confidence = 0.7;
        }

        return new AgentOrchestrator.AgentOpinion("technical", response, signal, score, confidence);
    }
}
