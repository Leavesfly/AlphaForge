package io.leavesfly.alphaforge.infrastructure.search;

import io.leavesfly.alphaforge.domain.service.port.NewsSearchPort;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.leavesfly.alphaforge.config.AppConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Tavily 搜索 API 适配器
 * 
 * 实现 NewsSearchPort 接口，封装 Tavily HTTP 调用细节。
 */
@Component
public class TavilyNewsAdapter implements NewsSearchPort {

    private static final Logger log = LoggerFactory.getLogger(TavilyNewsAdapter.class);
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TavilyNewsAdapter(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getProviderName() {
        return "tavily";
    }

    @Override
    public boolean isAvailable() {
        String apiKey = config.getTavilyApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public List<Map<String, Object>> search(String query, int maxResults) {
        if (!isAvailable()) {
            log.debug("Tavily API Key未配置，跳过新闻搜索");
            return Collections.emptyList();
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("api_key", config.getTavilyApiKey());
            payload.put("query", query);
            payload.put("max_results", maxResults);
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
        } catch (Exception e) {
            log.error("Tavily搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String extractDomain(String url) {
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            return parsedUrl.getHost();
        } catch (Exception e) {
            return "";
        }
    }
}
