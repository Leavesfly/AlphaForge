package io.leavesfly.stock.application.service.report;

import io.leavesfly.stock.domain.model.entity.analysis.AnalysisReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportFormatterService 报告格式化服务测试")
class ReportFormatterServiceTest {

    private ReportFormatterService service;

    @BeforeEach
    void setUp() {
        service = new ReportFormatterService();
    }

    /** 创建测试报告 */
    private AnalysisReport createTestReport() {
        AnalysisReport report = new AnalysisReport();
        report.setStockCode("600519");
        report.setStockName("贵州茅台");
        report.setAnalysisDate(LocalDateTime.of(2024, 1, 15, 10, 30));
        report.setSignal("buy");
        report.setTotalScore(75);
        report.setCurrentPrice(1800.50);
        report.setChangePct(2.35);
        report.setSummary("基本面强劲，技术面偏多");
        report.setFullReport("## 详细分析\n\n这是一份详细的分析报告。");
        report.setLlmModel("gpt-4o");
        report.setDurationSeconds(3.5);
        return report;
    }

    @Nested
    @DisplayName("formatMarkdown - Markdown格式")
    class FormatMarkdownTests {

        @Test
        @DisplayName("包含标题和股票信息")
        void containsTitleAndStockInfo() {
            AnalysisReport report = createTestReport();
            String md = service.formatMarkdown(report);
            assertNotNull(md);
            assertTrue(md.contains("贵州茅台"));
            assertTrue(md.contains("600519"));
        }

        @Test
        @DisplayName("包含交易信号")
        void containsSignal() {
            AnalysisReport report = createTestReport();
            String md = service.formatMarkdown(report);
            assertTrue(md.contains("买入"));
        }

        @Test
        @DisplayName("包含评分")
        void containsScore() {
            AnalysisReport report = createTestReport();
            String md = service.formatMarkdown(report);
            assertTrue(md.contains("75"));
            assertTrue(md.contains("/100"));
        }

        @Test
        @DisplayName("包含价格和涨跌幅")
        void containsPriceAndChangePct() {
            AnalysisReport report = createTestReport();
            String md = service.formatMarkdown(report);
            assertTrue(md.contains("1800.50"));
            assertTrue(md.contains("+2.35%"));
        }

        @Test
        @DisplayName("包含详细分析")
        void containsFullReport() {
            AnalysisReport report = createTestReport();
            String md = service.formatMarkdown(report);
            assertTrue(md.contains("详细分析"));
            assertTrue(md.contains("这是一份详细的分析报告"));
        }

        @Test
        @DisplayName("包含模型和耗时信息")
        void containsModelAndDuration() {
            AnalysisReport report = createTestReport();
            String md = service.formatMarkdown(report);
            assertTrue(md.contains("gpt-4o"));
            assertTrue(md.contains("3.5秒"));
        }

        @Test
        @DisplayName("null信号不显示交易信号行")
        void nullSignalOmitsSignalLine() {
            AnalysisReport report = createTestReport();
            report.setSignal(null);
            String md = service.formatMarkdown(report);
            assertNotNull(md);
            assertFalse(md.contains("交易信号"));
        }
    }

    @Nested
    @DisplayName("formatBrief - 简短格式")
    class FormatBriefTests {

        @Test
        @DisplayName("包含股票名称和代码")
        void containsNameAndCode() {
            AnalysisReport report = createTestReport();
            String brief = service.formatBrief(report);
            assertNotNull(brief);
            assertTrue(brief.contains("贵州茅台"));
            assertTrue(brief.contains("600519"));
        }

        @Test
        @DisplayName("包含价格和涨跌幅")
        void containsPriceInfo() {
            AnalysisReport report = createTestReport();
            String brief = service.formatBrief(report);
            assertTrue(brief.contains("1800.50"));
            assertTrue(brief.contains("+2.35%"));
        }

        @Test
        @DisplayName("包含信号和评分")
        void containsSignalAndScore() {
            AnalysisReport report = createTestReport();
            String brief = service.formatBrief(report);
            assertTrue(brief.contains("买入"));
            assertTrue(brief.contains("75"));
        }

        @Test
        @DisplayName("包含摘要")
        void containsSummary() {
            AnalysisReport report = createTestReport();
            String brief = service.formatBrief(report);
            assertTrue(brief.contains("基本面强劲"));
        }
    }

    @Nested
    @DisplayName("formatWecom - 企业微信格式")
    class FormatWecomTests {

        @Test
        @DisplayName("包含股票信息")
        void containsStockInfo() {
            AnalysisReport report = createTestReport();
            String wecom = service.formatWecom(report);
            assertNotNull(wecom);
            assertTrue(wecom.contains("贵州茅台"));
            assertTrue(wecom.contains("600519"));
        }

        @Test
        @DisplayName("涨跌幅正数使用warning颜色")
        void positiveChangeUsesWarningColor() {
            AnalysisReport report = createTestReport();
            report.setChangePct(3.5);
            String wecom = service.formatWecom(report);
            assertTrue(wecom.contains("warning"));
        }

        @Test
        @DisplayName("涨跌幅负数使用comment颜色")
        void negativeChangeUsesCommentColor() {
            AnalysisReport report = createTestReport();
            report.setChangePct(-2.5);
            String wecom = service.formatWecom(report);
            assertTrue(wecom.contains("comment"));
        }

        @Test
        @DisplayName("包含信号信息")
        void containsSignalInfo() {
            AnalysisReport report = createTestReport();
            String wecom = service.formatWecom(report);
            assertTrue(wecom.contains("买入"));
        }
    }

    @Nested
    @DisplayName("formatBatchMarkdown - 批量Markdown")
    class FormatBatchMarkdownTests {

        @Test
        @DisplayName("批量格式化多个报告")
        void formatMultipleReports() {
            AnalysisReport r1 = createTestReport();
            AnalysisReport r2 = createTestReport();
            r2.setStockCode("000001");
            r2.setStockName("平安银行");
            String batch = service.formatBatchMarkdown(Arrays.asList(r1, r2));
            assertNotNull(batch);
            assertTrue(batch.contains("贵州茅台"));
            assertTrue(batch.contains("平安银行"));
            assertTrue(batch.contains("2只"));
        }

        @Test
        @DisplayName("空列表也能格式化")
        void emptyList() {
            List<AnalysisReport> empty = List.of();
            String batch = service.formatBatchMarkdown(empty);
            assertNotNull(batch);
            assertTrue(batch.contains("0只"));
        }
    }

    @Nested
    @DisplayName("信号中文化")
    class SignalLocalizationTests {

        @Test
        @DisplayName("strong_buy显示强烈买入")
        void strongBuyLocalized() {
            AnalysisReport report = createTestReport();
            report.setSignal("strong_buy");
            assertTrue(service.formatMarkdown(report).contains("强烈买入"));
        }

        @Test
        @DisplayName("sell显示卖出")
        void sellLocalized() {
            AnalysisReport report = createTestReport();
            report.setSignal("sell");
            assertTrue(service.formatMarkdown(report).contains("卖出"));
        }

        @Test
        @DisplayName("strong_sell显示强烈卖出")
        void strongSellLocalized() {
            AnalysisReport report = createTestReport();
            report.setSignal("strong_sell");
            assertTrue(service.formatMarkdown(report).contains("强烈卖出"));
        }

        @Test
        @DisplayName("weak_buy显示偏多")
        void weakBuyLocalized() {
            AnalysisReport report = createTestReport();
            report.setSignal("weak_buy");
            assertTrue(service.formatMarkdown(report).contains("偏多"));
        }

        @Test
        @DisplayName("weak_sell显示偏空")
        void weakSellLocalized() {
            AnalysisReport report = createTestReport();
            report.setSignal("weak_sell");
            assertTrue(service.formatMarkdown(report).contains("偏空"));
        }

        @Test
        @DisplayName("neutral显示中性")
        void neutralLocalized() {
            AnalysisReport report = createTestReport();
            report.setSignal("neutral");
            assertTrue(service.formatMarkdown(report).contains("中性"));
        }
    }
}
