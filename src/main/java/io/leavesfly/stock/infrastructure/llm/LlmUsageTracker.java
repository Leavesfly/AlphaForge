package io.leavesfly.stock.infrastructure.llm;

import io.leavesfly.stock.infrastructure.persistence.LlmUsageDailyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM Token使用量追踪
 * 内存快速统计 + 异步持久化到 llm_usage_daily 表
 */
@Component
public class LlmUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageTracker.class);

    private final LlmUsageDailyRepository usageRepository;

    /** 日维度内存统计 */
    private final Map<String, DailyUsage> dailyUsages = new ConcurrentHashMap<>();
    /** 模型维度内存统计 */
    private final Map<String, ModelUsage> modelUsages = new ConcurrentHashMap<>();
    /** 总调用次数 */
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    /** 总Token数 */
    private final AtomicLong totalTokens = new AtomicLong(0);

    public LlmUsageTracker(LlmUsageDailyRepository usageRepository) {
        this.usageRepository = usageRepository;
    }

    /**
     * 记录一次LLM调用
     */
    public void recordUsage(String model, int promptTokens, int completionTokens, long durationMs) {
        recordUsage(model, null, promptTokens, completionTokens, durationMs);
    }

    /**
     * 记录一次LLM调用(含provider)
     */
    public void recordUsage(String model, String provider, int promptTokens, int completionTokens, long durationMs) {
        int total = promptTokens + completionTokens;
        totalCalls.incrementAndGet();
        totalTokens.addAndGet(total);

        // 内存统计
        String today = LocalDate.now().toString();
        DailyUsage daily = dailyUsages.computeIfAbsent(today, k -> new DailyUsage());
        daily.calls.incrementAndGet();
        daily.promptTokens.addAndGet(promptTokens);
        daily.completionTokens.addAndGet(completionTokens);
        daily.totalDurationMs.addAndGet(durationMs);

        ModelUsage modelUsage = modelUsages.computeIfAbsent(model, k -> new ModelUsage());
        modelUsage.calls.incrementAndGet();
        modelUsage.totalTokens.addAndGet(total);

        // 持久化到DB
        try {
            double cost = estimateCostValue(promptTokens, completionTokens);
            usageRepository.recordUsage(LocalDate.now(), model, provider != null ? provider : "unknown", promptTokens, completionTokens, cost);
        } catch (Exception e) {
            log.warn("LLM用量持久化失败(不影响功能): {}", e.getMessage());
        }

        log.debug("LLM用量记录: model={}, tokens={}, duration={}ms", model, total, durationMs);
    }

    /** 获取今日用量统计 */
    public Map<String, Object> getTodayUsage() {
        String today = LocalDate.now().toString();
        DailyUsage daily = dailyUsages.get(today);
        Map<String, Object> result = new LinkedHashMap<>();
        if (daily == null) {
            result.put("date", today);
            result.put("calls", 0);
            result.put("prompt_tokens", 0);
            result.put("completion_tokens", 0);
            result.put("total_tokens", 0);
            result.put("estimated_cost", "$0.00");
            return result;
        }
        result.put("date", today);
        result.put("calls", daily.calls.get());
        result.put("prompt_tokens", daily.promptTokens.get());
        result.put("completion_tokens", daily.completionTokens.get());
        result.put("total_tokens", daily.promptTokens.get() + daily.completionTokens.get());
        result.put("avg_duration_ms", daily.calls.get() > 0 ? daily.totalDurationMs.get() / daily.calls.get() : 0);
        result.put("estimated_cost", estimateCost(daily.promptTokens.get(), daily.completionTokens.get()));
        return result;
    }

    /** 获取总体统计 */
    public Map<String, Object> getOverallStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_calls", totalCalls.get());
        stats.put("total_tokens", totalTokens.get());
        stats.put("daily_records", dailyUsages.size());
        stats.put("models_used", modelUsages.keySet());
        stats.put("today", getTodayUsage());
        return stats;
    }

    /** 获取按模型的用量分布 */
    public Map<String, Object> getModelBreakdown() {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        for (Map.Entry<String, ModelUsage> entry : modelUsages.entrySet()) {
            breakdown.put(entry.getKey(), Map.of(
                    "calls", entry.getValue().calls.get(),
                    "tokens", entry.getValue().totalTokens.get()
            ));
        }
        return breakdown;
    }

    /** 估算费用(基于OpenAI GPT-4o定价) */
    private String estimateCost(long promptTokens, long completionTokens) {
        return String.format("$%.4f", estimateCostValue(promptTokens, completionTokens));
    }

    private double estimateCostValue(long promptTokens, long completionTokens) {
        // GPT-4o: $2.5/1M input, $10/1M output
        return (promptTokens * 2.5 + completionTokens * 10.0) / 1000000.0;
    }

    private static class DailyUsage {
        final AtomicInteger calls = new AtomicInteger(0);
        final AtomicLong promptTokens = new AtomicLong(0);
        final AtomicLong completionTokens = new AtomicLong(0);
        final AtomicLong totalDurationMs = new AtomicLong(0);
    }

    private static class ModelUsage {
        final AtomicInteger calls = new AtomicInteger(0);
        final AtomicLong totalTokens = new AtomicLong(0);
    }
}
