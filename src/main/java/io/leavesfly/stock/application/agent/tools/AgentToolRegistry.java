package io.leavesfly.stock.application.agent.tools;

import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import io.leavesfly.stock.domain.service.TechnicalAnalysisService;
import io.leavesfly.stock.application.service.NewsSearchService;
import io.leavesfly.stock.application.service.BacktestService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Agent工具注册中心
 * 
 * 对应Python版本的 src/agent/tools/registry.py
 * 为Agent提供可调用的工具集合
 */
@Component
public class AgentToolRegistry {

    private final DataFetcherManager dataFetcher;
    private final TechnicalAnalysisService technicalService;
    private final NewsSearchService newsService;
    private final BacktestService backtestService;

    /** 工具定义列表(用于LLM Function Calling) */
    private final List<Map<String, Object>> toolDefinitions;

    public AgentToolRegistry(DataFetcherManager dataFetcher, TechnicalAnalysisService technicalService,
                            NewsSearchService newsService, BacktestService backtestService) {
        this.dataFetcher = dataFetcher;
        this.technicalService = technicalService;
        this.newsService = newsService;
        this.backtestService = backtestService;
        this.toolDefinitions = buildToolDefinitions();
    }

    /** 获取所有工具定义(OpenAI Function Calling格式) */
    public List<Map<String, Object>> getToolDefinitions() {
        return toolDefinitions;
    }

    /**
     * 执行工具调用
     *
     * @param toolName 工具名称
     * @param args     工具参数
     * @return 工具执行结果
     */
    public String executeTool(String toolName, Map<String, Object> args) {
        try {
            switch (toolName) {
                case "get_stock_price": return getStockPrice(args);
                case "get_stock_history": return getStockHistory(args);
                case "technical_analysis": return runTechnicalAnalysis(args);
                case "search_news": return searchNews(args);
                case "run_backtest": return runBacktest(args);
                case "get_market_overview": return getMarketOverview(args);
                default: return "未知工具: " + toolName;
            }
        } catch (Exception e) {
            return "工具执行失败: " + e.getMessage();
        }
    }

    // ========== 工具实现 ==========

    private String getStockPrice(Map<String, Object> args) {
        String code = (String) args.get("stock_code");
        Map<String, Object> quote = dataFetcher.getRealtimeQuote(code);
        return quote.isEmpty() ? "无法获取行情数据" : quote.toString();
    }

    private String getStockHistory(Map<String, Object> args) {
        String code = (String) args.get("stock_code");
        int days = args.containsKey("days") ? ((Number) args.get("days")).intValue() : 30;
        List<StockDailyData> data = dataFetcher.getHistoryData(code, LocalDate.now().minusDays(days), LocalDate.now());
        if (data.isEmpty()) return "无法获取历史数据";
        StringBuilder sb = new StringBuilder("最近" + days + "天行情:\n");
        int start = Math.max(0, data.size() - 10);
        for (int i = start; i < data.size(); i++) {
            StockDailyData d = data.get(i);
            sb.append(String.format("%s: 收盘%.2f 涨跌%.2f%%\n", d.getTradeDate(), d.getClosePrice(), d.getChangePct() != null ? d.getChangePct() : 0));
        }
        return sb.toString();
    }

    private String runTechnicalAnalysis(Map<String, Object> args) {
        String code = (String) args.get("stock_code");
        List<StockDailyData> data = dataFetcher.getHistoryData(code, LocalDate.now().minusDays(60), LocalDate.now());
        if (data.isEmpty()) return "数据不足";
        Map<String, Object> result = technicalService.analyze(data);
        return result.toString();
    }

    private String searchNews(Map<String, Object> args) {
        String code = (String) args.get("stock_code");
        String name = (String) args.getOrDefault("stock_name", code);
        List<Map<String, Object>> news = newsService.searchNews(code, name);
        if (news.isEmpty()) return "未找到相关新闻";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> n : news) {
            sb.append("• ").append(n.get("title")).append("\n");
        }
        return sb.toString();
    }

    private String runBacktest(Map<String, Object> args) {
        String code = (String) args.get("stock_code");
        String strategy = (String) args.getOrDefault("strategy", "ma_golden_cross");
        int days = args.containsKey("days") ? ((Number) args.get("days")).intValue() : 180;
        var result = backtestService.runBacktest(code, strategy, LocalDate.now().minusDays(days), LocalDate.now(), 100000);
        return result != null ? String.format("回测结果: 收益%.2f%% 最大回撤%.2f%% 胜率%.1f%%",
                result.getTotalReturnPct(), result.getMaxDrawdownPct(), result.getWinRatePct()) : "回测失败";
    }

    private String getMarketOverview(Map<String, Object> args) {
        return "请查看大盘数据..."; // 简化
    }

    // ========== 构建工具定义 ==========

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(buildTool("get_stock_price", "获取股票实时行情", Map.of("stock_code", "string:股票代码")));
        tools.add(buildTool("get_stock_history", "获取股票历史K线", Map.of("stock_code", "string:股票代码", "days", "integer:天数")));
        tools.add(buildTool("technical_analysis", "技术指标分析", Map.of("stock_code", "string:股票代码")));
        tools.add(buildTool("search_news", "搜索股票相关新闻", Map.of("stock_code", "string:股票代码", "stock_name", "string:股票名称")));
        tools.add(buildTool("run_backtest", "执行策略回测", Map.of("stock_code", "string:股票代码", "strategy", "string:策略名", "days", "integer:回测天数")));
        tools.add(buildTool("get_market_overview", "获取大盘概况", Map.of()));
        return tools;
    }

    private Map<String, Object> buildTool(String name, String description, Map<String, String> params) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String[] parts = entry.getValue().split(":");
            properties.put(entry.getKey(), Map.of("type", parts[0], "description", parts.length > 1 ? parts[1] : ""));
        }
        parameters.put("properties", properties);
        fn.put("parameters", parameters);
        tool.put("function", fn);
        return tool;
    }
}
