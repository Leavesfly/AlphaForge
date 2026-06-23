package io.leavesfly.stock.agent;

import java.util.Map;

/**
 * Agent基础接口
 * 所有专业分析Agent都需要实现此接口
 */
public interface BaseAgent {

    /**
     * 获取Agent名称
     */
    String getName();

    /**
     * 获取Agent描述
     */
    String getDescription();

    /**
     * 执行分析
     *
     * @param context 分析上下文(包含股票数据、技术指标、新闻等)
     * @return Agent意见
     */
    AgentOrchestrator.AgentOpinion analyze(Map<String, Object> context);
}
