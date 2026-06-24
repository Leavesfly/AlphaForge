package io.leavesfly.stock.service;

import io.leavesfly.stock.model.entity.DecisionSignal;
import io.leavesfly.stock.repository.DecisionSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 决策信号服务
 * 
 * 对应Python版本的 src/services/decision_signal_service.py
 * 功能: 信号生成、追踪、过期处理
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
        if (signal.getValidUntil() == null) {
            signal.setValidUntil(LocalDateTime.now().plusDays(7)); // 默认7天有效
        }
        return signalRepo.save(signal);
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

    /** 标记信号为已执行 */
    public DecisionSignal executeSignal(Long id) {
        return signalRepo.findByIdOpt(id).map(s -> {
            s.setStatus("executed");
            return signalRepo.save(s);
        }).orElse(null);
    }

    /** 取消信号 */
    public DecisionSignal cancelSignal(Long id) {
        return signalRepo.findByIdOpt(id).map(s -> {
            s.setStatus("cancelled");
            return signalRepo.save(s);
        }).orElse(null);
    }

    /** 处理过期信号 */
    public void expireOldSignals() {
        List<DecisionSignal> activeSignals = signalRepo.findByStatusOrderByCreatedAtDesc("active");
        LocalDateTime now = LocalDateTime.now();
        for (DecisionSignal signal : activeSignals) {
            if (signal.getValidUntil() != null && signal.getValidUntil().isBefore(now)) {
                signal.setStatus("expired");
                signalRepo.save(signal);
            }
        }
    }

    /** 从分析报告中提取信号 */
    public DecisionSignal extractFromReport(Long reportId, String stockCode, String stockName,
                                            String signalType, int strength, double confidence,
                                            Double targetPrice, Double stopLoss, String reasoning) {
        DecisionSignal signal = new DecisionSignal();
        signal.setReportId(reportId);
        signal.setStockCode(stockCode);
        signal.setStockName(stockName);
        signal.setSignalType(signalType);
        signal.setStrength(strength);
        signal.setConfidence(confidence);
        signal.setTargetPrice(targetPrice);
        signal.setStopLossPrice(stopLoss);
        signal.setReasoning(reasoning);
        signal.setSource("agent");
        signal.setStatus("active");
        signal.setValidUntil(LocalDateTime.now().plusDays(7));
        return signalRepo.save(signal);
    }
}
