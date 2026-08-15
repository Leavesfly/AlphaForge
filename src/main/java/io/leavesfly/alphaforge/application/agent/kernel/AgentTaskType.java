package io.leavesfly.alphaforge.application.agent.kernel;

/**
 * 认知型任务类型 — 仅承载需要多步推理/动态取舍/自然语言意图的任务。
 *
 * 参数明确的查询、CRUD、精确参数回测、手动下单等确定性操作走「功能轨」，
 * 不进入 AgentTask，也不出现在此枚举。
 */
public enum AgentTaskType {

    /** 自然语言对话（ReAct 工具循环） */
    CHAT,

    /** 个股综合研判（技术面/基本面/舆情） */
    STOCK_ANALYSIS,

    /** 买点三灯评分（价/势/时 → 七态行动结论，只读） */
    DECISION_SCORE,

    /** 策略生成 */
    STRATEGY_GENERATE,

    /** 策略优化 */
    STRATEGY_OPTIMIZE,

    /** 自然语言选股 */
    NL_SCREENING,

    /** 投资组合审查 */
    PORTFOLIO_REVIEW,

    /** 自主闭环：信号是否执行的研判 */
    AUTONOMY_DECISION,

    /** 因子进化 */
    FACTOR_EVOLUTION
}
