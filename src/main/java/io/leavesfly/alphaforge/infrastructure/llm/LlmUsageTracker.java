package io.leavesfly.alphaforge.infrastructure.llm;

import io.leavesfly.alphaforge.domain.service.port.LlmUsagePort;

import io.leavesfly.alphaforge.domain.model.entity.usage.LlmUsageDaily;
import io.leavesfly.alphaforge.domain.repository.usage.LlmUsageDailyRepository;
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
public class LlmUsageTracker implements LlmUsagePort {

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

    /** 模型定价表：model -> [输入$/百万token, 输出$/百万token] */
    private static final Map<String, double[]> MODEL_PRICING = Map.of(
            "gpt-4o", new double[]{2.5, 10.0},
            "gpt-4o-mini", new double[]{0.15, 0.6},
            "gpt-4-turbo", new double[]{10.0, 30.0},
            "gpt-3.5-turbo", new double[]{0.5, 1.5},
            "qwen-plus", new double[]{0.4, 1.2},
            "qwen-max", new double[]{2.0, 6.0},
            "qwen-turbo", new double[]{0.05, 0.2},
            "deepseek-chat", new double[]{0.14, 0.28},
            "deepseek-coder", new double[]{0.14, 0.28}
    );
    /** 默认定价（未知模型回退） */
    private static final double[] DEFAULT_PRICING = new double[]{2.5, 10.0};

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
            double cost = estimateCostValue(model, promptTokens, completionTokens) * 7.2; // 转为人民币
            usageRepository.recordUsage(LocalDate.now(), model, provider != null ? provider : "unknown", promptTokens, completionTokens, cost);
        } catch (Exception e) {
            log.warn("LLM用量持久化失败(不影响功能): {}", e.getMessage());
        }

        log.debug("LLM用量记录: model={}, tokens={}, duration={}ms", model, total, durationMs);
    }

    /** 获取今日用量统计（优先内存，内存为空时回退DB） */
    public Map<String, Object> getTodayUsage() {
        String today = LocalDate.now().toString();
        DailyUsage daily = dailyUsages.get(today);
        Map<String, Object> result = new LinkedHashMap<>();
        if (daily != null && daily.calls.get() > 0) {
            result.put("date", today);
            result.put("calls", daily.calls.get());
            result.put("prompt_tokens", daily.promptTokens.get());
            result.put("completion_tokens", daily.completionTokens.get());
            result.put("total_tokens", daily.promptTokens.get() + daily.completionTokens.get());
            result.put("avg_duration_ms", daily.calls.get() > 0 ? daily.totalDurationMs.get() / daily.calls.get() : 0);
            result.put("estimated_cost", estimateCost(daily.promptTokens.get(), daily.completionTokens.get()));
            return result;
        }
        // 内存为空（如重启后），从DB读取今日数据
        try {
            List<LlmUsageDaily> dbList = usageRepository.findByDateRange(LocalDate.now(), LocalDate.now());
            int calls = 0, promptTokens = 0, completionTokens = 0;
            for (LlmUsageDaily u : dbList) {
                calls += u.getRequestCount() != null ? u.getRequestCount() : 0;
                promptTokens += u.getPromptTokens() != null ? u.getPromptTokens() : 0;
                completionTokens += u.getCompletionTokens() != null ? u.getCompletionTokens() : 0;
            }
            result.put("date", today);
            result.put("calls", calls);
            result.put("prompt_tokens", promptTokens);
            result.put("completion_tokens", completionTokens);
            result.put("total_tokens", promptTokens + completionTokens);
            result.put("avg_duration_ms", 0);
            result.put("estimated_cost", estimateCost(promptTokens, completionTokens));
        } catch (Exception e) {
            log.warn("从DB读取今日用量失败: {}", e.getMessage());
            result.put("date", today);
            result.put("calls", 0);
            result.put("prompt_tokens", 0);
            result.put("completion_tokens", 0);
            result.put("total_tokens", 0);
            result.put("estimated_cost", "¥0.00");
        }
        return result;
    }

    /** 获取总体统计（从DB读取） */
    public Map<String, Object> getOverallStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            // 全部历史统计
            LocalDate fromDate = LocalDate.of(2020, 1, 1);
            LocalDate toDate = LocalDate.now();
            Map<String, Object> dbStats = usageRepository.getTotalStats(fromDate, toDate);
            stats.put("total_calls", dbStats != null ? dbStats.getOrDefault("total_calls", 0) : 0);
            stats.put("total_tokens", dbStats != null ? dbStats.getOrDefault("total_tokens", 0) : 0);
            stats.put("total_cost", dbStats != null ? dbStats.getOrDefault("total_cost", 0.0) : 0.0);
        } catch (Exception e) {
            log.warn("从DB读取总体统计失败: {}", e.getMessage());
            stats.put("total_calls", totalCalls.get());
            stats.put("total_tokens", totalTokens.get());
            stats.put("total_cost", 0.0);
        }
        stats.put("daily_records", dailyUsages.size());
        stats.put("models_used", modelUsages.keySet());
        stats.put("today", getTodayUsage());
        return stats;
    }

    /** 获取按模型的用量分布（从DB读取近30天） */
    public Map<String, Object> getModelBreakdown() {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        try {
            LocalDate fromDate = LocalDate.now().minusDays(30);
            LocalDate toDate = LocalDate.now();
            List<Map<String, Object>> dbList = usageRepository.aggregateByModel(fromDate, toDate);
            if (dbList != null) {
                for (Map<String, Object> row : dbList) {
                    String model = String.valueOf(row.getOrDefault("model", "unknown"));
                    breakdown.put(model, Map.of(
                            "calls", row.getOrDefault("calls", 0),
                            "tokens", row.getOrDefault("total_tokens", 0),
                            "cost", row.getOrDefault("cost", 0.0)
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("从DB读取模型分布失败: {}", e.getMessage());
        }
        // 若DB为空，回退内存
        if (breakdown.isEmpty()) {
            for (Map.Entry<String, ModelUsage> entry : modelUsages.entrySet()) {
                breakdown.put(entry.getKey(), Map.of(
                        "calls", entry.getValue().calls.get(),
                        "tokens", entry.getValue().totalTokens.get()
                ));
            }
        }
        return breakdown;
    }

    /** 获取本月统计（从DB读取） */
    public Map<String, Object> getMonthlyStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            LocalDate fromDate = LocalDate.now().withDayOfMonth(1);
            LocalDate toDate = LocalDate.now();
            Map<String, Object> dbStats = usageRepository.getTotalStats(fromDate, toDate);
            result.put("calls", dbStats != null ? dbStats.getOrDefault("total_calls", 0) : 0);
            result.put("total_tokens", dbStats != null ? dbStats.getOrDefault("total_tokens", 0) : 0);
            result.put("total_cost", dbStats != null ? dbStats.getOrDefault("total_cost", 0.0) : 0.0);
        } catch (Exception e) {
            log.warn("从DB读取月度统计失败: {}", e.getMessage());
            result.put("calls", 0);
            result.put("total_tokens", 0);
            result.put("total_cost", 0.0);
        }
        return result;
    }

    /** 获取每日明细（从DB读取） */
    public List<Map<String, Object>> getDailyDetail(int days) {
        try {
            LocalDate fromDate = LocalDate.now().minusDays(days - 1);
            LocalDate toDate = LocalDate.now();
            List<LlmUsageDaily> list = usageRepository.findByDateRange(fromDate, toDate);
            List<Map<String, Object>> result = new ArrayList<>();
            if (list != null) {
                for (LlmUsageDaily u : list) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", u.getUsageDate() != null ? u.getUsageDate().toString() : "-");
                    row.put("model", u.getModel() != null ? u.getModel() : "-");
                    row.put("provider", u.getProvider() != null ? u.getProvider() : "-");
                    row.put("calls", u.getRequestCount() != null ? u.getRequestCount() : 0);
                    row.put("prompt_tokens", u.getPromptTokens() != null ? u.getPromptTokens() : 0);
                    row.put("completion_tokens", u.getCompletionTokens() != null ? u.getCompletionTokens() : 0);
                    row.put("total_tokens", u.getTotalTokens() != null ? u.getTotalTokens() : 0);
                    row.put("cost", u.getTotalCost() != null ? u.getTotalCost() : 0.0);
                    result.add(row);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("从DB读取每日明细失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 获取每日费用趋势（从DB读取） */
    public List<Map<String, Object>> getCostTrend(int days) {
        try {
            LocalDate fromDate = LocalDate.now().minusDays(days - 1);
            LocalDate toDate = LocalDate.now();
            List<Map<String, Object>> list = usageRepository.aggregateByDate(fromDate, toDate);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.warn("从DB读取费用趋势失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 获取模型分布（从DB读取） */
    public List<Map<String, Object>> getModelDistribution(int days) {
        try {
            LocalDate fromDate = LocalDate.now().minusDays(days - 1);
            LocalDate toDate = LocalDate.now();
            List<Map<String, Object>> list = usageRepository.aggregateByModel(fromDate, toDate);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.warn("从DB读取模型分布失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 估算费用(根据模型定价，转换为人民币) */
    private String estimateCost(long promptTokens, long completionTokens) {
        return String.format("¥%.2f", estimateCostValue(null, promptTokens, completionTokens) * 7.2);
    }

    /** 根据模型定价估算费用（美元） */
    private double estimateCostValue(String model, long promptTokens, long completionTokens) {
        double[] pricing = model != null
                ? MODEL_PRICING.getOrDefault(model.toLowerCase(), DEFAULT_PRICING)
                : DEFAULT_PRICING;
        return (promptTokens * pricing[0] + completionTokens * pricing[1]) / 1000000.0;
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
