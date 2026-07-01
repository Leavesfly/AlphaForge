package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取行业板块排名工具
 * 返回东财行业涨跌幅/领涨股/上涨下跌家数，用于行业轮动策略
 */
@Component
public class GetIndustryRankingTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetIndustryRankingTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_industry_ranking";
    }

    @Override
    public String description() {
        return "获取行业板块排名，包括各行业涨跌幅、领涨股、上涨/下跌家数，用于行业轮动和横向对比";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        params.put("properties", properties);
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        List<Map<String, Object>> data = dataFetcher.getIndustryRanking();
        if (data == null || data.isEmpty()) {
            return "无法获取行业板块排名数据";
        }

        StringBuilder sb = new StringBuilder("行业板块排名(TOP20):\n");
        sb.append(String.format("%-12s %8s %8s %8s %s\n",
                "行业", "涨跌幅%", "上涨家", "下跌家", "领涨股"));
        sb.append("-".repeat(60)).append("\n");

        int limit = Math.min(20, data.size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> d = data.get(i);
            sb.append(String.format("%-12s %8.2f %8d %8d %s\n",
                    d.get("board_name"), num(d.get("change_pct")),
                    (int) num(d.get("rise_count")), (int) num(d.get("fall_count")),
                    d.get("lead_stock")));
        }
        return sb.toString().trim();
    }

    private double num(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0;
    }
}
