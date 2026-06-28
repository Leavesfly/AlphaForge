package io.leavesfly.stock.domain.service.port;

import java.util.List;
import java.util.Map;

/**
 * LLM 用量追踪端口（依赖倒置）
 *
 * application 层通过此端口记录与查询大模型用量，具体实现由 infrastructure.llm 提供。
 */
public interface LlmUsagePort {

    /** 记录用量(默认提供商) */
    void recordUsage(String model, int promptTokens, int completionTokens, long durationMs);

    /** 记录用量(指定提供商) */
    void recordUsage(String model, String provider, int promptTokens, int completionTokens, long durationMs);

    /** 今日用量 */
    Map<String, Object> getTodayUsage();

    /** 总体统计 */
    Map<String, Object> getOverallStats();

    /** 按模型拆分 */
    Map<String, Object> getModelBreakdown();

    /** 月度统计 */
    Map<String, Object> getMonthlyStats();

    /** 每日明细(最近N天) */
    List<Map<String, Object>> getDailyDetail(int days);

    /** 成本趋势(最近N天) */
    List<Map<String, Object>> getCostTrend(int days);

    /** 模型分布(最近N天) */
    List<Map<String, Object>> getModelDistribution(int days);
}
