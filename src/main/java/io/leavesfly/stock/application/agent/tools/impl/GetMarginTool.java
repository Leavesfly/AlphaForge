package io.leavesfly.stock.application.agent.tools.impl;

import io.leavesfly.stock.application.agent.tools.Tool;
import io.leavesfly.stock.application.agent.tools.ToolException;
import io.leavesfly.stock.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取杠杆与筹码数据工具 — 融资融券 + 股东户数 + 分红送转
 */
@Component
public class GetMarginTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetMarginTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_margin";
    }

    @Override
    public String description() {
        return "获取杠杆与筹码数据，包括融资融券明细、股东户数变化(筹码集中度)、分红送转历史";
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

        Map<String, Object> marginType = new HashMap<>();
        marginType.put("type", "string");
        marginType.put("description", "数据类型: margin(融资融券) / shareholder(股东户数) / dividend(分红送转)，默认margin");
        marginType.put("default", "margin");
        properties.put("margin_type", marginType);

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
        String marginType = (String) args.getOrDefault("margin_type", "margin");

        switch (marginType) {
            case "margin" -> {
                List<Map<String, Object>> data = dataFetcher.getMarginTrading(code, 10);
                if (data == null || data.isEmpty()) return "无法获取 " + code + " 的融资融券数据";
                StringBuilder sb = new StringBuilder("融资融券(最近10天):\n");
                for (Map<String, Object> d : data) {
                    sb.append(String.format("[%s] 融资余额%.0f万 融券余额%.0f万 融资融券余额%.0f万\n",
                            d.get("date"), toWan(d.get("rzye")), toWan(d.get("rqye")), toWan(d.get("rzrqye"))));
                }
                return sb.toString().trim();
            }
            case "shareholder" -> {
                List<Map<String, Object>> data = dataFetcher.getShareholderCount(code);
                if (data == null || data.isEmpty()) return "无法获取 " + code + " 的股东户数数据";
                StringBuilder sb = new StringBuilder("股东户数变化:\n");
                for (Map<String, Object> d : data) {
                    sb.append(String.format("[%s] 股东%d户 环比%.2f%% 户均持股%.0f\n",
                            d.get("end_date"), num(d.get("holder_num")),
                            num(d.get("holder_num_change")), num(d.get("avg_hold_amount"))));
                }
                return sb.toString().trim();
            }
            case "dividend" -> {
                List<Map<String, Object>> data = dataFetcher.getDividendHistory(code);
                if (data == null || data.isEmpty()) return "无法获取 " + code + " 的分红送转历史";
                StringBuilder sb = new StringBuilder("分红送转历史:\n");
                for (Map<String, Object> d : data) {
                    sb.append(String.format("[%s] 每股派息%.2f 送股%.2f 转增%.2f %s\n",
                            d.get("report_date"), num(d.get("dps")),
                            num(d.get("send_stock")), num(d.get("convert_stock")), d.get("progress")));
                }
                return sb.toString().trim();
            }
            default -> { return "不支持的数据类型: " + marginType; }
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
