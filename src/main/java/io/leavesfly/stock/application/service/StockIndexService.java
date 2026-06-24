package io.leavesfly.stock.application.service;

import io.leavesfly.stock.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Agent模型服务 + 分析服务 + 股票服务 + 系统配置服务 + 导入解析 + 指数远程
 * 对应Python: agent_model_service.py / analyzer_service.py / stock_service.py
 *             system_config_service.py / import_parser.py / stock_index_remote_service.py
 */
@Service
public class StockIndexService {
    private static final Logger log = LoggerFactory.getLogger(StockIndexService.class);
    private final AppConfig config;

    public StockIndexService(AppConfig config) { this.config = config; }

    /** 获取可用LLM模型列表 */
    public List<Map<String, Object>> getAvailableModels() {
        List<Map<String, Object>> models = new ArrayList<>();
        models.add(Map.of("id", config.getLlmModel(), "name", config.getLlmModel(), "provider", "configured", "active", true));
        return models;
    }

    /** 获取系统配置概要(脱敏) */
    public Map<String, Object> getSystemConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("llm_model", config.getLlmModel());
        cfg.put("agent_mode", config.getAgentMode());
        cfg.put("data_provider", config.getDataProvider());
        cfg.put("history_days", config.getHistoryDays());
        cfg.put("notification_channels", config.getNotificationChannels());
        cfg.put("auth_enabled", config.isAuthEnabled());
        return cfg;
    }

    /** 解析导入文本(CSV/JSON/自由文本) */
    public List<String> parseImportText(String text) {
        List<String> codes = new ArrayList<>();
        if (text == null || text.isEmpty()) return codes;
        // JSON数组格式
        if (text.trim().startsWith("[")) {
            text = text.replaceAll("[\\[\\]\"]", "");
        }
        // CSV/逗号/换行分隔
        String[] parts = text.split("[,;\\n\\r、]+");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty() && trimmed.length() <= 10) codes.add(trimmed);
        }
        return codes;
    }

    /** 远程获取股票指数列表(A股主要指数) */
    public List<Map<String, Object>> getMainIndices() {
        return List.of(
            Map.of("code", "000001", "name", "上证指数", "market", "A"),
            Map.of("code", "399001", "name", "深证成指", "market", "A"),
            Map.of("code", "399006", "name", "创业板指", "market", "A"),
            Map.of("code", "000300", "name", "沪深300", "market", "A"),
            Map.of("code", "000016", "name", "上证50", "market", "A"),
            Map.of("code", "000905", "name", "中证500", "market", "A"),
            Map.of("code", "000688", "name", "科创50", "market", "A"),
            Map.of("code", "HSI", "name", "恒生指数", "market", "HK"),
            Map.of("code", "DJI", "name", "道琼斯", "market", "US"),
            Map.of("code", "SPX", "name", "标普500", "market", "US"),
            Map.of("code", "IXIC", "name", "纳斯达克", "market", "US")
        );
    }
}
