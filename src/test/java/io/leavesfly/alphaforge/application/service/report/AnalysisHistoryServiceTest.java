package io.leavesfly.alphaforge.application.service.report;

import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.domain.repository.analysis.AnalysisReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisHistoryService 分析历史服务测试")
class AnalysisHistoryServiceTest {

    @Mock
    private AnalysisReportRepository repository;

    private AnalysisHistoryService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisHistoryService(repository);
    }

    private AnalysisReport report(String code, String name) {
        AnalysisReport r = new AnalysisReport();
        r.setId(1L);
        r.setStockCode(code);
        r.setStockName(name);
        r.setAnalysisDate(LocalDateTime.of(2024, 6, 1, 10, 0));
        return r;
    }

    @Test
    @DisplayName("保存报告成功应返回持久化结果")
    void saveReportSuccess() {
        AnalysisReport input = report("600519", "贵州茅台");
        when(repository.save(input)).thenReturn(input);

        AnalysisReport saved = service.saveReport(input);

        assertSame(input, saved);
        verify(repository).save(input);
    }

    @Test
    @DisplayName("保存失败时应返回原报告而不抛异常")
    void saveReportFailureReturnsOriginal() {
        AnalysisReport input = report("600519", "贵州茅台");
        when(repository.save(input)).thenThrow(new RuntimeException("DB error"));

        AnalysisReport result = service.saveReport(input);

        assertSame(input, result);
    }

    @Test
    @DisplayName("按股票代码查询最近报告应限制条数")
    void getRecentReportsByStockCode() {
        List<AnalysisReport> all = List.of(
                report("600519", "贵州茅台"),
                report("600519", "贵州茅台"),
                report("600519", "贵州茅台")
        );
        when(repository.findByStockCodeOrderByAnalysisDateDesc("600519")).thenReturn(all);

        List<AnalysisReport> result = service.getRecentReports("600519", 2);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("未指定股票代码时应查全部并限制条数")
    void getRecentReportsAll() {
        List<AnalysisReport> all = List.of(
                report("600519", "贵州茅台"),
                report("000858", "五粮液")
        );
        when(repository.findAllByOrderByAnalysisDateDesc()).thenReturn(all);

        List<AnalysisReport> result = service.getRecentReports(null, 1);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("按 ID 查询报告")
    void getReportById() {
        AnalysisReport r = report("600519", "贵州茅台");
        when(repository.findByIdOpt(1L)).thenReturn(Optional.of(r));

        assertTrue(service.getReportById(1L).isPresent());
        assertEquals("600519", service.getReportById(1L).get().getStockCode());
    }

    @Test
    @DisplayName("按日期范围查询报告")
    void getReportsByDateRange() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);
        when(repository.findByAnalysisDateBetweenOrderByAnalysisDateDesc(start, end))
                .thenReturn(List.of(report("600519", "贵州茅台")));

        List<AnalysisReport> result = service.getReportsByDateRange(start, end);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("统计总分析次数")
    void getTotalAnalysisCount() {
        when(repository.count()).thenReturn(42L);
        assertEquals(42L, service.getTotalAnalysisCount());
    }

    @Test
    @DisplayName("删除历史报告")
    void deleteReport() {
        service.deleteReport(99L);
        verify(repository).deleteById(99L);
    }
}
