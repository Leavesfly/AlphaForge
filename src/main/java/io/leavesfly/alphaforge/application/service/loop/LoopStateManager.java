package io.leavesfly.alphaforge.application.service.loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loop 状态管理器（系统自我感知层）— 纯运行时状态监控
 *
 * 追踪 Loop 循环的健康状态与性能指标，为系统决策提供依据。
 * 准确率刷新逻辑已移至 SignalOutcomeEvaluator.refreshAndSyncAccuracy()，
 * 本类仅负责循环计数和 Verifier 统计。
 *
 * - lastRunTime:     最后一次完整分析的时间
 * - totalLoopRuns:   累计循环轮次
 * - avgLoopDuration: 平均每轮耗时
 * - consecutiveErrors: 连续失败次数（触发降级策略）
 */
@Component
public class LoopStateManager {

    private static final Logger log = LoggerFactory.getLogger(LoopStateManager.class);

    // ===== 状态字段（线程安全）=====
    private final AtomicLong totalLoopRuns = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);
    private final AtomicInteger totalSignalsVerified = new AtomicInteger(0);
    private final AtomicInteger totalSignalsAdjusted = new AtomicInteger(0);

    private volatile LocalDateTime lastRunTime;
    private volatile String lastRunStatus = "idle";

    // ===== 状态更新 =====

    /**
     * 记录一次完整分析运行开始
     */
    public void onLoopStart(String loopType) {
        lastRunTime = LocalDateTime.now();
        lastRunStatus = "running:" + loopType;
        log.debug("Loop[{}] 开始第 {} 轮", loopType, totalLoopRuns.get() + 1);
    }

    /**
     * 记录一次完整分析运行完成
     */
    public void onLoopComplete(String loopType, long durationMs, int stocksAnalyzed) {
        totalLoopRuns.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
        consecutiveErrors.set(0);
        lastRunStatus = "idle";
        log.info("Loop[{}] 第{}轮完成 | 分析:{}只 耗时:{}ms | 累计轮次:{}",
                loopType, totalLoopRuns.get(), stocksAnalyzed, durationMs, totalLoopRuns.get());
    }

    /**
     * 记录循环失败
     */
    public void onLoopError(String loopType, String errorMsg) {
        consecutiveErrors.incrementAndGet();
        lastRunStatus = "error:" + loopType;
        log.error("Loop[{}] 执行失败（连续错误次数:{}）: {}", loopType, consecutiveErrors.get(), errorMsg);
    }

    /**
     * 记录 Verifier 统计
     */
    public void onSignalVerified(boolean wasAdjusted) {
        totalSignalsVerified.incrementAndGet();
        if (wasAdjusted) totalSignalsAdjusted.incrementAndGet();
    }

    // ===== 状态查询 =====

    /**
     * 获取连续错误次数（供 Scheduler 降级判断使用）
     */
    public int getConsecutiveErrors() {
        return consecutiveErrors.get();
    }

    /**
     * 判断 Loop 是否健康
     * 条件：连续错误 < 3，且最近运行时间在24小时内（工作日）
     */
    public boolean isHealthy() {
        return consecutiveErrors.get() < 3;
    }

    /**
     * 判断是否需要降级（连续失败过多）
     */
    public boolean needsDegradation() {
        return consecutiveErrors.get() >= 3;
    }

    /**
     * 获取平均循环耗时（毫秒）
     */
    public long getAvgLoopDurationMs() {
        long runs = totalLoopRuns.get();
        return runs > 0 ? totalDurationMs.get() / runs : 0;
    }

    /**
     * 获取 Verifier 调整率（被 Verifier 调整信号的比例）
     */
    public double getVerifierAdjustmentRate() {
        int verified = totalSignalsVerified.get();
        return verified > 0 ? (double) totalSignalsAdjusted.get() / verified * 100.0 : 0.0;
    }

    /**
     * 导出健康状态报告（供 API 返回）
     */
    public Map<String, Object> getHealthReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("healthy", isHealthy());
        report.put("status", lastRunStatus);
        report.put("last_run_time", lastRunTime != null ? lastRunTime.toString() : "never");
        report.put("total_loop_runs", totalLoopRuns.get());
        report.put("avg_loop_duration_ms", getAvgLoopDurationMs());
        report.put("consecutive_errors", consecutiveErrors.get());
        report.put("verifier_adjustment_rate",
                String.format("%.1f%%", getVerifierAdjustmentRate()));
        report.put("total_signals_verified", totalSignalsVerified.get());
        report.put("total_signals_adjusted", totalSignalsAdjusted.get());
        return report;
    }
}
