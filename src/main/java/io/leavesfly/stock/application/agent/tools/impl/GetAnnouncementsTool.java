package io.leavesfly.stock.application.agent.tools.impl;

import io.leavesfly.stock.application.agent.tools.Tool;
import io.leavesfly.stock.application.agent.tools.ToolException;
import io.leavesfly.stock.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取个股公告工具 — 沪深北全量公告
 */
@Component
public class GetAnnouncementsTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetAnnouncementsTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_announcements";
    }

    @Override
    public String description() {
        return "获取个股最新公告列表，包括标题、日期、类型等，用于了解公司重大事件(重组/定增/业绩预告等)";
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

        Map<String, Object> count = new HashMap<>();
        count.put("type", "integer");
        count.put("description", "获取公告数量，默认10条");
        count.put("default", 10);
        properties.put("count", count);

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
        int count = 10;
        Object countObj = args.get("count");
        if (countObj instanceof Number) count = ((Number) countObj).intValue();

        List<Map<String, Object>> data = dataFetcher.getAnnouncements(code, count);
        if (data == null || data.isEmpty()) {
            return "无法获取 " + code + " 的公告数据";
        }

        StringBuilder sb = new StringBuilder("最近" + count + "条公告:\n");
        for (Map<String, Object> d : data) {
            sb.append(String.format("[%s] %s\n", d.get("notice_date"), d.get("title")));
        }
        return sb.toString().trim();
    }
}
