package io.leavesfly.alphaforge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用配置 — 仅管理全局配置（服务端口、股票列表）
 *
 * LLM 配置已独立为 LlmConfig Bean。
 * 通知、搜索、Bot、评分、认证等子配置已独立为 @Component Bean。
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private final EnvVarProvider envVarProvider;

    // ========== 全局服务配置 ==========
    private int serverPort = 8000;
    private String serverHost = "0.0.0.0";

    // ========== 股票配置 ==========
    private List<String> stockList = new ArrayList<>();
    private String market = "A";
    private int historyDays = 60;

    public AppConfig(EnvVarProvider envVarProvider) {
        this.envVarProvider = envVarProvider;
    }

    @PostConstruct
    public void init() {
        loadConfig();
        log.info("配置加载完成: market={}, stocks={}", market, stockList.size());
    }

    private void loadConfig() {
        serverPort = envVarProvider.getInt("SERVER_PORT", 8000);
        serverHost = envVarProvider.get("SERVER_HOST", "0.0.0.0");

        String stockStr = envVarProvider.get("STOCK_LIST", "");
        if (!stockStr.isEmpty()) {
            stockList = Arrays.stream(stockStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        market = envVarProvider.get("MARKET", "A");
        historyDays = envVarProvider.getInt("HISTORY_DAYS", 60);
    }

    // ========== 环境变量读取（委托给 EnvVarProvider，供子配置加载时使用）==========

    public String getEnv(String key, String defaultValue) {
        return envVarProvider.get(key, defaultValue);
    }

    public int getIntEnv(String key, int defaultValue) {
        return envVarProvider.getInt(key, defaultValue);
    }

    public double getDoubleEnv(String key, double defaultValue) {
        return envVarProvider.getDouble(key, defaultValue);
    }

    public boolean getBoolEnv(String key, boolean defaultValue) {
        return envVarProvider.getBool(key, defaultValue);
    }

    // ========== Getter ==========

    public int getServerPort() { return serverPort; }
    public String getServerHost() { return serverHost; }
    public List<String> getStockList() { return stockList; }
    public String getMarket() { return market; }
    public int getHistoryDays() { return historyDays; }
}
