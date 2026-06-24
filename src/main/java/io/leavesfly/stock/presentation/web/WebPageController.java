package io.leavesfly.stock.presentation.web;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.service.TradingCalendar;
import io.leavesfly.stock.application.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

/**
 * WebUI页面控制器
 * 
 * 对应Python版本的 apps/dsa-web/ React前端
 * 使用Thymeleaf服务端渲染实现相同功能
 */
@Controller
@RequestMapping("/web")
public class WebPageController {

    private final AppConfig config;
    private final AnalysisHistoryService historyService;
    private final MarketAnalysisService marketService;
    private final PortfolioService portfolioService;
    private final AlertService alertService;
    private final TradingCalendar tradingCalendar;
    private final DailyMarketContextService dailyMarketContext;

    public WebPageController(AppConfig config, AnalysisHistoryService historyService,
                            MarketAnalysisService marketService, PortfolioService portfolioService,
                            AlertService alertService, TradingCalendar tradingCalendar,
                            DailyMarketContextService dailyMarketContext) {
        this.config = config;
        this.historyService = historyService;
        this.marketService = marketService;
        this.portfolioService = portfolioService;
        this.alertService = alertService;
        this.tradingCalendar = tradingCalendar;
        this.dailyMarketContext = dailyMarketContext;
    }

    /** 首页/仪表盘 */
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "仪表盘");
        model.addAttribute("marketContext", dailyMarketContext.getDailyContext());
        model.addAttribute("recentReports", historyService.getRecentReports(null, 10));
        model.addAttribute("portfolioSummary", portfolioService.getPortfolioSummary());
        model.addAttribute("activeAlerts", alertService.getActiveAlerts().size());
        model.addAttribute("isTradingTime", tradingCalendar.isTradingTime());
        return "dashboard";
    }

    /** 股票分析页 */
    @GetMapping("/analysis")
    public String analysis(@RequestParam(required = false) String code, Model model) {
        model.addAttribute("pageTitle", "股票分析");
        model.addAttribute("stockCode", code != null ? code : "");
        if (code != null && !code.isEmpty()) {
            model.addAttribute("reports", historyService.getRecentReports(code, 5));
        }
        return "analysis";
    }

    /** AI问股对话页 */
    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("pageTitle", "AI问股");
        return "chat";
    }

    /** 智能选股页 */
    @GetMapping("/screening")
    public String screening(Model model) {
        model.addAttribute("pageTitle", "智能选股");
        return "screening";
    }

    /** 投资组合页 */
    @GetMapping("/portfolio")
    public String portfolio(Model model) {
        model.addAttribute("pageTitle", "投资组合");
        return "portfolio";
    }

    /** 决策信号页 */
    @GetMapping("/decision-signals")
    public String decisionSignals(Model model) {
        model.addAttribute("pageTitle", "决策信号");
        return "decision-signals";
    }

    /** 回测分析页 */
    @GetMapping("/backtest")
    public String backtest(Model model) {
        model.addAttribute("pageTitle", "回测分析");
        return "backtest";
    }

    /** 告警管理页 */
    @GetMapping("/alerts")
    public String alerts(Model model) {
        model.addAttribute("pageTitle", "告警管理");
        return "alerts";
    }

    /** Token使用统计页 */
    @GetMapping("/usage")
    public String usage(Model model) {
        model.addAttribute("pageTitle", "Token统计");
        return "usage";
    }

    /** 分析历史页 */
    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("pageTitle", "分析历史");
        return "history";
    }

    /** 系统设置页 */
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("pageTitle", "系统设置");
        model.addAttribute("llmModel", config.getLlmModel());
        model.addAttribute("dataProvider", config.getDataProvider());
        model.addAttribute("market", config.getMarket());
        return "settings";
    }

    /** 登录页 */
    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "登录");
        model.addAttribute("authEnabled", config.isAuthEnabled());
        return "login";
    }

    /** 登出 */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/web/login";
    }
}
