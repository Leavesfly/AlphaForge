package com.stock.service;

import com.stock.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 新闻搜索服务
 * 
 * 对应Python版本的 src/search_service.py
 * 支持Tavily和Anspire两种搜索提供商
 */
@Service
public class NewsSearchService {

    private static final Logger log = LoggerFactory.getLogger(NewsSearchService.class);
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NewsSearchService(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 搜索股票相关新闻
     *
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @return 新闻列表
     */
    public List<Map<String, Object>> searchNews(String stockCode, String stockName) {
        String provider = config.getSearchProvider();
        try {
            switch (provider.toLowerCase()) {
                case "tavily":
                    return searchWithTavily(stockCode, stockName);
                case "anspire":
                    return searchWithAnspire(stockCode, stockName);
                default:
                    return searchWithTavily(stockCode, stockName);
            }
        } catch (Exception e) {
            log.error("新闻搜索失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 使用Tavily API搜索
     */
    private List<Map<String, Object>> searchWithTavily(String stockCode, String stockName) throws Exception {
        String apiKey = config.getTavilyApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            log.debug("Tavily API Key未配置，跳过新闻搜索");
            return Collections.emptyList();
        }

        String query = buildSearchQuery(stockCode, stockName);
        
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("api_key", apiKey);
        payload.put("query", query);
        payload.put("max_results", config.getNewsMaxResults());
        payload.put("search_depth", "basic");
        payload.put("include_answer", false);

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(payload),
                MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(TAVILY_API_URL)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return Collections.emptyList();
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.path("results");

            if (!results.isArray()) return Collections.emptyList();

            List<Map<String, Object>> newsList = new ArrayList<>();
            for (JsonNode item : results) {
                Map<String, Object> news = new LinkedHashMap<>();
                news.put("title", item.path("title").asText(""));
                news.put("url", item.path("url").asText(""));
                news.put("content", item.path("content").asText(""));
                news.put("source", extractDomain(item.path("url").asText("")));
                news.put("published_date", item.path("published_date").asText(""));
                newsList.add(news);
            }
            
            log.info("Tavily搜索完成: {} 条结果", newsList.size());
            return newsList;
        }
    }

    /**
     * 使用Anspire API搜索
     */
    private List<Map<String, Object>> searchWithAnspire(String stockCode, String stockName) throws Exception {
        String apiKey = config.getAnspireApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            log.debug("Anspire API Key未配置");
            return Collections.emptyList();
        }

        String query = buildSearchQuery(stockCode, stockName);
        String url = "https://api.anspire.ai/v1/search?q=" + 
                java.net.URLEncoder.encode(query, "UTF-8") + 
                "&num=" + config.getNewsMaxResults();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return Collections.emptyList();
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.path("results");

            if (!results.isArray()) return Collections.emptyList();

            List<Map<String, Object>> newsList = new ArrayList<>();
            for (JsonNode item : results) {
                Map<String, Object> news = new LinkedHashMap<>();
                news.put("title", item.path("title").asText(""));
                news.put("url", item.path("link").asText(""));
                news.put("content", item.path("snippet").asText(""));
                news.put("source", item.path("source").asText(""));
                newsList.add(news);
            }
            return newsList;
        }
    }

    /**
     * 构建搜索查询
     */
    private String buildSearchQuery(String stockCode, String stockName) {
        if (stockName != null && !stockName.isEmpty()) {
            return stockName + " 股票 最新消息 分析";
        }
        return stockCode + " 股票 最新动态";
    }

    /**
     * 从URL提取域名
     */
    private String extractDomain(String url) {
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            return parsedUrl.getHost();
        } catch (Exception e) {
            return "";
        }
    }
}
