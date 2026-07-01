package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.backtest.BacktestService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 策略回测工具
 *
 * 使用历史数据验证交易策略的表现
 */
@Component
public class RunBacktestTool implements Tool {

    private final BacktestService backtestService;

    public RunBacktestTool(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @Override
    public String name() {
        return "run_backtest";
    }

    @Override
    public String description() {
        return "使用历史数据对指定交易策略进行回测，返回收益率、最大回撤、胜率等指标";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> stockCode = new HashMap<>();
        stockCode.put("type", "string");
        stockCode.put("description", "股票代码，如 600519、000001");
        properties.put("stock_code", stockCode);

        Map<String, Object> strategy = new HashMap<>();
        strategy.put("type", "string");
        strategy.put("description", "策略名称，如 ma_golden_cross（均线金叉）");
        strategy.put("default", "ma_golden_cross");
        properties.put("strategy", strategy);

        Map<String, Object> days = new HashMap<>();
        days.put("type", "integer");
        days.put("description", "回测时间范围（天数），默认180天");
        days.put("default", 180);
        properties.put("days", days);

        params.put("properties", properties);
        params.put("required", new String[]{"stock_code"});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String code = (String) args.get("stock_code");
        if (code == null || code.isBlank()) {
            throw new ToolException("参数 stock_code 不能为空", "PARAM_MISSING");
        }

        String strategy = (String) args.getOrDefault("strategy", "ma_golden_cross");
        int days = 180;
        Object daysObj = args.get("days");
        if (daysObj instanceof Number) {
            days = ((Number) daysObj).intValue();
        }

        var result = backtestService.runBacktest(code, strategy,
                LocalDate.now().minusDays(days), LocalDate.now(), 100000);
        if (result == null) {
            return "回测失败";
        }
        return String.format("回测结果: 收益%.2f%% 最大回撤%.2f%% 胜率%.1f%%",
                result.getTotalReturnPct(), result.getMaxDrawdownPct(), result.getWinRatePct());
    }
}
