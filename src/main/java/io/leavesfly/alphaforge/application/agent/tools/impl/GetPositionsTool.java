package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.service.portfolio.PortfolioService;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioPosition;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 持仓查询工具 — 供 CHAT 工具循环响应"我现在持有什么仓"。
 *
 * <p>与 decision_score 联动：先查持仓再逐只三灯评估，形成"持仓体检"动线。</p>
 */
@Component
public class GetPositionsTool implements Tool {

    private final PortfolioService portfolioService;

    public GetPositionsTool(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Override
    public String name() {
        return "get_positions";
    }

    @Override
    public String description() {
        return "查询当前登记的全部持仓（代码/名称/数量/成本价/浮动盈亏）。"
                + "用户问\"我现在持有什么/我的持仓\"时调用；"
                + "查到持仓后可对个股调用 decision_score 做持仓视角体检（减风险纪律评估）。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        params.put("properties", new HashMap<>());
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        List<PortfolioPosition> positions = portfolioService.getAllPositions();
        if (positions.isEmpty()) {
            return "当前无登记持仓。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("当前持仓 %d 只：%n", positions.size()));
        for (PortfolioPosition p : positions) {
            sb.append(String.format("- %s %s：%d 股，成本 %.2f，现价 %s，盈亏 %s（%s%%）%n",
                    p.getStockCode(),
                    p.getStockName() != null ? p.getStockName() : "",
                    p.getQuantity() != null ? p.getQuantity() : 0,
                    p.getCostPrice() != null ? p.getCostPrice() : 0,
                    p.getCurrentPrice() != null ? String.format("%.2f", p.getCurrentPrice()) : "-",
                    p.getProfitLoss() != null ? String.format("%.0f", p.getProfitLoss()) : "-",
                    p.getProfitLossPct() != null ? String.format("%.2f", p.getProfitLossPct()) : "-"));
        }
        sb.append("（如需逐只体检可调用 decision_score，持仓成本将自动带入）");
        return sb.toString();
    }
}
