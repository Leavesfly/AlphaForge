package io.leavesfly.stock.application.agent.tools.impl;

import io.leavesfly.stock.application.agent.tools.Tool;
import io.leavesfly.stock.application.agent.tools.ToolException;
import io.leavesfly.stock.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 获取股票实时行情工具
 */
@Component
public class GetStockPriceTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetStockPriceTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_stock_price";
    }

    @Override
    public String description() {
        return "获取股票实时行情数据，包括当前价格、涨跌幅、成交量等";
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

        Map<String, Object> quote = dataFetcher.getRealtimeQuote(code);
        if (quote == null || quote.isEmpty()) {
            return "无法获取 " + code + " 的实时行情数据";
        }
        return quote.toString();
    }
}
