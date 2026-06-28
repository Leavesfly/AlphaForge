package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.service.screening.BacktestService;
import io.leavesfly.alphaforge.domain.model.entity.backtest.BacktestRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 多策略对比工具 — 对同一标的运行多个策略回测，横向对比表现。
 *
 * 供 LLM 在策略编排后验证选择：选定候选策略后，用历史数据对比
 * 各策略的收益率/回撤/胜率，辅助 LLM 给出最终推荐。
 */
@Component
public class CompareStrategiesTool implements Tool {

    private final BacktestService backtestService;

    public CompareStrategiesTool(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @Override
    public String name() {
        return "compare_strategies";
    }

    @Override
    public String description() {
        return "对同一股票运行多个策略回测，横向对比收益率、最大回撤、胜率等指标，辅助选择最优策略";
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

        Map<String, Object> strategies = new HashMap<>();
        strategies.put("type", "string");
        strategies.put("description", "策略名称列表，逗号分隔，如 ma_golden_cross,volume_breakout,shrink_pullback");
        properties.put("strategies", strategies);

        Map<String, Object> days = new HashMap<>();
        days.put("type", "integer");
        days.put("description", "回测时间范围（天），默认180天");
        days.put("default", 180);
        properties.put("days", days);

        params.put("properties", properties);
        params.put("required", new String[]{"stock_code", "strategies"});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String code = (String) args.get("stock_code");
        if (code == null || code.isBlank()) {
            throw new ToolException("参数 stock_code 不能为空", "PARAM_MISSING");
        }
        String strategiesStr = (String) args.get("strategies");
        if (strategiesStr == null || strategiesStr.isBlank()) {
            throw new ToolException("参数 strategies 不能为空", "PARAM_MISSING");
        }

        int days = 180;
        Object daysObj = args.get("days");
        if (daysObj instanceof Number) {
            days = ((Number) daysObj).intValue();
        }

        String[] strategyNames = strategiesStr.split(",");
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("策略对比结果（%s, %d天）：\n\n", code, days));
        sb.append(String.format("%-22s %10s %10s %10s %8s %8s\n",
                "策略", "总收益%", "最大回撤%", "年化%", "胜率%", "交易次数"));
        sb.append("-".repeat(80)).append("\n");

        String bestStrategy = null;
        double bestReturn = Double.NEGATIVE_INFINITY;

        for (String name : strategyNames) {
            String trimmed = name.trim();
            try {
                BacktestRecord record = backtestService.runBacktest(code, trimmed, start, end, 100000);
                if (record == null) {
                    sb.append(String.format("%-22s %10s\n", trimmed, "回测失败/不支持"));
                    continue;
                }
                double totalReturn = record.getTotalReturnPct() != null ? record.getTotalReturnPct() : 0;
                double maxDrawdown = record.getMaxDrawdownPct() != null ? record.getMaxDrawdownPct() : 0;
                double annualReturn = record.getAnnualReturnPct() != null ? record.getAnnualReturnPct() : 0;
                double winRate = record.getWinRatePct() != null ? record.getWinRatePct() : 0;
                int trades = record.getTotalTrades() != null ? record.getTotalTrades() : 0;

                sb.append(String.format("%-22s %10.2f %10.2f %10.2f %8.1f %8d\n",
                        trimmed, totalReturn, maxDrawdown, annualReturn, winRate, trades));

                if (totalReturn > bestReturn) {
                    bestReturn = totalReturn;
                    bestStrategy = trimmed;
                }
            } catch (Exception e) {
                sb.append(String.format("%-22s %10s\n", trimmed, "错误: " + e.getMessage()));
            }
        }

        if (bestStrategy != null) {
            sb.append(String.format("\n最优策略: %s（收益 %.2f%%）", bestStrategy, bestReturn));
        }
        return sb.toString().trim();
    }
}
