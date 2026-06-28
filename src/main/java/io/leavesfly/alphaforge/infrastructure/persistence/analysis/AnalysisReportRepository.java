package io.leavesfly.alphaforge.infrastructure.persistence.analysis;

import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 分析报告数据访问层
 */
@Mapper
public interface AnalysisReportRepository {

    void insert(AnalysisReport report);

    void update(AnalysisReport report);

    AnalysisReport findById(@Param("id") Long id);

    List<AnalysisReport> findAll();

    void deleteById(@Param("id") Long id);

    List<AnalysisReport> findByStockCodeOrderByAnalysisDateDesc(@Param("stockCode") String stockCode);

    List<AnalysisReport> findAllByOrderByAnalysisDateDesc();

    List<AnalysisReport> findByAnalysisDateBetweenOrderByAnalysisDateDesc(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<AnalysisReport> findByMarketOrderByAnalysisDateDesc(@Param("market") String market);

    List<AnalysisReport> findBySignalOrderByAnalysisDateDesc(@Param("signal") String signal);

    long countByStockCode(@Param("stockCode") String stockCode);

    long count();

    default AnalysisReport save(AnalysisReport report) {
        if (report.getId() == null) {
            if (report.getCreatedAt() == null) {
                report.setCreatedAt(LocalDateTime.now());
            }
            if (report.getAnalysisDate() == null) {
                report.setAnalysisDate(LocalDateTime.now());
            }
            insert(report);
        } else {
            update(report);
        }
        return report;
    }

    default Optional<AnalysisReport> findByIdOpt(Long id) {
        return Optional.ofNullable(findById(id));
    }
}
