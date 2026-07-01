package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取资金流数据工具 — 主力/大单/中单/小单净流入
 */
@Component
public class GetFundFlowTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetFundFlowTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_fund_flow";
    }

    @Override
    public String description() {
        return "获取股票资金流向数据，支持日级和分钟级，包括主力、大单、中单、小单净流入金额，用于判断资金动向";
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

        Map<String, Object> days = new HashMap<>();
        days.put("type", "integer");
        days.put("description", "获取最近N天的资金流数据（日级）或N条分钟级数据，默认10");
        days.put("default", 10);
        properties.put("days", days);

        Map<String, Object> minuteLevel = new HashMap<>();
        minuteLevel.put("type", "boolean");
        minuteLevel.put("description", "是否获取分钟级资金流数据（盘中实时动向），默认false（日级）");
        minuteLevel.put("default", false);
        properties.put("minute_level", minuteLevel);

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
        int days = 10;
        Object daysObj = args.get("days");
        if (daysObj instanceof Number) days = ((Number) daysObj).intValue();
        boolean minuteLevel = Boolean.TRUE.equals(args.get("minute_level"));

        List<Map<String, Object>> data = dataFetcher.getFundFlow(code, days, minuteLevel);
        if (data == null || data.isEmpty()) {
            return "无法获取 " + code + " 的资金流数据";
        }

        String label = minuteLevel ? "分钟级资金流" : "最近" + days + "天资金流";
        StringBuilder sb = new StringBuilder(label + ":\n");
        int start = Math.max(0, data.size() - 5);
        for (int i = start; i < data.size(); i++) {
            Map<String, Object> d = data.get(i);
            sb.append(String.format("%s: 主力净流入%.0f万 大单%.0f万 中单%.0f万 小单%.0f万\n",
                    d.get("date"),
                    toWan(d.get("main_net")),
                    toWan(d.get("big_net")),
                    toWan(d.get("mid_net")),
                    toWan(d.get("small_net"))));
        }
        return sb.toString().trim();
    }

    private double toWan(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue() / 10000;
        return 0;
    }
}
