package io.leavesfly.alphaforge.infrastructure.llm;

import io.leavesfly.alphaforge.domain.model.entity.usage.LlmUsageDaily;
import io.leavesfly.alphaforge.infrastructure.persistence.usage.LlmUsageDailyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmUsageTracker LLM用量追踪器测试")
class LlmUsageTrackerTest {

    @Mock
    private LlmUsageDailyRepository usageRepository;

    @InjectMocks
    private LlmUsageTracker tracker;

    @Nested
    @DisplayName("recordUsage - 记录LLM调用")
    class RecordUsageTests {

        @Test
        @DisplayName("记录单次调用不抛异常")
        void recordSingleUsageDoesNotThrow() {
            doNothing().when(usageRepository).recordUsage(any(), anyString(), anyString(), anyInt(), anyInt(), anyDouble());
            assertDoesNotThrow(() -> tracker.recordUsage("gpt-4o", 100, 50, 500));
        }

        @Test
        @DisplayName("记录带provider的调用")
        void recordUsageWithProvider() {
            doNothing().when(usageRepository).recordUsage(any(), anyString(), anyString(), anyInt(), anyInt(), anyDouble());
            assertDoesNotThrow(() -> tracker.recordUsage("gpt-4o", "openai", 100, 50, 500));
        }

        @Test
        @DisplayName("DB持久化失败不影响内存统计")
        void dbFailureDoesNotAffectMemoryStats() {
            doThrow(new RuntimeException("DB error"))
                .when(usageRepository).recordUsage(any(), anyString(), anyString(), anyInt(), anyInt(), anyDouble());
            assertDoesNotThrow(() -> tracker.recordUsage("gpt-4o", 100, 50, 500));
            // 内存中应该有统计数据
            Map<String, Object> todayUsage = tracker.getTodayUsage();
            assertNotNull(todayUsage);
            assertEquals(1, todayUsage.get("calls"));
        }

        @Test
        @DisplayName("多次记录调用次数累加")
        void multipleRecordsAccumulate() {
            doNothing().when(usageRepository).recordUsage(any(), anyString(), anyString(), anyInt(), anyInt(), anyDouble());
            tracker.recordUsage("gpt-4o", 100, 50, 500);
            tracker.recordUsage("gpt-4o", 200, 100, 600);
            tracker.recordUsage("gpt-4o", 150, 80, 400);

            Map<String, Object> todayUsage = tracker.getTodayUsage();
            assertEquals(3, todayUsage.get("calls"));
            assertEquals(450L, todayUsage.get("prompt_tokens"));
            assertEquals(230L, todayUsage.get("completion_tokens"));
            assertEquals(680L, todayUsage.get("total_tokens"));
        }
    }

    @Nested
    @DisplayName("getTodayUsage - 获取今日用量")
    class GetTodayUsageTests {

        @Test
        @DisplayName("内存有数据时优先返回内存数据")
        void memoryDataPreferred() {
            doNothing().when(usageRepository).recordUsage(any(), anyString(), anyString(), anyInt(), anyInt(), anyDouble());
            tracker.recordUsage("gpt-4o", 100, 50, 500);

            Map<String, Object> todayUsage = tracker.getTodayUsage();
            assertNotNull(todayUsage);
            assertEquals(LocalDate.now().toString(), todayUsage.get("date"));
            assertEquals(1, todayUsage.get("calls"));
            assertEquals(100L, todayUsage.get("prompt_tokens"));
            assertEquals(50L, todayUsage.get("completion_tokens"));
            assertEquals(150L, todayUsage.get("total_tokens"));
            assertEquals(500L, todayUsage.get("avg_duration_ms"));
        }

        @Test
        @DisplayName("内存为空时回退DB数据")
        void fallbackToDbWhenMemoryEmpty() {
            LlmUsageDaily dbRecord = new LlmUsageDaily();
            dbRecord.setUsageDate(LocalDate.now());
            dbRecord.setModel("gpt-4o");
            dbRecord.setRequestCount(5);
            dbRecord.setPromptTokens(1000);
            dbRecord.setCompletionTokens(500);
            dbRecord.setTotalTokens(1500);
            dbRecord.setTotalCost(0.05);

            when(usageRepository.findByDateRange(eq(LocalDate.now()), eq(LocalDate.now())))
                .thenReturn(Collections.singletonList(dbRecord));

            Map<String, Object> todayUsage = tracker.getTodayUsage();
            assertNotNull(todayUsage);
            assertEquals(5, todayUsage.get("calls"));
            assertEquals(1000, todayUsage.get("prompt_tokens"));
            assertEquals(500, todayUsage.get("completion_tokens"));
            assertEquals(1500, todayUsage.get("total_tokens"));
        }

        @Test
        @DisplayName("DB也失败时返回零值")
        void dbFailureReturnsZeros() {
            when(usageRepository.findByDateRange(any(), any()))
                .thenThrow(new RuntimeException("DB error"));

            Map<String, Object> todayUsage = tracker.getTodayUsage();
            assertNotNull(todayUsage);
            assertEquals(0, todayUsage.get("calls"));
            assertEquals(0, todayUsage.get("prompt_tokens"));
            assertEquals(0, todayUsage.get("completion_tokens"));
            assertEquals(0, todayUsage.get("total_tokens"));
        }

        @Test
        @DisplayName("费用估算包含人民币符号")
        void costEstimationContainsCurrencySymbol() {
            doNothing().when(usageRepository).recordUsage(any(), anyString(), anyString(), anyInt(), anyInt(), anyDouble());
            tracker.recordUsage("gpt-4o", 1000, 500, 500);

            Map<String, Object> todayUsage = tracker.getTodayUsage();
            String cost = (String) todayUsage.get("estimated_cost");
            assertNotNull(cost);
            assertTrue(cost.startsWith("¥"));
        }
    }

    @Nested
    @DisplayName("getOverallStats - 获取总体统计")
    class GetOverallStatsTests {

        @Test
        @DisplayName("DB返回统计数据")
        void dbReturnsStats() {
            Map<String, Object> dbStats = new HashMap<>();
            dbStats.put("total_calls", 100);
            dbStats.put("total_tokens", 50000);
            dbStats.put("total_cost", 3.5);
            when(usageRepository.getTotalStats(any(), any())).thenReturn(dbStats);

            Map<String, Object> stats = tracker.getOverallStats();
            assertNotNull(stats);
            assertEquals(100, stats.get("total_calls"));
            assertEquals(50000, stats.get("total_tokens"));
            assertEquals(3.5, stats.get("total_cost"));
            assertNotNull(stats.get("today"));
        }

        @Test
        @DisplayName("DB失败回退内存统计")
        void dbFailureFallbackToMemory() {
            doNothing().when(usageRepository).recordUsage(any(), anyString(), anyString(), anyInt(), anyInt(), anyDouble());
            tracker.recordUsage("gpt-4o", 100, 50, 500);

            when(usageRepository.getTotalStats(any(), any()))
                .thenThrow(new RuntimeException("DB error"));

            Map<String, Object> stats = tracker.getOverallStats();
            assertNotNull(stats);
            // 应回退到内存中的数据
            assertNotNull(stats.get("total_calls"));
        }

        @Test
        @DisplayName("包含今日用量和模型列表")
        void containsTodayUsageAndModels() {
            when(usageRepository.getTotalStats(any(), any())).thenReturn(Collections.emptyMap());
            doNothing().when(usageRepository).recordUsage(any(), anyString(), anyString(), anyInt(), anyInt(), anyDouble());

            tracker.recordUsage("gpt-4o", 100, 50, 500);
            tracker.recordUsage("claude-3", 80, 40, 400);

            Map<String, Object> stats = tracker.getOverallStats();
            assertNotNull(stats);
            assertNotNull(stats.get("today"));
            assertNotNull(stats.get("models_used"));
        }
    }

    @Nested
    @DisplayName("getMonthlyStats - 获取月度统计")
    class GetMonthlyStatsTests {

        @Test
        @DisplayName("DB返回月度数据")
        void dbReturnsMonthlyStats() {
            Map<String, Object> dbStats = new HashMap<>();
            dbStats.put("total_calls", 300);
            dbStats.put("total_tokens", 150000);
            dbStats.put("total_cost", 10.5);
            when(usageRepository.getTotalStats(any(), any())).thenReturn(dbStats);

            Map<String, Object> monthly = tracker.getMonthlyStats();
            assertNotNull(monthly);
            assertEquals(300, monthly.get("calls"));
            assertEquals(150000, monthly.get("total_tokens"));
            assertEquals(10.5, monthly.get("total_cost"));
        }

        @Test
        @DisplayName("DB失败返回零值")
        void dbFailureReturnsZeros() {
            when(usageRepository.getTotalStats(any(), any()))
                .thenThrow(new RuntimeException("DB error"));

            Map<String, Object> monthly = tracker.getMonthlyStats();
            assertNotNull(monthly);
            assertEquals(0, monthly.get("calls"));
            assertEquals(0, monthly.get("total_tokens"));
            assertEquals(0.0, monthly.get("total_cost"));
        }
    }

    @Nested
    @DisplayName("getDailyDetail - 获取每日明细")
    class GetDailyDetailTests {

        @Test
        @DisplayName("DB返回每日明细")
        void dbReturnsDailyDetail() {
            LlmUsageDaily record = new LlmUsageDaily();
            record.setUsageDate(LocalDate.now());
            record.setModel("gpt-4o");
            record.setProvider("openai");
            record.setRequestCount(10);
            record.setPromptTokens(2000);
            record.setCompletionTokens(1000);
            record.setTotalTokens(3000);
            record.setTotalCost(0.15);

            when(usageRepository.findByDateRange(any(), any()))
                .thenReturn(Collections.singletonList(record));

            List<Map<String, Object>> detail = tracker.getDailyDetail(7);
            assertNotNull(detail);
            assertEquals(1, detail.size());
            Map<String, Object> row = detail.get(0);
            assertEquals("gpt-4o", row.get("model"));
            assertEquals(10, row.get("calls"));
            assertEquals(3000, row.get("total_tokens"));
        }

        @Test
        @DisplayName("DB失败返回空列表")
        void dbFailureReturnsEmptyList() {
            when(usageRepository.findByDateRange(any(), any()))
                .thenThrow(new RuntimeException("DB error"));

            List<Map<String, Object>> detail = tracker.getDailyDetail(7);
            assertNotNull(detail);
            assertTrue(detail.isEmpty());
        }
    }

    @Nested
    @DisplayName("getModelBreakdown - 获取模型用量分布")
    class GetModelBreakdownTests {

        @Test
        @DisplayName("DB有数据时返回DB分布")
        void dbHasDataReturnsDistribution() {
            Map<String, Object> row = new HashMap<>();
            row.put("model", "gpt-4o");
            row.put("calls", 50);
            row.put("total_tokens", 10000);
            row.put("cost", 0.5);

            when(usageRepository.aggregateByModel(any(), any()))
                .thenReturn(Collections.singletonList(row));

            Map<String, Object> breakdown = tracker.getModelBreakdown();
            assertNotNull(breakdown);
            assertTrue(breakdown.containsKey("gpt-4o"));
        }

        @Test
        @DisplayName("DB为空时回退内存统计")
        void dbEmptyFallbackToMemory() {
            doNothing().when(usageRepository).recordUsage(any(), anyString(), anyString(), anyInt(), anyInt(), anyDouble());
            when(usageRepository.aggregateByModel(any(), any()))
                .thenReturn(Collections.emptyList());

            tracker.recordUsage("gpt-4o", 100, 50, 500);

            Map<String, Object> breakdown = tracker.getModelBreakdown();
            assertNotNull(breakdown);
            assertTrue(breakdown.containsKey("gpt-4o"));
        }
    }
}
