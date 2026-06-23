package io.leavesfly.stock.service;

import io.leavesfly.stock.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 智能情报服务
 * 对应Python版本的 src/services/intelligence_service.py
 * 功能: 情报收集、分析整合、智能摘要
 */
@Service
public class IntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceService.class);
    private final NewsSearchService newsService;
    private final LlmService llmService;

    public IntelligenceService(NewsSearchService newsService, LlmService llmService) {
        this.newsService = newsService;
        this.llmService = llmService;
    }

    /**
     * 获取股票智能情报摘要
     */
    public Map<String, Object> getIntelligence(String stockCode, String stockName) {
        Map<String, Object> intel = new LinkedHashMap<>();
        intel.put("stock_code", stockCode);
        intel.put("stock_name", stockName);

        // 搜索相关新闻
        List<Map<String, Object>> news = newsService.searchNews(stockCode, stockName);
        intel.put("news_count", news.size());
        intel.put("news", news);

        // LLM情报分析
        if (!news.isEmpty()) {
            String newsText = formatNewsForAnalysis(news);
            String analysis = llmService.chat(
                    "你是一位市场情报分析师，请分析以下新闻对股价的潜在影响，给出情绪判断(正面/负面/中性)和影响程度评估。",
                    "股票: " + stockName + "(" + stockCode + ")\n\n相关新闻:\n" + newsText);
            intel.put("analysis", analysis);
            intel.put("sentiment", extractSentiment(analysis));
        } else {
            intel.put("analysis", "暂无相关情报");
            intel.put("sentiment", "neutral");
        }
        return intel;
    }

    /**
     * 批量情报收集
     */
    public List<Map<String, Object>> batchIntelligence(List<String> stockCodes) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String code : stockCodes) {
            try {
                results.add(getIntelligence(code, code));
            } catch (Exception e) {
                log.error("情报收集失败: {}", code);
            }
        }
        return results;
    }

    private String formatNewsForAnalysis(List<Map<String, Object>> news) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(news.size(), 5); i++) {
            Map<String, Object> n = news.get(i);
            sb.append(i + 1).append(". ").append(n.get("title")).append("\n");
            Object content = n.get("content");
            if (content != null) {
                String c = content.toString();
                sb.append("   ").append(c.length() > 200 ? c.substring(0, 200) + "..." : c).append("\n");
            }
        }
        return sb.toString();
    }

    private String extractSentiment(String analysis) {
        String lower = analysis.toLowerCase();
        if (lower.contains("正面") || lower.contains("利好") || lower.contains("积极")) return "positive";
        if (lower.contains("负面") || lower.contains("利空") || lower.contains("消极")) return "negative";
        return "neutral";
    }
}
