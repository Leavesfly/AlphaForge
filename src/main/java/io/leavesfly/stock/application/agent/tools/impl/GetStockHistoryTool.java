package io.leavesfly.stock.application.agent.tools.impl;

import io.leavesfly.stock.application.agent.tools.Tool;
import io.leavesfly.stock.application.agent.tools.ToolException;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import io.leavesfly.stock.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取股票历史K线数据工具
 */
@Component
public class GetStockHistoryTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetStockHistoryTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_stock_history";
    }

    @Override
    public String description() {
        return "获取股票历史K线数据，包括每日开盘价、收盘价、最高价、最低价、涨跌幅等";
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
        days.put("description", "获取最近N天的数据，默认30天");
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
        if (daysObj instanceof Number) {
            days = ((Number) daysObj).intValue();
        }

        List<StockDailyData> data = dataFetcher.getHistoryData(code,
                LocalDate.now().minusDays(days), LocalDate.now());
        if (data == null || data.isEmpty()) {
            return "无法获取 " + code + " 的历史数据";
        }

        StringBuilder sb = new StringBuilder("最近" + days + "天行情:\n");
        int start = Math.max(0, data.size() - 10);
        for (int i = start; i < data.size(); i++) {
            StockDailyData d = data.get(i);
            sb.append(String.format("%s: 收盘%.2f 涨跌%.2f%%\n",
                    d.getTradeDate(), d.getClosePrice(),
                    d.getChangePct() != null ? d.getChangePct() : 0));
        }
        return sb.toString().trim();
    }
}
