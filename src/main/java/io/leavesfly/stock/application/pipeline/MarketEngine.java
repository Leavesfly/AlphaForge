package io.leavesfly.stock.application.pipeline;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import io.leavesfly.stock.domain.service.TechnicalAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * 回测引擎 + 配置管理 + 配置注册 + 市场概况 + 大盘复盘 + 市场策略
 * 对应Python: core/backtest_engine.py / config_manager.py / config_registry.py
 *             core/market_profile.py / market_review.py / market_strategy.py
 */
@Component
public class MarketEngine {

    private static final Logger log = LoggerFactory.getLogger(MarketEngine.class);
    private final DataFetcherManager dataFetcher;
    private final TechnicalAnalysisService techService;
    private final AppConfig config;

    public MarketEngine(DataFetcherManager dataFetcher, TechnicalAnalysisService techService, AppConfig config) {
        this.dataFetcher = dataFetcher;
        this.techService = techService;
        this.config = config;
    }

    // ===== 回测引擎(backtest_engine.py) =====

    /** 执行策略回测 */
    public Map<String, Object> runBacktest(String stockCode, String strategy, int days) {
        List<StockDailyData> data = dataFetcher.getHistoryData(stockCode, LocalDate.now().minusDays(days), LocalDate.now());
        if (data.size() < 10) return Map.of("error", "数据不足");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stock_code", stockCode);
        result.put("strategy", strategy);
        result.put("period_days", days);
        result.put("data_points", data.size());

        // 计算收益
        double startPrice = data.get(0).getClosePrice();
        double endPrice = data.get(data.size() - 1).getClosePrice();
        double totalReturn = (endPrice - startPrice) / startPrice * 100;

        // 最大回撤
        double peak = startPrice, maxDD = 0;
        for (StockDailyData d : data) {
            if (d.getClosePrice() > peak) peak = d.getClosePrice();
            double dd = (peak - d.getClosePrice()) / peak * 100;
            if (dd > maxDD) maxDD = dd;
        }

        // 夏普比率(简化)
        double[] returns = new double[data.size() - 1];
        for (int i = 1; i < data.size(); i++)
            returns[i - 1] = (data.get(i).getClosePrice() - data.get(i - 1).getClosePrice()) / data.get(i - 1).getClosePrice();
        double avgRet = Arrays.stream(returns).average().orElse(0);
        double stdRet = Math.sqrt(Arrays.stream(returns).map(r -> Math.pow(r - avgRet, 2)).average().orElse(0));
        double sharpe = stdRet > 0 ? (avgRet * 252 - 0.03) / (stdRet * Math.sqrt(252)) : 0;

        result.put("total_return_pct", Math.round(totalReturn * 100) / 100.0);
        result.put("max_drawdown_pct", Math.round(maxDD * 100) / 100.0);
        result.put("sharpe_ratio", Math.round(sharpe * 100) / 100.0);
        result.put("start_price", startPrice);
        result.put("end_price", endPrice);
        return result;
    }

    // ===== 市场概况(market_profile.py) =====

    /** 获取市场概况 */
    public Map<String, Object> getMarketProfile() {
        Map<String, Object> profile = new LinkedHashMap<>();
        // 上证指数
        List<StockDailyData> shData = dataFetcher.getHistoryData("000001", LocalDate.now().minusDays(5), LocalDate.now());
        if (!shData.isEmpty()) {
            StockDailyData latest = shData.get(shData.size() - 1);
            profile.put("sh_index", latest.getClosePrice());
            profile.put("sh_change_pct", latest.getChangePct());
        }
        profile.put("trading_day", tradingCalendar());
        return profile;
    }

    // ===== 大盘复盘(market_review.py) =====

    /** 生成大盘复盘报告 */
    public Map<String, Object> generateMarketReview() {
        Map<String, Object> review = new LinkedHashMap<>();
        String[] indices = {"000001", "399001", "399006", "000300"};
        List<Map<String, Object>> indexData = new ArrayList<>();
        for (String idx : indices) {
            var data = dataFetcher.getHistoryData(idx, LocalDate.now().minusDays(5), LocalDate.now());
            if (!data.isEmpty()) {
                StockDailyData d = data.get(data.size() - 1);
                indexData.add(Map.of("code", idx, "close", d.getClosePrice(), "change_pct", d.getChangePct() != null ? d.getChangePct() : 0));
            }
        }
        review.put("indices", indexData);
        review.put("date", LocalDate.now().toString());
        return review;
    }

    // ===== 市场策略(market_strategy.py) =====

    /** 根据市场环境推荐策略 */
    public String recommendStrategy(String marketSentiment) {
        switch (marketSentiment != null ? marketSentiment : "") {
            case "牛市": return "bull_trend";
            case "熊市": return "shrink_pullback";
            case "震荡": return "box_oscillation";
            default: return "ma_golden_cross";
        }
    }

    // ===== 配置注册(config_registry.py) =====

    /** 获取可配置项注册表 */
    public List<Map<String, Object>> getConfigRegistry() {
        return List.of(
            Map.of("key", "LLM_MODEL", "type", "string", "desc", "LLM模型", "required", true),
            Map.of("key", "LLM_API_KEY", "type", "secret", "desc", "API密钥", "required", true),
            Map.of("key", "STOCK_LIST", "type", "list", "desc", "自选股列表", "required", false),
            Map.of("key", "DATA_PROVIDER", "type", "enum", "desc", "数据源", "required", false),
            Map.of("key", "NOTIFICATION_CHANNELS", "type", "list", "desc", "通知渠道", "required", false)
        );
    }

    private String tradingCalendar() { return LocalDate.now().toString(); }
}
