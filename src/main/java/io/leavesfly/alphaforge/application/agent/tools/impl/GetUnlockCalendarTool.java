package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取限售解禁日历工具
 * 返回解禁日期/解禁股数/解禁比例，用于中长线抛压预警
 */
@Component
public class GetUnlockCalendarTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetUnlockCalendarTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_unlock_calendar";
    }

    @Override
    public String description() {
        return "获取限售解禁日历，包括解禁日期、解禁股数、解禁比例，用于判断未来抛压风险";
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

        Map<String, Object> days = new HashMap<>();
        days.put("type", "integer");
        days.put("description", "获取最近N天的解禁记录，默认90天");
        days.put("default", 90);
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
        int days = 90;
        Object daysObj = args.get("days");
        if (daysObj instanceof Number) days = ((Number) daysObj).intValue();

        List<Map<String, Object>> data = dataFetcher.getRestrictedShareUnlock(code, days);
        if (data == null || data.isEmpty()) {
            return code + " 近期无限售解禁记录";
        }

        StringBuilder sb = new StringBuilder("限售解禁日历:\n");
        for (Map<String, Object> d : data) {
            sb.append(String.format("[%s] 解禁股数:%.0f 占总股本:%.2f%%\n",
                    d.get("unlock_date"), num(d.get("share_count")), num(d.get("ratio"))));
        }
        return sb.toString().trim();
    }

    private double num(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0;
    }
}
