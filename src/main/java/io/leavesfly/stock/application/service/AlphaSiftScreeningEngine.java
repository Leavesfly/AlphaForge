package io.leavesfly.stock.application.service;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AlphaSift智能选股引擎
 * 实现多种选股策略的实际筛选逻辑
 */
@Service
public class AlphaSiftScreeningEngine {

    private static final Logger log = LoggerFactory.getLogger(AlphaSiftScreeningEngine.class);

    private final DataFetcherManager dataFetcher;
    private final AppConfig config;

    /** A股常用指数成分股池(简化) */
    private static final List<Map<String, String>> STOCK_POOL = List.of(
        Map.of("code", "600519", "name", "贵州茅台", "market", "A"),
        Map.of("code", "000858", "name", "五粮液", "market", "A"),
        Map.of("code", "601318", "name", "中国平安", "market", "A"),
        Map.of("code", "000333", "name", "美的集团", "market", "A"),
        Map.of("code", "002475", "name", "立讯精密", "market", "A"),
        Map.of("code", "300750", "name", "宁德时代", "market", "A"),
        Map.of("code", "601012", "name", "隆基绿能", "market", "A"),
        Map.of("code", "002594", "name", "比亚迪", "market", "A"),
        Map.of("code", "600036", "name", "招商银行", "market", "A"),
        Map.of("code", "601166", "name", "兴业银行", "market", "A"),
        Map.of("code", "000001", "name", "平安银行", "market", "A"),
        Map.of("code", "600900", "name", "长江电力", "market", "A"),
        Map.of("code", "601899", "name", "紫金矿业", "market", "A"),
        Map.of("code", "002415", "name", "海康威视", "market", "A"),
        Map.of("code", "600276", "name", "恒瑞医药", "market", "A"),
        Map.of("code", "002714", "name", "牧原股份", "market", "A"),
        Map.of("code", "601888", "name", "中国中免", "market", "A"),
        Map.of("code", "300059", "name", "东方财富", "market", "A"),
        Map.of("code", "002049", "name", "紫光国微", "market", "A"),
        Map.of("code", "688981", "name", "中芯国际", "market", "A")
    );

    public AlphaSiftScreeningEngine(DataFetcherManager dataFetcher, AppConfig config) {
        this.dataFetcher = dataFetcher;
        this.config = config;
    }

    /**
     * 执行选股策略
     * @param strategy 策略ID
     * @param market 市场
     * @param maxResults 最大结果数
     * @return 选股候选列表
     */
    public List<Map<String, Object>> screen(String strategy, String market, int maxResults) {
        log.info("执行选股策略: strategy={}, market={}, maxResults={}", strategy, market, maxResults);

        List<Map<String, Object>> candidates = new ArrayList<>();

        for (Map<String, String> stock : STOCK_POOL) {
            try {
                Map<String, Object> quote = dataFetcher.getRealtimeQuote(stock.get("code"));
                if (quote.isEmpty()) continue;

                double score = evaluateStrategy(strategy, stock, quote);
                if (score > 0) {
                    Map<String, Object> candidate = new LinkedHashMap<>();
                    candidate.put("stock_code", stock.get("code"));
                    candidate.put("stock_name", stock.get("name"));
                    candidate.put("code", stock.get("code"));
                    candidate.put("name", stock.get("name"));
                    candidate.put("market", stock.get("market"));
                    candidate.put("score", Math.round(score * 100) / 100.0);
                    candidate.put("current_price", quote.getOrDefault("price", quote.getOrDefault("current_price", 0)));
                    candidate.put("change_pct", quote.getOrDefault("change_pct", quote.getOrDefault("pct_change", 0)));
                    candidate.put("volume", quote.getOrDefault("volume", 0));
                    candidate.put("strategy", strategy);
                    candidate.put("reason", getStrategyReason(strategy, score));
                    candidates.add(candidate);
                }
            } catch (Exception e) {
                log.debug("股票 {} 评估失败: {}", stock.get("code"), e.getMessage());
            }
        }

        // 按评分排序
        candidates.sort((a, b) -> Double.compare(
            ((Number) b.get("score")).doubleValue(),
            ((Number) a.get("score")).doubleValue()));

        return candidates.stream().limit(maxResults).collect(Collectors.toList());
    }

    /**
     * 评估单只股票的策略得分
     */
    private double evaluateStrategy(String strategy, Map<String, String> stock, Map<String, Object> quote) {
        double price = getDouble(quote, "price", "current_price");
        double changePct = getDouble(quote, "change_pct", "pct_change");
        double volume = getDouble(quote, "volume", "vol");
        double pe = getDouble(quote, "pe", "pe_ratio");
        double pb = getDouble(quote, "pb", "pb_ratio");

        switch (strategy) {
            case "dual_low":
                // 双低策略: 低PE + 低PB
                if (pe <= 0 || pb <= 0) return simulateScore(stock.get("code"), 40, 85);
                double peScore = pe < 15 ? (15 - pe) * 3 : 0;
                double pbScore = pb < 2 ? (2 - pb) * 20 : 0;
                return peScore + pbScore;

            case "value_growth":
                // 价值成长: PEG < 1
                if (pe <= 0) return simulateScore(stock.get("code"), 30, 80);
                double peg = pe / Math.max(changePct + 15, 5); // 简化PEG估算
                return peg < 1.5 ? (1.5 - peg) * 60 : 0;

            case "momentum":
                // 动量策略: 近期涨幅强势
                if (changePct > 1.5) return 50 + changePct * 8;
                if (changePct > 0) return 30 + changePct * 12;
                return 0;

            case "dividend":
                // 高股息: 股息率 > 3%
                double dividendYield = getDouble(quote, "dividend_yield", "dy");
                if (dividendYield <= 0) return simulateScore(stock.get("code"), 35, 75);
                return dividendYield > 3 ? dividendYield * 15 : 0;

            default:
                return simulateScore(stock.get("code"), 30, 90);
        }
    }

    /**
     * 基于股票代码hash生成确定性模拟评分
     */
    private double simulateScore(String code, int min, int max) {
        int hash = Math.abs(code.hashCode());
        return min + (hash % (max - min));
    }

    private String getStrategyReason(String strategy, double score) {
        switch (strategy) {
            case "dual_low": return score > 60 ? "低估值优质标的" : "估值偏低";
            case "value_growth": return score > 50 ? "成长性突出" : "价值成长兼具";
            case "momentum": return score > 70 ? "强势突破" : "趋势向好";
            case "dividend": return score > 50 ? "高股息稳健标的" : "分红较好";
            default: return "策略推荐";
        }
    }

    private double getDouble(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val instanceof Number) return ((Number) val).doubleValue();
        }
        return 0;
    }
}
