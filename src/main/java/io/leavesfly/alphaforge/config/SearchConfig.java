package io.leavesfly.alphaforge.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 搜索配置 — 新闻搜索提供商和 API Key，独立 Spring Bean
 */
@Component
public class SearchConfig {

    private final EnvVarProvider env;

    public SearchConfig(EnvVarProvider env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        searchProvider = env.get("SEARCH_PROVIDER", "tavily");
        tavilyApiKey = env.get("TAVILY_API_KEY", "");
        anspireApiKey = env.get("ANSPIRE_API_KEY", "");
        newsMaxResults = env.getInt("NEWS_MAX_RESULTS", 5);
    }

    // Getters
    public String getSearchProvider() { return searchProvider; }
    public String getTavilyApiKey() { return tavilyApiKey; }
    public String getAnspireApiKey() { return anspireApiKey; }
    public int getNewsMaxResults() { return newsMaxResults; }

    private String searchProvider = "tavily";
    private String tavilyApiKey = "";
    private String anspireApiKey = "";
    private int newsMaxResults = 5;
}
