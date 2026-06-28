package io.leavesfly.stock.application.agent.tools.impl;

import io.leavesfly.stock.application.agent.tools.Tool;
import io.leavesfly.stock.application.agent.tools.ToolException;
import io.leavesfly.stock.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取研报与业绩预告工具 — 个股研报 + 机构一致预期EPS
 */
@Component
public class GetResearchTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetResearchTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_research";
    }

    @Override
    public String description() {
        return "获取个股研报(评级+EPS预测)和业绩预告(预增/预减/续盈/扭亏)，用于了解机构观点和盈利预期";
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

        Map<String, Object> researchType = new HashMap<>();
        researchType.put("type", "string");
        researchType.put("description", "数据类型: report(研报列表) / forecast(业绩预告)，默认report");
        researchType.put("default", "report");
        properties.put("research_type", researchType);

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
        String researchType = (String) args.getOrDefault("research_type", "report");

        switch (researchType) {
            case "report" -> {
                List<Map<String, Object>> data = dataFetcher.getResearchReports(code, 5);
                if (data == null || data.isEmpty()) return "无法获取 " + code + " 的研报数据";
                StringBuilder sb = new StringBuilder("机构研报:\n");
                for (Map<String, Object> d : data) {
                    sb.append(String.format("[%s] %s 评级:%s 今年EPS:%.2f 明年EPS:%.2f 今年PE:%.1f\n",
                            d.get("publish_date"), d.get("org_name"), d.get("rating"),
                            num(d.get("predict_eps_this_year")), num(d.get("predict_eps_next_year")),
                            num(d.get("predict_pe_this_year"))));
                }
                return sb.toString().trim();
            }
            case "forecast" -> {
                List<Map<String, Object>> data = dataFetcher.getConsensusEPS(code);
                if (data == null || data.isEmpty()) return "无法获取 " + code + " 的业绩预告";
                StringBuilder sb = new StringBuilder("业绩预告:\n");
                for (Map<String, Object> d : data) {
                    sb.append(String.format("[%s] %s 预测净利%.0f万 变动%.1f%% EPS预测%.2f\n",
                            d.get("notice_date"), d.get("forecast_type"),
                            toWan(d.get("profit_forecast")), num(d.get("profit_change_pct")),
                            num(d.get("eps_forecast"))));
                }
                return sb.toString().trim();
            }
            default -> { return "不支持的数据类型: " + researchType; }
        }
    }

    private double num(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0;
    }

    private double toWan(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue() / 10000;
        return 0;
    }
}
