package io.leavesfly.stock.infrastructure.search;

import io.leavesfly.stock.domain.service.port.NewsSearchPort;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.stock.config.AppConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Anspire 搜索 API 适配器
 * 
 * 实现 NewsSearchPort 接口，封装 Anspire HTTP 调用细节。
 */
@Component
public class AnspireNewsAdapter implements NewsSearchPort {

    private static final Logger log = LoggerFactory.getLogger(AnspireNewsAdapter.class);

    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnspireNewsAdapter(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getProviderName() {
        return "anspire";
    }

    @Override
    public boolean isAvailable() {
        String apiKey = config.getAnspireApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public List<Map<String, Object>> search(String query, int maxResults) {
        if (!isAvailable()) {
            log.debug("Anspire API Key未配置");
            return Collections.emptyList();
        }

        try {
            String url = "https://api.anspire.ai/v1/search?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&num=" + maxResults;

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + config.getAnspireApiKey())
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

                log.info("Anspire搜索完成: {} 条结果", newsList.size());
                return newsList;
            }
        } catch (Exception e) {
            log.error("Anspire搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
