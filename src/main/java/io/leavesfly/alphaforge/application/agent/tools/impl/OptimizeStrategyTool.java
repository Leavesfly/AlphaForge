package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.engine.ParameterOptimizer;
import io.leavesfly.alphaforge.application.strategy.model.OptimizationResult;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数优化工具 — 供 LLM 自主触发策略参数网格搜索。
 *
 * 读取策略 YAML 中声明的 param_space，遍历所有参数组合，
 * 对每组参数运行回测，返回最优参数及 Top 候选结果。
 */
@Component
public class OptimizeStrategyTool implements Tool {

    private final StrategyCatalog catalog;
    private final ParameterOptimizer optimizer;
    private final MarketDataPort dataFetcher;

    public OptimizeStrategyTool(StrategyCatalog catalog, ParameterOptimizer optimizer,
                                 MarketDataPort dataFetcher) {
        this.catalog = catalog;
        this.optimizer = optimizer;
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "optimize_strategy";
    }

    @Override
    public String description() {
        return "对指定策略进行参数网格搜索优化，找到历史回测收益最优的参数组合";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> stockCode = new HashMap<>();
        stockCode.put("type", "string");
        stockCode.put("description", "股票代码，如 600519");
        properties.put("stock_code", stockCode);

        Map<String, Object> strategy = new HashMap<>();
        strategy.put("type", "string");
        strategy.put("description", "策略名称，如 ma_golden_cross");
        strategy.put("default", "ma_golden_cross");
        properties.put("strategy", strategy);

        Map<String, Object> days = new HashMap<>();
        days.put("type", "integer");
        days.put("description", "优化使用的历史数据范围（天），默认365天");
        days.put("default", 365);
        properties.put("days", days);

        params.put("properties", properties);
        params.put("required", new String[]{"stock_code", "strategy"});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String code = (String) args.get("stock_code");
        if (code == null || code.isBlank()) {
            throw new ToolException("参数 stock_code 不能为空", "PARAM_MISSING");
        }
        String strategyName = (String) args.get("strategy");
        if (strategyName == null || strategyName.isBlank()) {
            throw new ToolException("参数 strategy 不能为空", "PARAM_MISSING");
        }

        int days = 365;
        Object daysObj = args.get("days");
        if (daysObj instanceof Number) {
            days = ((Number) daysObj).intValue();
        }

        StrategyDefinition strategy = catalog.find(strategyName)
                .orElseThrow(() -> new ToolException("策略不存在: " + strategyName, "STRATEGY_NOT_FOUND"));

        if (strategy.getBacktest() == null || !strategy.getBacktest().hasParamSpace()) {
            return String.format("策略 %s 未声明 param_space，无法优化。请在 YAML 中添加 backtest.param_space 段。", strategyName);
        }

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        List<StockDailyData> data = dataFetcher.getHistoryData(code, start, end);
        if (data == null || data.isEmpty()) {
            return "无法获取历史数据: " + code;
        }

        OptimizationResult result = optimizer.optimize(strategy, data, 100000);

        if (result.getTotalCandidates() == 0) {
            return "优化失败：未生成有效参数组合";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("参数优化结果（%s, 策略=%s, %d天）：\n\n", code, strategyName, days));
        sb.append(String.format("搜索组合数: %d\n", result.getTotalCandidates()));
        sb.append(String.format("最优参数: %s\n", result.getBestParams()));
        sb.append(String.format("最优收益: %.2f%%\n", result.getBestReturnPct()));
        sb.append(String.format("最大回撤: %.2f%%\n", result.getBestMaxDrawdownPct()));
        sb.append(String.format("胜率: %.1f%%\n", result.getBestWinRatePct()));
        sb.append(String.format("夏普比率: %.3f\n\n", result.getBestSharpeRatio()));

        if (!result.getTopCandidates().isEmpty()) {
            sb.append("Top 候选结果:\n");
            for (int i = 0; i < result.getTopCandidates().size(); i++) {
                OptimizationResult.CandidateResult c = result.getTopCandidates().get(i);
                sb.append(String.format("%d. %s → 收益%.2f%% 回撤%.2f%% 胜率%.1f%% 夏普%.3f\n",
                        i + 1, c.params(), c.returnPct(), c.maxDrawdownPct(), c.winRatePct(), c.sharpeRatio()));
            }
        }
        return sb.toString().trim();
    }
}
