package io.leavesfly.stock.application.agent.tools.impl;

import io.leavesfly.stock.application.agent.tools.Tool;
import io.leavesfly.stock.application.agent.tools.ToolException;
import io.leavesfly.stock.application.service.market.NewsSearchService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索股票相关新闻工具
 */
@Component
public class SearchNewsTool implements Tool {

    private final NewsSearchService newsService;

    public SearchNewsTool(NewsSearchService newsService) {
        this.newsService = newsService;
    }

    @Override
    public String name() {
        return "search_news";
    }

    @Override
    public String description() {
        return "搜索与指定股票相关的新闻资讯和舆情信息";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> stockCode = new HashMap<>();
        stockCode.put("type", "string");
        stockCode.put("description", "股票代码，如 600519、000001");
        properties.put("stock_code", stockCode);

        Map<String, Object> stockName = new HashMap<>();
        stockName.put("type", "string");
        stockName.put("description", "股票名称（可选，提升搜索精度）");
        properties.put("stock_name", stockName);

        params.put("properties", properties);
        params.put("required", new String[]{"stock_code"});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String code = (String) args.get("stock_code");
        if (code == null || code.isBlank()) {
            throw new ToolException("参数 stock_code 不能为空", "PARAM_MISSING");
        }
        String name = (String) args.getOrDefault("stock_name", code);

        List<Map<String, Object>> news = newsService.searchNews(code, name);
        if (news == null || news.isEmpty()) {
            return "未找到 " + code + " 的相关新闻";
        }

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> n : news) {
            sb.append("• ").append(n.get("title")).append("\n");
        }
        return sb.toString().trim();
    }
}
