package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.service.market.MarketAnalysisService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 市场概览工具
 *
 * 获取大盘整体走势、市场信号灯等宏观数据
 */
@Component
public class GetMarketOverviewTool implements Tool {

    private final MarketAnalysisService marketAnalysisService;

    public GetMarketOverviewTool(MarketAnalysisService marketAnalysisService) {
        this.marketAnalysisService = marketAnalysisService;
    }

    @Override
    public String name() {
        return "get_market_overview";
    }

    @Override
    public String description() {
        return "获取市场整体概况，包括大盘指数走势、市场信号灯（多空状态）等宏观数据";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        params.put("properties", new HashMap<>());
        params.put("required", new String[]{});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        Map<String, Object> marketLight = marketAnalysisService.getMarketLight();
        if (marketLight == null || marketLight.isEmpty()) {
            return "暂无市场概览数据";
        }
        return marketLight.toString();
    }
}
