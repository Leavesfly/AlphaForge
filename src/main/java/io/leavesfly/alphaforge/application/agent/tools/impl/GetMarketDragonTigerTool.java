package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取全市场龙虎榜工具
 * 返回每日全市场上榜股票 + 净买额排名，用于市场情绪温度计
 */
@Component
public class GetMarketDragonTigerTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetMarketDragonTigerTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_market_dragon_tiger";
    }

    @Override
    public String description() {
        return "获取全市场龙虎榜数据，包括当日所有上榜股票的净买额排名，用于判断市场情绪和游资动向";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> dateParam = new HashMap<>();
        dateParam.put("type", "string");
        dateParam.put("description", "交易日期(YYYY-MM-DD)，默认今天");
        properties.put("date", dateParam);

        params.put("properties", properties);
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        java.time.LocalDate date = java.time.LocalDate.now();
        Object dateObj = args.get("date");
        if (dateObj instanceof String s && !s.isBlank()) {
            date = java.time.LocalDate.parse(s);
        }

        List<Map<String, Object>> data = dataFetcher.getMarketDragonTiger(date);
        if (data == null || data.isEmpty()) {
            return date + " 无龙虎榜数据";
        }

        StringBuilder sb = new StringBuilder("全市场龙虎榜(" + date + "):\n");
        int limit = Math.min(20, data.size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> d = data.get(i);
            sb.append(String.format("%d. %s(%s) 净买入:%.0f万 涨跌:%.2f%% 原因:%s\n",
                    i + 1, d.get("stock_name"), d.get("stock_code"),
                    toWan(d.get("net_buy")), num(d.get("change_pct")), d.get("reason")));
        }
        return sb.toString().trim();
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
