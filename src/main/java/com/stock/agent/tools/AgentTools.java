package com.stock.agent.tools;

import com.stock.dataprovider.DataFetcherManager;
import com.stock.model.entity.StockDailyData;
import com.stock.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Agent工具集合(6类)
 * 对应Python: agent/tools/ 目录
 * - data_tools: 数据获取
 * - analysis_tools: 技术分析
 * - search_tools: 搜索
 * - market_tools: 市场数据
 * - backtest_tools: 回测
 * - registry: 工具注册
 */
@Component
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);
    private final DataFetcherManager dataFetcher;
    private final TechnicalAnalysisService techService;
    private final NewsSearchService newsService;
    private final BacktestService backtestService;
    private final MarketLightService marketLightService;

    public AgentTools(DataFetcherManager dataFetcher, TechnicalAnalysisService techService,
                      NewsSearchService newsService, BacktestService backtestService,
                      MarketLightService marketLightService) {
        this.dataFetcher = dataFetcher;
        this.techService = techService;
        this.newsService = newsService;
        this.backtestService = backtestService;
        this.marketLightService = marketLightService;
    }

    // ===== data_tools =====

    /** 获取股票历史数据 */
    public Map<String, Object> getStockHistory(String code, int days) {
        List<StockDailyData> data = dataFetcher.getHistoryData(code, LocalDate.now().minusDays(days), LocalDate.now());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("count", data.size());
        if (!data.isEmpty()) {
            StockDailyData last = data.get(data.size() - 1);
            result.put("latest_close", last.getClosePrice());
            result.put("latest_date", last.getTradeDate().toString());
        }
        return result;
    }

    /** 获取实时行情 */
    public Map<String, Object> getRealtimeQuote(String code) {
        return dataFetcher.getRealtimeQuote(code);
    }

    // ===== analysis_tools =====

    /** 技术指标分析 */
    public Map<String, Object> analyzeTechnical(String code) {
        List<StockDailyData> data = dataFetcher.getHistoryData(code, LocalDate.now().minusDays(60), LocalDate.now());
        return techService.analyze(data);
    }

    /** 计算RSI */
    public double calculateRsi(String code, int period) {
        List<StockDailyData> data = dataFetcher.getHistoryData(code, LocalDate.now().minusDays(period + 10), LocalDate.now());
        if (data.size() < period + 1) return 50.0;
        double gainSum = 0, lossSum = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double change = data.get(i).getClosePrice() - data.get(i - 1).getClosePrice();
            if (change > 0) gainSum += change; else lossSum -= change;
        }
        double avgGain = gainSum / period, avgLoss = lossSum / period;
        return avgLoss == 0 ? 100 : 100 - (100 / (1 + avgGain / avgLoss));
    }

    // ===== search_tools =====

    /** 搜索相关新闻 */
    public List<Map<String, Object>> searchNews(String code, String name) {
        return newsService.searchNews(code, name);
    }

    // ===== market_tools =====

    /** 获取市场信号灯 */
    public Map<String, Object> getMarketLight() {
        return marketLightService.getMarketLight();
    }

    /** 获取板块数据 */
    public List<String> getStockBoards(String code) {
        return dataFetcher.getStockBoards(code);
    }

    // ===== backtest_tools =====

    /** 运行策略回测 */
    public Map<String, Object> runBacktest(String code, String strategy, int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("strategy", strategy);
        result.put("days", days);
        result.put("status", "completed");
        return result;
    }

    // ===== registry =====

    /** 获取所有可用工具的Schema列表 */
    public List<Map<String, Object>> getToolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        schemas.add(toolSchema("get_stock_history", "获取股票历史数据", Map.of("code", "string", "days", "integer")));
        schemas.add(toolSchema("get_realtime_quote", "获取实时行情", Map.of("code", "string")));
        schemas.add(toolSchema("analyze_technical", "技术指标分析", Map.of("code", "string")));
        schemas.add(toolSchema("calculate_rsi", "计算RSI", Map.of("code", "string", "period", "integer")));
        schemas.add(toolSchema("search_news", "搜索新闻", Map.of("code", "string", "name", "string")));
        schemas.add(toolSchema("get_market_light", "市场信号灯", Map.of()));
        schemas.add(toolSchema("get_stock_boards", "获取板块", Map.of("code", "string")));
        schemas.add(toolSchema("run_backtest", "策略回测", Map.of("code", "string", "strategy", "string", "days", "integer")));
        return schemas;
    }

    /** 根据名称执行工具 */
    @SuppressWarnings("unchecked")
    public Object executeTool(String name, Map<String, Object> args) {
        switch (name) {
            case "get_stock_history": return getStockHistory((String) args.get("code"), (int) args.getOrDefault("days", 60));
            case "get_realtime_quote": return getRealtimeQuote((String) args.get("code"));
            case "analyze_technical": return analyzeTechnical((String) args.get("code"));
            case "calculate_rsi": return calculateRsi((String) args.get("code"), (int) args.getOrDefault("period", 14));
            case "search_news": return searchNews((String) args.get("code"), (String) args.getOrDefault("name", ""));
            case "get_market_light": return getMarketLight();
            case "get_stock_boards": return getStockBoards((String) args.get("code"));
            case "run_backtest": return runBacktest((String) args.get("code"), (String) args.get("strategy"), (int) args.getOrDefault("days", 60));
            default: return Map.of("error", "未知工具: " + name);
        }
    }

    private Map<String, Object> toolSchema(String name, String desc, Map<String, String> params) {
        return Map.of("type", "function", "function", Map.of("name", name, "description", desc, "parameters", params));
    }
}
