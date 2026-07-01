package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取交易信号工具 — 龙虎榜 + 北向资金 + 板块归属
 */
@Component
public class GetSignalTool implements Tool {

    private final MarketDataPort dataFetcher;

    public GetSignalTool(MarketDataPort dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    @Override
    public String name() {
        return "get_signal";
    }

    @Override
    public String description() {
        return "获取交易信号数据，包括龙虎榜(游资/机构动向)、北向资金(外资流向)、个股板块归属(行业/概念)、大宗交易(机构减持)、解禁日历(抛压预警)、行业排名(轮动信号)";
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

        Map<String, Object> signalType = new HashMap<>();
        signalType.put("type", "string");
        signalType.put("description", "信号类型: dragon_tiger(龙虎榜) / northbound(北向资金) / boards(板块归属) / block_trade(大宗交易) / unlock(解禁日历) / industry(行业排名)，默认dragon_tiger");
        signalType.put("default", "dragon_tiger");
        properties.put("signal_type", signalType);

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
        String signalType = (String) args.getOrDefault("signal_type", "dragon_tiger");

        switch (signalType) {
            case "dragon_tiger" -> {
                List<Map<String, Object>> data = dataFetcher.getDragonTigerList(code, 10);
                if (data == null || data.isEmpty()) return code + " 近期未上龙虎榜";
                StringBuilder sb = new StringBuilder("龙虎榜记录:\n");
                for (Map<String, Object> d : data) {
                    sb.append(String.format("[%s] %s 净买入%.0f万 买入%.0f万 卖出%.0f万 涨跌%.2f%%\n",
                            d.get("trade_date"), d.get("reason"),
                            toWan(d.get("net_buy")), toWan(d.get("buy_amount")), toWan(d.get("sell_amount")),
                            num(d.get("change_pct"))));
                }
                return sb.toString().trim();
            }
            case "northbound" -> {
                List<Map<String, Object>> data = dataFetcher.getNorthboundFlow(10);
                if (data == null || data.isEmpty()) return "无法获取北向资金数据";
                StringBuilder sb = new StringBuilder("北向资金流向(最近10天):\n");
                for (Map<String, Object> d : data) {
                    sb.append(String.format("[%s] %s 净买入%.0f万 累计%.0f万\n",
                            d.get("trade_date"), d.get("board_type"),
                            toWan(d.get("net_amount")), toWan(d.get("accumulate_amount"))));
                }
                return sb.toString().trim();
            }
            case "boards" -> {
                List<Map<String, Object>> data = dataFetcher.getStockBoardsDetail(code);
                if (data == null || data.isEmpty()) return "无法获取 " + code + " 的板块归属";
                StringBuilder sb = new StringBuilder("板块归属:\n");
                for (Map<String, Object> d : data) {
                    sb.append(String.format("- [%s] %s (代码:%s)\n", d.get("board_type"), d.get("board_name"), d.get("board_code")));
                }
                return sb.toString().trim();
            }
            case "block_trade" -> {
                List<Map<String, Object>> data = dataFetcher.getBlockTrades(code, 30);
                if (data == null || data.isEmpty()) return code + " 近期无大宗交易记录";
                StringBuilder sb = new StringBuilder("大宗交易记录:\n");
                for (Map<String, Object> d : data) {
                    sb.append(String.format("[%s] 成交价:%.2f 成交量:%.0f 折溢价率:%.2f%% 买方:%s 卖方:%s\n",
                            d.get("trade_date"), num(d.get("price")), num(d.get("volume")),
                            num(d.get("discount_rate")), d.get("buyer"), d.get("seller")));
                }
                return sb.toString().trim();
            }
            case "unlock" -> {
                List<Map<String, Object>> data = dataFetcher.getRestrictedShareUnlock(code, 90);
                if (data == null || data.isEmpty()) return code + " 近期无限售解禁记录";
                StringBuilder sb = new StringBuilder("限售解禁日历:\n");
                for (Map<String, Object> d : data) {
                    sb.append(String.format("[%s] 解禁股数:%.0f 占总股本:%.2f%%\n",
                            d.get("unlock_date"), num(d.get("share_count")), num(d.get("ratio"))));
                }
                return sb.toString().trim();
            }
            case "industry" -> {
                List<Map<String, Object>> data = dataFetcher.getIndustryRanking();
                if (data == null || data.isEmpty()) return "无法获取行业板块排名数据";
                StringBuilder sb = new StringBuilder("行业板块排名(TOP20):\n");
                int limit = Math.min(20, data.size());
                for (int i = 0; i < limit; i++) {
                    Map<String, Object> d = data.get(i);
                    sb.append(String.format("%d. %s 涨跌幅%.2f%% 上涨%d家 下跌%d家 领涨:%s\n",
                            i + 1, d.get("board_name"), num(d.get("change_pct")),
                            (int) num(d.get("rise_count")), (int) num(d.get("fall_count")), d.get("lead_stock")));
                }
                return sb.toString().trim();
            }
            default -> { return "不支持的信号类型: " + signalType + ", 可选: dragon_tiger/northbound/boards/block_trade/unlock/industry"; }
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
