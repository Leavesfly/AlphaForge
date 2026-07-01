package io.leavesfly.alphaforge.application.service.report;

import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.domain.repository.analysis.AnalysisReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 分析历史服务
 *
 * 负责分析报告的持久化存储和查询
 */
@Service
public class AnalysisHistoryService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisHistoryService.class);
    private final AnalysisReportRepository repository;

    public AnalysisHistoryService(AnalysisReportRepository repository) {
        this.repository = repository;
    }

    /**
     * 保存分析报告
     */
    public AnalysisReport saveReport(AnalysisReport report) {
        try {
            return repository.save(report);
        } catch (Exception e) {
            log.error("保存分析报告失败: {} - {}", report.getStockCode(), e.getMessage());
            return report;
        }
    }

    /**
     * 查询最近的分析报告
     *
     * @param stockCode 股票代码(可选，为空则查所有)
     * @param limit     返回条数
     * @return 报告列表
     */
    public List<AnalysisReport> getRecentReports(String stockCode, int limit) {
        if (stockCode != null && !stockCode.isEmpty()) {
            return repository.findByStockCodeOrderByAnalysisDateDesc(stockCode)
                    .stream().limit(limit).toList();
        }
        return repository.findAllByOrderByAnalysisDateDesc()
                .stream().limit(limit).toList();
    }

    /**
     * 根据ID获取报告详情
     */
    public Optional<AnalysisReport> getReportById(Long id) {
        return repository.findByIdOpt(id);
    }

    /**
     * 获取指定日期范围内的报告
     */
    public List<AnalysisReport> getReportsByDateRange(LocalDateTime start, LocalDateTime end) {
        return repository.findByAnalysisDateBetweenOrderByAnalysisDateDesc(start, end);
    }

    /**
     * 统计分析次数
     */
    public long getTotalAnalysisCount() {
        return repository.count();
    }

    /**
     * 删除历史报告
     */
    public void deleteReport(Long id) {
        repository.deleteById(id);
    }
}
