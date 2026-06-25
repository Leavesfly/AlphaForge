package io.leavesfly.stock.application.service;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.port.NewsSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 新闻搜索服务
 *
 * 通过 NewsSearchPort 接口与搜索提供商解耦，不再直接依赖 HTTP 客户端。
 */
@Service
public class NewsSearchService {

    private static final Logger log = LoggerFactory.getLogger(NewsSearchService.class);

    private final AppConfig config;
    private final Map<String, NewsSearchPort> adapterMap;

    public NewsSearchService(AppConfig config, List<NewsSearchPort> adapters) {
        this.config = config;
        this.adapterMap = new LinkedHashMap<>();
        for (NewsSearchPort adapter : adapters) {
            adapterMap.put(adapter.getProviderName().toLowerCase(), adapter);
        }
        log.info("新闻搜索服务初始化, 已注册 {} 个搜索适配器: {}", adapters.size(), adapterMap.keySet());
    }

    /**
     * 搜索股票相关新闻
     *
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @return 新闻列表
     */
    public List<Map<String, Object>> searchNews(String stockCode, String stockName) {
        String provider = config.getSearchProvider().toLowerCase();
        String query = buildSearchQuery(stockCode, stockName);
        int maxResults = config.getNewsMaxResults();

        // 1. 尝试配置的首选提供商
        NewsSearchPort adapter = adapterMap.get(provider);
        if (adapter != null && adapter.isAvailable()) {
            try {
                List<Map<String, Object>> results = adapter.search(query, maxResults);
                if (!results.isEmpty()) return results;
            } catch (Exception e) {
                log.warn("首选搜索提供商 {} 失败: {}", provider, e.getMessage());
            }
        }

        // 2. Fallback: 尝试其他可用适配器
        for (NewsSearchPort fallback : adapterMap.values()) {
            if (fallback.getProviderName().equals(provider)) continue;
            if (!fallback.isAvailable()) continue;
            try {
                List<Map<String, Object>> results = fallback.search(query, maxResults);
                if (!results.isEmpty()) {
                    log.info("使用备选搜索提供商: {}", fallback.getProviderName());
                    return results;
                }
            } catch (Exception e) {
                log.warn("备选搜索提供商 {} 失败: {}", fallback.getProviderName(), e.getMessage());
            }
        }

        log.debug("所有搜索提供商均无法获取新闻: {}", stockCode);
        return Collections.emptyList();
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
}
