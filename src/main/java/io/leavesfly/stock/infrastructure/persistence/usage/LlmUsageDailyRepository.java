package io.leavesfly.stock.infrastructure.persistence.usage;

import io.leavesfly.stock.domain.model.entity.usage.LlmUsageDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * LLM每日用量统计数据访问层
 */
@Mapper
public interface LlmUsageDailyRepository {

    void insert(LlmUsageDaily usage);

    void update(LlmUsageDaily usage);

    LlmUsageDaily findByDateAndModel(@Param("usageDate") LocalDate usageDate, @Param("model") String model);

    List<LlmUsageDaily> findByDateRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    List<LlmUsageDaily> findByModel(@Param("model") String model, @Param("limit") int limit);

    /** 按日期聚合 */
    List<Map<String, Object>> aggregateByDate(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** 按模型聚合 */
    List<Map<String, Object>> aggregateByModel(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** 总量统计 */
    Map<String, Object> getTotalStats(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** 增量更新(upsert逻辑) */
    default void recordUsage(LocalDate date, String model, String provider, int promptTokens, int completionTokens, double cost) {
        LlmUsageDaily existing = findByDateAndModel(date, model);
        if (existing == null) {
            LlmUsageDaily usage = new LlmUsageDaily();
            usage.setUsageDate(date);
            usage.setModel(model);
            usage.setProvider(provider);
            usage.setRequestCount(1);
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(promptTokens + completionTokens);
            usage.setTotalCost(cost);
            insert(usage);
        } else {
            existing.setRequestCount(existing.getRequestCount() + 1);
            existing.setPromptTokens(existing.getPromptTokens() + promptTokens);
            existing.setCompletionTokens(existing.getCompletionTokens() + completionTokens);
            existing.setTotalTokens(existing.getTotalTokens() + promptTokens + completionTokens);
            existing.setTotalCost(existing.getTotalCost() + cost);
            update(existing);
        }
    }
}
