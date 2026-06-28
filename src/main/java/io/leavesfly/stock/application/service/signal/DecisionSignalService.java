package io.leavesfly.stock.application.service.signal;

import io.leavesfly.stock.domain.model.entity.signal.DecisionSignal;
import io.leavesfly.stock.infrastructure.persistence.signal.DecisionSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 决策信号服务
 * 对齐 Python 版 src/services/decision_signal_service.py
 * 完整的 CRUD + 多条件筛选 + 状态管理
 */
@Service
public class DecisionSignalService {

    private static final Logger log = LoggerFactory.getLogger(DecisionSignalService.class);
    private final DecisionSignalRepository signalRepo;

    public DecisionSignalService(DecisionSignalRepository signalRepo) {
        this.signalRepo = signalRepo;
    }

    /** 创建决策信号 */
    public DecisionSignal createSignal(DecisionSignal signal) {
        if (signal.getStatus() == null) signal.setStatus("active");
        if (signal.getExpiresAt() == null) signal.setExpiresAt(LocalDateTime.now().plusDays(7));
        return signalRepo.save(signal);
    }

    /** 多条件筛选查询(分页) */
    public Map<String, Object> listSignals(String market, String stockCode, String action,
                                            String marketPhase, String sourceType, String status,
                                            LocalDateTime createdFrom, LocalDateTime createdTo,
                                            int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<DecisionSignal> items = signalRepo.findFiltered(market, stockCode, action, marketPhase, sourceType, status, createdFrom, createdTo, pageSize, offset);
        int total = signalRepo.countFiltered(market, stockCode, action, marketPhase, sourceType, status, createdFrom, createdTo);
        return Map.of("items", items, "total", total, "page", page);
    }

    /** 获取活跃信号 */
    public List<DecisionSignal> getActiveSignals() {
        return signalRepo.findByStatusOrderByCreatedAtDesc("active");
    }

    /** 获取指定股票的信号 */
    public List<DecisionSignal> getSignalsByStock(String stockCode) {
        return signalRepo.findByStockCodeOrderByCreatedAtDesc(stockCode);
    }

    /** 获取最近信号 */
    public List<DecisionSignal> getRecentSignals() {
        return signalRepo.findTop20ByOrderByCreatedAtDesc();
    }

    /** 获取信号详情 */
    public Optional<DecisionSignal> getSignalById(Long id) {
        return signalRepo.findByIdOpt(id);
    }

    /** 获取某只股票最新N条信号 */
    public List<DecisionSignal> getLatestByStockCode(String stockCode, int limit) {
        return signalRepo.findByLatestStockCode(stockCode, limit);
    }

    /** 更新信号状态 */
    public DecisionSignal updateSignalStatus(Long id, String status) {
        DecisionSignal signal = signalRepo.findById(id);
        if (signal == null) return null;
        signalRepo.updateStatus(id, status, LocalDateTime.now());
        signal.setStatus(status);
        return signal;
    }

    /** 标记信号为已执行 */
    public DecisionSignal executeSignal(Long id) {
        return updateSignalStatus(id, "executed");
    }

    /** 取消信号 */
    public DecisionSignal cancelSignal(Long id) {
        return updateSignalStatus(id, "cancelled");
    }

    /** 处理过期信号 */
    public void expireOldSignals() {
        List<DecisionSignal> activeSignals = signalRepo.findByStatusOrderByCreatedAtDesc("active");
        LocalDateTime now = LocalDateTime.now();
        for (DecisionSignal signal : activeSignals) {
            if (signal.getExpiresAt() != null && signal.getExpiresAt().isBefore(now)) {
                signalRepo.updateStatus(signal.getId(), "expired", now);
            }
        }
    }

    /** 从分析报告中提取信号 */
    public DecisionSignal extractFromReport(Long reportId, String stockCode, String stockName,
                                            String action, Double confidence, Integer score,
                                            Double targetPrice, Double stopLoss, String reason) {
        DecisionSignal signal = new DecisionSignal();
        signal.setSourceReportId(reportId);
        signal.setStockCode(stockCode);
        signal.setStockName(stockName);
        signal.setAction(action);
        signal.setConfidence(confidence);
        signal.setScore(score);
        signal.setTargetPrice(targetPrice);
        signal.setStopLoss(stopLoss);
        signal.setReason(reason);
        signal.setSourceType("analysis");
        signal.setSourceAgent("pipeline");
        signal.setStatus("active");
        signal.setExpiresAt(LocalDateTime.now().plusDays(7));
        return signalRepo.save(signal);
    }
}
