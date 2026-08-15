package io.leavesfly.alphaforge.application.agent.kernel;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 链式引导顾问 — 按任务类型与结果特征生成"下一步"建议。
 *
 * <p>规则示例：DECISION_SCORE→trend_entry 建议模拟盘跟踪与止损告警；
 * NL_SCREENING 建议对候选逐只三灯评估；失败结果给修复建议（数据源体检）。
 * 建议只做引导，不改变结果本身；输出经 SSE next_steps 事件与 REST next_steps 字段下发。</p>
 */
@Component
public class NextStepAdvisor {

    /** 内核通用入口：按 AgentResult 的 taskType + 结果特征生成建议 */
    public List<NextStep> advise(AgentResult result) {
        if (result == null) {
            return List.of();
        }
        if (!result.isSuccess()) {
            return List.of(new NextStep("check_datasource", "数据源体检",
                    "loop-monitor", "执行失败常见于行情源限流或无 Key，先看数据源状态"));
        }
        List<NextStep> steps = new ArrayList<>();
        switch (result.getTaskType()) {
            case DECISION_SCORE -> steps.addAll(adviseForDecision(verdictOf(result), null));
            case STRATEGY_GENERATE -> steps.add(new NextStep("optimize_strategy", "参数寻优",
                    "strategy-center", "生成的策略先做参数寻优，再回测验证"));
            case STRATEGY_OPTIMIZE -> steps.add(new NextStep("backtest_verify", "回测验证",
                    "backtest", "寻优后的参数需回测确认样本外表现"));
            case NL_SCREENING -> steps.add(new NextStep("decision_top_candidates", "逐只三灯评估",
                    "research/decision", "对扫描 Top 候选逐只做买点三灯评估，形成闭环"));
            case STOCK_ANALYSIS -> {
                if (isLowScore(result)) {
                    steps.add(new NextStep("backtest_strategy", "回测该股策略表现",
                            "backtest", "综合评分不高时，用策略回测观察该股的历史可操作性"));
                }
                steps.add(new NextStep("decision_score", "买点三灯评估",
                        "research/decision", "从『值不值得/认不认同/是不是好时机』三个维度再检验"));
            }
            default -> {
                // CHAT/PORTFOLIO_REVIEW 等：无固定建议（CHAT 的建议走 adviseForChatTools）
            }
        }
        return steps;
    }

    /** 决策场景建议（DECISION_SCORE 七态 → 动作），REST decision/score 端点与 Advisor 共用 */
    public List<NextStep> adviseForDecision(String verdict, String stockCode) {
        List<NextStep> steps = new ArrayList<>();
        if (verdict == null) {
            return steps;
        }
        switch (verdict) {
            case "trend_entry", "trend_only" -> {
                steps.add(new NextStep("paper_trading_track", "加入模拟盘跟踪",
                        "mine/paper-trading", "先在模拟盘验证入场纪律，不直接真金"));
                steps.add(new NextStep("set_stop_loss_alert", "设置止损告警",
                        "mine/alerts", "按计划止损价设告警，触发即执行，不临场犹豫"));
            }
            case "wait_pullback" -> {
                steps.add(new NextStep("add_to_watchlist", "加入自选观察",
                        "mine/watchlist", "等回踩触发条件，先入自选池盯住"));
                steps.add(new NextStep("set_pullback_alert", "设置到价提醒",
                        "mine/alerts", "回踩 MA20 附近设提醒，触发再评估"));
            }
            case "left_watch" -> steps.add(new NextStep("add_to_watchlist", "加入自选观察",
                    "mine/watchlist", "左侧观察需要跟踪右侧触发条件何时满足"));
            case "reduce_risk" -> steps.add(new NextStep("review_positions", "查看持仓",
                    "mine/paper-trading", "减仓纪律触发：核对持仓与止损参考位"));
            case "avoid" -> steps.add(new NextStep("scan_alternatives", "换个标的扫一扫",
                    "research/screening", "当前标的缺乏趋势与估值吸引力，扫描其他机会"));
            case "unrated" -> steps.add(new NextStep("check_datasource", "数据源体检",
                    "loop-monitor", "K 线不足多为数据源问题，先确认行情可用性"));
            default -> {
            }
        }
        return steps;
    }

    /** CHAT 流式场景：按本轮实际调用的工具给建议（工具循环结束后调用） */
    public List<NextStep> adviseForChatTools(List<String> calledTools) {
        if (calledTools == null || calledTools.isEmpty()) {
            return List.of();
        }
        List<NextStep> steps = new ArrayList<>();
        if (calledTools.contains("decision_score")) {
            steps.add(new NextStep("open_decision_desk", "打开决策台",
                    "research/decision", "对话中的三灯结论可在决策台查看完整交易计划与证据链"));
        }
        if (calledTools.contains("select_strategies") || calledTools.contains("generate_strategy")) {
            steps.add(new NextStep("optimize_strategy", "参数寻优",
                    "strategy-center", "生成的策略先做参数寻优，再回测验证"));
        }
        if (calledTools.contains("get_positions")) {
            steps.add(new NextStep("open_decision_desk", "逐只三灯体检",
                    "research/decision", "对持仓逐只评估，减仓纪律逐条核对"));
        }
        return steps;
    }

    private String verdictOf(AgentResult result) {
        Object decisionResult = result.data("decisionResult");
        if (decisionResult instanceof Map<?, ?> map && map.get("verdict") != null) {
            return String.valueOf(map.get("verdict"));
        }
        return null;
    }

    private boolean isLowScore(AgentResult result) {
        Object analysis = result.data("analysisResult");
        if (analysis instanceof Map<?, ?> map && map.get("totalScore") instanceof Number score) {
            return score.doubleValue() < 60;
        }
        return false;
    }
}
