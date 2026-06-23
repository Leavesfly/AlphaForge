package io.leavesfly.stock.agent;

import io.leavesfly.stock.llm.LlmService;
import io.leavesfly.stock.service.PortfolioService;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 投资组合Agent - 分析持仓和仓位
 * 对应Python版本的 src/agent/agents/portfolio_agent.py
 */
@Component
public class PortfolioAgent implements BaseAgent {

    private final LlmService llmService;
    private final PortfolioService portfolioService;

    public PortfolioAgent(LlmService llmService, PortfolioService portfolioService) {
        this.llmService = llmService;
        this.portfolioService = portfolioService;
    }

    @Override public String getName() { return "portfolio"; }
    @Override public String getDescription() { return "投资组合Agent - 持仓分析和仓位管理建议"; }

    @Override
    public AgentOrchestrator.AgentOpinion analyze(Map<String, Object> context) {
        Map<String, Object> summary = portfolioService.getPortfolioSummary();
        Map<String, Object> risk = portfolioService.assessRisk();

        StringBuilder prompt = new StringBuilder();
        prompt.append("当前投资组合状况:\n");
        prompt.append("- 持仓数: ").append(summary.get("total_positions")).append("\n");
        prompt.append("- 总市值: ").append(summary.get("total_market_value")).append("\n");
        prompt.append("- 总盈亏: ").append(summary.get("total_profit_loss")).append("\n");
        prompt.append("- 胜率: ").append(summary.get("win_rate_pct")).append("%\n");
        prompt.append("- 集中度风险: ").append(risk.get("concentration_risk")).append("\n");
        prompt.append("\n当前分析股票: ").append(context.get("stock_code")).append("\n");
        prompt.append("请评估是否适合加入投资组合，以及建议仓位配比。");

        String response = llmService.chat(
                "你是一位投资组合管理专家。请结合组合状况给出配置建议。", prompt.toString());
        return new AgentOrchestrator.AgentOpinion("portfolio", response, "neutral", 50, 0.5);
    }
}
