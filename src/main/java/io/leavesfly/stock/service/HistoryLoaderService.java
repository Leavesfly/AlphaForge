package io.leavesfly.stock.service;

import io.leavesfly.stock.dataprovider.DataFetcherManager;
import io.leavesfly.stock.model.entity.AnalysisReport;
import io.leavesfly.stock.model.entity.StockDailyData;
import io.leavesfly.stock.repository.AnalysisReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 历史数据加载器 + 历史对比服务
 * 对应Python: history_loader.py + history_comparison_service.py
 */
@Service
public class HistoryLoaderService {

    private static final Logger log = LoggerFactory.getLogger(HistoryLoaderService.class);
    private final DataFetcherManager dataFetcher;
    private final AnalysisReportRepository reportRepo;

    public HistoryLoaderService(DataFetcherManager dataFetcher, AnalysisReportRepository reportRepo) {
        this.dataFetcher = dataFetcher;
        this.reportRepo = reportRepo;
    }

    /** 加载指定天数的历史K线 */
    public List<StockDailyData> loadHistory(String stockCode, int days) {
        return dataFetcher.getHistoryData(stockCode, LocalDate.now().minusDays(days), LocalDate.now());
    }

    /** 历史分析报告对比(当前vs上次) */
    public Map<String, Object> compareWithLastReport(String stockCode, AnalysisReport current) {
        Map<String, Object> comparison = new LinkedHashMap<>();
        List<AnalysisReport> history = reportRepo.findByStockCodeOrderByAnalysisDateDesc(stockCode);
        if (history.size() < 2) {
            comparison.put("has_previous", false);
            return comparison;
        }
        AnalysisReport previous = history.get(1); // 上一次
        comparison.put("has_previous", true);
        comparison.put("prev_signal", previous.getSignal());
        comparison.put("prev_score", previous.getTotalScore());
        comparison.put("signal_changed", !Objects.equals(current.getSignal(), previous.getSignal()));
        int scoreDelta = (current.getTotalScore() != null ? current.getTotalScore() : 50)
                - (previous.getTotalScore() != null ? previous.getTotalScore() : 50);
        comparison.put("score_delta", scoreDelta);
        comparison.put("trend", scoreDelta > 0 ? "improving" : scoreDelta < 0 ? "declining" : "stable");
        return comparison;
    }

    /** 获取历史评分趋势(最近N次分析) */
    public List<Map<String, Object>> getScoreTrend(String stockCode, int count) {
        List<AnalysisReport> history = reportRepo.findByStockCodeOrderByAnalysisDateDesc(stockCode);
        return history.stream().limit(count).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", r.getAnalysisDate());
            m.put("score", r.getTotalScore());
            m.put("signal", r.getSignal());
            return m;
        }).collect(Collectors.toList());
    }
}
