package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.AdjustType;
import io.leavesfly.alphaforge.domain.model.enums.KLineFrequency;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
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
        return "获取股票历史K线数据，支持日/周/月/分钟级多频率和前复权/后复权/不复权，包括开盘价、收盘价、最高价、最低价、涨跌幅等";
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

        Map<String, Object> frequency = new HashMap<>();
        frequency.put("type", "string");
        frequency.put("description", "K线频率: daily(日线，默认) / weekly(周线) / monthly(月线) / 1min(1分钟) / 5min(5分钟) / 15min(15分钟) / 30min(30分钟) / 60min(60分钟)");
        frequency.put("default", "daily");
        properties.put("frequency", frequency);

        Map<String, Object> adjust = new HashMap<>();
        adjust.put("type", "string");
        adjust.put("description", "复权类型: front(前复权，默认) / none(不复权) / back(后复权)");
        adjust.put("default", "front");
        properties.put("adjust", adjust);

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

        List<StockDailyData> data;
        String freqStr = (String) args.getOrDefault("frequency", "daily");
        String adjustStr = (String) args.getOrDefault("adjust", "front");
        KLineFrequency frequency = parseFrequency(freqStr);
        AdjustType adjustType = parseAdjustType(adjustStr);

        data = dataFetcher.getHistoryData(code,
                LocalDate.now().minusDays(days), LocalDate.now(), frequency, adjustType);
        if (data == null || data.isEmpty()) {
            return "无法获取 " + code + " 的历史数据";
        }

        StringBuilder sb = new StringBuilder("最近" + days + "天行情(" + frequency.getDescription() + "," + adjustType.getDescription() + "):\n");
        int start = Math.max(0, data.size() - 10);
        for (int i = start; i < data.size(); i++) {
            StockDailyData d = data.get(i);
            sb.append(String.format("%s: 收盘%.2f 涨跌%.2f%%\n",
                    d.getTradeDate(), d.getClosePrice(),
                    d.getChangePct() != null ? d.getChangePct() : 0));
        }
        return sb.toString().trim();
    }

    private KLineFrequency parseFrequency(String freqStr) {
        if (freqStr == null) return KLineFrequency.DAILY;
        return switch (freqStr.toLowerCase()) {
            case "weekly", "1wk" -> KLineFrequency.WEEKLY;
            case "monthly", "1mo" -> KLineFrequency.MONTHLY;
            case "1min", "minute1" -> KLineFrequency.MINUTE_1;
            case "5min", "minute5" -> KLineFrequency.MINUTE_5;
            case "15min", "minute15" -> KLineFrequency.MINUTE_15;
            case "30min", "minute30" -> KLineFrequency.MINUTE_30;
            case "60min", "1h" -> KLineFrequency.MINUTE_60;
            default -> KLineFrequency.DAILY;
        };
    }

    private AdjustType parseAdjustType(String adjustStr) {
        if (adjustStr == null) return AdjustType.FRONT;
        return switch (adjustStr.toLowerCase()) {
            case "none", "raw" -> AdjustType.NONE;
            case "back", "qfq" -> AdjustType.BACK;
            default -> AdjustType.FRONT;
        };
    }
}
