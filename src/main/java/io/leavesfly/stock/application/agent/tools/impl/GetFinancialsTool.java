package io.leavesfly.stock.application.agent.tools.impl;

import io.leavesfly.stock.application.agent.tools.Tool;
import io.leavesfly.stock.application.agent.tools.ToolException;
import io.leavesfly.stock.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取财务数据工具 — 财报三表 + 关键指标
 */
@Component
public class GetFinancialsTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetFinancialsTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_financials";
    }

    @Override
    public String description() {
        return "获取股票财务数据，包括财报三表(资产负债表/利润表/现金流量表)和关键财务指标(ROE/ROA/EPS/毛利率等)";
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

        Map<String, Object> type = new HashMap<>();
        type.put("type", "string");
        type.put("description", "数据类型: indicators(关键指标) / balance(资产负债表) / income(利润表) / cashflow(现金流量表)，默认indicators");
        type.put("default", "indicators");
        properties.put("data_type", type);

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
        String dataType = (String) args.getOrDefault("data_type", "indicators");

        if ("indicators".equals(dataType)) {
            List<Map<String, Object>> data = dataFetcher.getKeyIndicators(code);
            if (data == null || data.isEmpty()) return "无法获取 " + code + " 的关键财务指标";
            StringBuilder sb = new StringBuilder("关键财务指标:\n");
            for (Map<String, Object> d : data) {
                sb.append(String.format("[%s] 营收%.0f EPS%.2f ROE%.2f%% ROA%.2f%% 毛利率%.2f%% 资产负债率%.2f%%\n",
                        d.get("report_date"), num(d.get("operate_income")), num(d.get("basic_eps")),
                        num(d.get("roe_avg")), num(d.get("roa")),
                        num(d.get("gross_profit_ratio")), num(d.get("debt_asset_ratio"))));
            }
            return sb.toString().trim();
        }

        List<Map<String, Object>> data = dataFetcher.getFinancialStatements(code, dataType);
        if (data == null || data.isEmpty()) return "无法获取 " + code + " 的" + dataType + "数据";
        StringBuilder sb = new StringBuilder("财报数据(" + dataType + "):\n");
        for (Map<String, Object> d : data) {
            sb.append(String.format("[%s] %s: %.0f (同比%.1f%%)\n",
                    d.get("report_date"), d.get("item_name"),
                    num(d.get("amount")), num(d.get("yoy_ratio"))));
        }
        return sb.toString().trim();
    }

    private double num(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0;
    }
}
