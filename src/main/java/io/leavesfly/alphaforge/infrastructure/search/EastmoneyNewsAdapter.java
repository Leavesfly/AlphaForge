package io.leavesfly.alphaforge.infrastructure.search;

import io.leavesfly.alphaforge.domain.service.port.NewsSearchPort;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 东方财富新闻搜索适配器
 *
 * 通过东方财富搜索 API 获取中文财经新闻，无需 API Key。
 * 适用于 A 股市场的新闻舆情采集，作为 Tavily/Anspire 的免费中文补充源。
 *
 * 数据来源:
 *   - 东方财富搜索接口: searchapi.eastmoney.com
 *   - 个股资讯接口: np-cnbond.eastmoney.com
 */
@Component
public class EastmoneyNewsAdapter implements NewsSearchPort {

    private static final Logger log = LoggerFactory.getLogger(EastmoneyNewsAdapter.class);

    /** 东方财富搜索 API — 关键词检索新闻 */
    private static final String SEARCH_URL = "https://searchapi.eastmoney.com/api/suggest/get";

    /** 东方财富资讯搜索 API — 关键词检索新闻资讯 */
    private static final String NEWS_SEARCH_URL = "https://search-api-web.eastmoney.com/search/jsonp";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EastmoneyNewsAdapter(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "eastmoney";
    }

    @Override
    public boolean isAvailable() {
        return true; // 东方财富接口无需 API Key，始终可用
    }

    @Override
    public List<Map<String, Object>> search(String query, int maxResults) {
        // 先尝试搜索接口（关键词检索）
        List<Map<String, Object>> results = searchByKeyword(query, maxResults);
        if (!results.isEmpty()) {
            return results;
        }

        // 搜索接口无结果时，尝试个股资讯接口
        return searchStockNews(query, maxResults);
    }

    /**
     * 通过东方财富搜索 API 关键词检索
     */
    private List<Map<String, Object>> searchByKeyword(String query, int maxResults) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = SEARCH_URL + "?input=" + encodedQuery
                    + "&type=14&count=" + maxResults
                    + "&_=" + System.currentTimeMillis();

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://so.eastmoney.com/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return Collections.emptyList();
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                JsonNode quotations = root.path("QuotationCodeTable");

                if (!quotations.isArray()) return Collections.emptyList();

                List<Map<String, Object>> newsList = new ArrayList<>();
                for (JsonNode item : quotations) {
                    Map<String, Object> news = new LinkedHashMap<>();
                    news.put("title", item.path("Name").asText("") + " " + item.path("Code").asText(""));
                    news.put("url", "https://so.eastmoney.com/web/s?keyword=" + encodedQuery);
                    news.put("content", item.path("MnemonicInfo").asText(""));
                    news.put("source", "eastmoney");
                    news.put("published_date", "");
                    newsList.add(news);
                    if (newsList.size() >= maxResults) break;
                }

                log.info("东方财富搜索完成: {} 条结果", newsList.size());
                return newsList;
            }
        } catch (Exception e) {
            log.error("东方财富搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 通过东方财富资讯搜索接口检索
     * 使用 search-api-web.eastmoney.com 获取新闻资讯
     */
    private List<Map<String, Object>> searchStockNews(String query, int maxResults) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            // 使用东财资讯搜索接口
            String url = NEWS_SEARCH_URL + "?cb=jQuery&param=%7B%22uid%22%3A%22%22%2C%22keyword%22%3A%22" + encodedQuery + "%22%2C%22type%22%3A%5B%22cmsArticleWebOld%22%5D%2C%22client%22%3A%22web%22%2C%22clientType%22%3A%22web%22%2C%22clientVersion%22%3A%22curr%22%2C%22param%22%3A%7B%22cmsArticleWebOld%22%3A%7B%22searchScope%22%3A%22default%22%2C%22sort%22%3A%22default%22%2C%22pageIndex%22%3A1%2C%22pageSize%22%3A" + maxResults + "%2C%22preTag%22%3A%22%22%2C%22postTag%22%3A%22%22%7D%7D%7D";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://so.eastmoney.com/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return Collections.emptyList();
                }

                String body = response.body().string();
                // 该接口返回 JSONP 包裹，需要提取 JSON
                if (body.startsWith("jQuery")) {
                    int start = body.indexOf("(");
                    int end = body.lastIndexOf(")");
                    if (start > 0 && end > start) {
                        body = body.substring(start + 1, end);
                    }
                }

                JsonNode root = objectMapper.readTree(body);
                JsonNode dataNode = root.path("result").path("cmsArticleWebOld").path("list");

                if (!dataNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> newsList = new ArrayList<>();
                for (JsonNode item : dataNode) {
                    Map<String, Object> news = new LinkedHashMap<>();
                    news.put("title", item.path("title").asText(""));
                    news.put("url", item.path("url").asText(""));
                    news.put("content", item.path("content").asText(""));
                    news.put("source", item.path("source").asText("eastmoney"));
                    news.put("published_date", item.path("date").asText(""));
                    newsList.add(news);
                    if (newsList.size() >= maxResults) break;
                }

                log.info("东方财富资讯搜索完成: {} 条结果", newsList.size());
                return newsList;
            }
        } catch (Exception e) {
            log.error("东方财富资讯搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
