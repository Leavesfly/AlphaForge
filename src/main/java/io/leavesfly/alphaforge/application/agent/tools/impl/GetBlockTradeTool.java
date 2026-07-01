package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取大宗交易数据工具
 * 返回成交价/量/买卖方营业部/折价率，用于判断机构大宗减持信号
 */
@Component
public class GetBlockTradeTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetBlockTradeTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_block_trade";
    }

    @Override
    public String description() {
        return "获取大宗交易数据，包括成交价、成交量、买方/卖方营业部、折溢价率，用于判断机构大宗交易信号";
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
        days.put("description", "获取最近N天的大宗交易数据，默认30天");
        days.put("default", 30);
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
        int days = 30;
        Object daysObj = args.get("days");
        if (daysObj instanceof Number) days = ((Number) daysObj).intValue();

        List<Map<String, Object>> data = dataFetcher.getBlockTrades(code, days);
        if (data == null || data.isEmpty()) {
            return code + " 近期无大宗交易记录";
        }

        StringBuilder sb = new StringBuilder("大宗交易记录:\n");
        for (Map<String, Object> d : data) {
            sb.append(String.format("[%s] 成交价:%.2f 成交量:%.0f 折溢价率:%.2f%% 买方:%s 卖方:%s\n",
                    d.get("trade_date"), num(d.get("price")), num(d.get("volume")),
                    num(d.get("discount_rate")), d.get("buyer"), d.get("seller")));
        }
        return sb.toString().trim();
    }

    private double num(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0;
    }
}
