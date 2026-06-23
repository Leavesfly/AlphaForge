package io.leavesfly.stock.repository;

import io.leavesfly.stock.model.entity.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分析报告数据访问层
 */
@Repository
public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    /** 按股票代码查询，按日期倒序 */
    List<AnalysisReport> findByStockCodeOrderByAnalysisDateDesc(String stockCode);

    /** 查询所有报告，按日期倒序 */
    List<AnalysisReport> findAllByOrderByAnalysisDateDesc();

    /** 按日期范围查询 */
    List<AnalysisReport> findByAnalysisDateBetweenOrderByAnalysisDateDesc(LocalDateTime start, LocalDateTime end);

    /** 按市场类型查询 */
    List<AnalysisReport> findByMarketOrderByAnalysisDateDesc(String market);

    /** 按信号查询 */
    List<AnalysisReport> findBySignalOrderByAnalysisDateDesc(String signal);

    /** 统计指定股票的分析次数 */
    long countByStockCode(String stockCode);
}
