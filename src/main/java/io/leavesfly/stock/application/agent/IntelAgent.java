package io.leavesfly.stock.application.agent;

import io.leavesfly.stock.infrastructure.llm.LlmService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 情报分析Agent - 新闻情绪和市场情报解读
 */
@Component
public class IntelAgent implements BaseAgent {

    private final LlmService llmService;

    public IntelAgent(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String getName() { return "intel"; }

    @Override
    public String getDescription() { return "情报分析Agent - 新闻情绪和市场情报解读"; }

    @Override
    public AgentOrchestrator.AgentOpinion analyze(Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("股票: ").append(context.get("stock_code")).append(" ").append(context.get("stock_name")).append("\n\n");
        
        Object news = context.get("news");
        if (news instanceof List) {
            prompt.append("相关新闻:\n");
            for (Object item : (List<?>) news) {
                if (item instanceof Map) {
                    prompt.append("- ").append(((Map<?, ?>) item).get("title")).append("\n");
                    Object content = ((Map<?, ?>) item).get("content");
                    if (content != null) {
                        String contentStr = content.toString();
                        if (contentStr.length() > 200) contentStr = contentStr.substring(0, 200) + "...";
                        prompt.append("  ").append(contentStr).append("\n");
                    }
                }
            }
        }

        String response = llmService.chat(
                "你是一位市场情报分析师。请根据新闻信息分析市场情绪和消息面影响。" +
                "给出: 1.情绪判断(正面/负面/中性) 2.影响程度 3.信号 4.评分(0-100) 5.置信度(0-1)",
                prompt.toString());

        return parseResponse(response);
    }

    private AgentOrchestrator.AgentOpinion parseResponse(String response) {
        String signal = "neutral";
        int score = 50;
        double confidence = 0.5;

        String lower = response.toLowerCase();
        if (lower.contains("正面") || lower.contains("利好") || lower.contains("buy")) {
            signal = "buy"; score = 65; confidence = 0.6;
        } else if (lower.contains("负面") || lower.contains("利空") || lower.contains("sell")) {
            signal = "sell"; score = 35; confidence = 0.6;
        }
        return new AgentOrchestrator.AgentOpinion("intel", response, signal, score, confidence);
    }
}
