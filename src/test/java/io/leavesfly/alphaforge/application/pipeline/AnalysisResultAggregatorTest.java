package io.leavesfly.alphaforge.application.pipeline;

import io.leavesfly.alphaforge.application.service.report.ReportFormatterService;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.infrastructure.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisResultAggregator 分析结果汇总测试")
class AnalysisResultAggregatorTest {

    @Mock
    private NotificationService notificationService;

    private AnalysisResultAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new AnalysisResultAggregator(notificationService, new ReportFormatterService());
    }

    private AnalysisReport report(String code, String name, String signal, int score) {
        AnalysisReport r = new AnalysisReport();
        r.setStockCode(code);
        r.setStockName(name);
        r.setSignal(signal);
        r.setTotalScore(score);
        r.setFullReport("report body");
        r.setDurationSeconds(2.5);
        r.setAnalysisDate(LocalDateTime.of(2024, 6, 1, 10, 0));
        return r;
    }

    @Test
    @DisplayName("空报告列表应返回基础统计")
    void buildSummaryEmpty() {
        Map<String, Object> summary = aggregator.buildSummary(List.of());

        assertEquals(0, summary.get("total_stocks"));
        assertFalse(summary.containsKey("avg_score"));
    }

    @Test
    @DisplayName("应统计信号分布、均分与买卖信号")
    void buildSummaryWithReports() {
        List<AnalysisReport> reports = List.of(
                report("600519", "贵州茅台", "buy", 80),
                report("000858", "五粮液", "sell", 30),
                report("601318", "中国平安", "neutral", 55)
        );

        Map<String, Object> summary = aggregator.buildSummary(reports);

        assertEquals(3, summary.get("total_stocks"));
        assertEquals("55.0", summary.get("avg_score"));
        @SuppressWarnings("unchecked")
        List<String> buySignals = (List<String>) summary.get("buy_signals");
        assertEquals(1, buySignals.size());
        assertTrue(buySignals.get(0).contains("贵州茅台"));
        @SuppressWarnings("unchecked")
        List<String> sellSignals = (List<String>) summary.get("sell_signals");
        assertEquals(1, sellSignals.size());
        assertEquals("100%", summary.get("success_rate"));
    }

    @Test
    @DisplayName("dryRun 模式不应发送通知")
    void aggregateDryRunSkipsNotification() {
        List<AnalysisReport> reports = List.of(report("600519", "贵州茅台", "buy", 80));

        Map<String, Object> summary = aggregator.aggregateAndNotify(reports, true);

        verify(notificationService, never()).sendAnalysisReports(any());
        assertFalse(summary.containsKey("notification_sent"));
    }

    @Test
    @DisplayName("非 dryRun 应推送通知并标记成功")
    void aggregateSendsNotification() {
        List<AnalysisReport> reports = List.of(report("600519", "贵州茅台", "buy", 80));

        Map<String, Object> summary = aggregator.aggregateAndNotify(reports, false);

        verify(notificationService).sendAnalysisReports(reports);
        assertEquals(true, summary.get("notification_sent"));
    }

    @Test
    @DisplayName("通知失败应记录错误而不抛异常")
    void aggregateHandlesNotificationFailure() {
        List<AnalysisReport> reports = List.of(report("600519", "贵州茅台", "buy", 80));
        doThrow(new RuntimeException("webhook down"))
                .when(notificationService).sendAnalysisReports(reports);

        Map<String, Object> summary = aggregator.aggregateAndNotify(reports, false);

        assertEquals(false, summary.get("notification_sent"));
        assertEquals("webhook down", summary.get("notification_error"));
    }

    @Test
    @DisplayName("强信号应筛选高分或低分股票")
    void getStrongSignals() {
        List<AnalysisReport> reports = List.of(
                report("600519", "贵州茅台", "buy", 85),
                report("000858", "五粮液", "neutral", 50),
                report("601318", "中国平安", "sell", 15)
        );

        List<AnalysisReport> strong = aggregator.getStrongSignals(reports);

        assertEquals(2, strong.size());
        assertTrue(strong.stream().anyMatch(r -> "600519".equals(r.getStockCode())));
        assertTrue(strong.stream().anyMatch(r -> "601318".equals(r.getStockCode())));
    }

    @Test
    @DisplayName("生成文本报告应委托格式化服务")
    void generateTextReport() {
        List<AnalysisReport> reports = List.of(report("600519", "贵州茅台", "buy", 80));

        String text = aggregator.generateTextReport(reports);

        assertNotNull(text);
        assertTrue(text.contains("贵州茅台"));
        assertEquals("无分析结果", aggregator.generateTextReport(List.of()));
    }
}
