package io.leavesfly.alphaforge.presentation.scheduler;

import io.leavesfly.alphaforge.config.AppConfig;
import io.leavesfly.alphaforge.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.alphaforge.application.service.signal.SignalOutcomeEvaluator;
import io.leavesfly.alphaforge.application.service.loop.LoopStateManager;
import io.leavesfly.alphaforge.domain.service.TradingCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 多频率分析调度器（Loop Engineering 循环引擎）
 *
 * 实现论文中「系统不断迭代，而非人工触发一次」的核心理念：
 *
 * 循环1 - 盘前告警扫描     (09:25, MON-FRI) ── 检查自选股告警条件
 * 循环2 - 盘中信号速扫     (10:00 & 14:30, MON-FRI) ── 轻量技术信号扫描
 * 循环3 - 盘后完整分析     (18:00, MON-FRI) ── 全链路 ReActAgent 深度分析
 * 循环4 - 信号结果评估     (19:00, MON-FRI) ── 评估历史信号准确率，回填经验记忆
 *
 * 每轮执行均通过 LoopStateManager 记录状态，实现系统自我感知。
 */
@Component
public class AnalysisScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalysisScheduler.class);
    private final AppConfig config;
    private final StockAnalysisPipeline pipeline;
    private final TradingCalendar tradingCalendar;
    private final SignalOutcomeEvaluator outcomeEvaluator;
    private final LoopStateManager loopState;
    private volatile boolean running = false;

    public AnalysisScheduler(AppConfig config, StockAnalysisPipeline pipeline,
                             TradingCalendar tradingCalendar,
                             SignalOutcomeEvaluator outcomeEvaluator,
                             LoopStateManager loopState) {
        this.config = config;
        this.pipeline = pipeline;
        this.tradingCalendar = tradingCalendar;
        this.outcomeEvaluator = outcomeEvaluator;
        this.loopState = loopState;
    }

    /**
     * 启动调度器
     */
    public void start() {
        running = true;
        log.info("调度器已启动 | cron: {}", config.getScheduleCron());
    }

    /**
     * 停止调度器
     */
    public void stop() {
        running = false;
        log.info("调度器已停止");
    }

    // ===== 循环1: 盘后完整深度分析（主循环，保留原有cron配置）=====

    /**
     * 盘后完整分析 - 工作日下午6点
     * 全链路 ReActAgent 分析 + Verifier 验证 + 通知推送
     */
    @Scheduled(cron = "${SCHEDULE_CRON:0 0 18 * * MON-FRI}")
    public void scheduledFullAnalysis() {
        if (!running || !isTradingDay()) return;
        String loopType = "full_analysis";
        long start = System.currentTimeMillis();

        // ===== 降级检测：连续失败≥ 3 次时切换为轻量技术分析模式 =====
        if (loopState.needsDegradation()) {
            log.warn("[Loop3] ⚠️ 系统降级（连续错误 {}次）- 本轮跳过 LLM，切换为技术指标速扫",
                    loopState.getConsecutiveErrors());
            runDryRunScan(loopType);
            return;
        }

        log.info("========== [Loop3-盘后分析] 触发 | {} ==========", LocalDateTime.now());
        loopState.onLoopStart(loopType);
        try {
            Map<String, Object> result = pipeline.runFullAnalysis(null, false, false);
            int stocks = result.containsKey("total_stocks") ?
                    ((Number) result.get("total_stocks")).intValue() : 0;
            loopState.onLoopComplete(loopType, System.currentTimeMillis() - start, stocks);
        } catch (Exception e) {
            log.error("[Loop3] 盘后分析失败: {}", e.getMessage(), e);
            loopState.onLoopError(loopType, e.getMessage());
        }
    }

    // ===== 循环2: 信号结果评估（复利记忆层）=====

    /**
     * 盘后信号评估 - 工作日下午7点
     * 评估历史信号准确率，构建复利经验记忆
     */
    @Scheduled(cron = "0 0 19 * * MON-FRI")
    public void scheduledSignalEvaluation() {
        if (!running || !isTradingDay()) return;
        log.info("========== [Loop4-信号评估] 触发 | {} ==========", LocalDateTime.now());
        try {
            SignalOutcomeEvaluator.EvaluationSummary summary = outcomeEvaluator.evaluatePendingSignals();
            loopState.refreshAccuracy();
            log.info("[Loop4] 信号评估完成: {}", summary);
        } catch (Exception e) {
            log.error("[Loop4] 信号评估失败: {}", e.getMessage(), e);
        }
    }

    // ===== 循环3: 盘中轻量技术信号速扫 =====

    /**
     * 盘中技术信号速扫 - 工作日 10:00 & 14:30
     * 仅执行技术指标快速扫描，不调用 LLM（轻量低成本）
     * 主要用于触发告警，不保存分析报告
     */
    @Scheduled(cron = "0 0 10 * * MON-FRI")
    public void scheduledIntradayScanAM() {
        if (!running || !isTradingDay()) return;
        log.info("[Loop2-盘中速扫-AM] 触发 | {}", LocalDateTime.now());
        runDryRunScan("intraday_am");
    }

    @Scheduled(cron = "0 30 14 * * MON-FRI")
    public void scheduledIntradayScanPM() {
        if (!running || !isTradingDay()) return;
        log.info("[Loop2-盘中速扫-PM] 触发 | {}", LocalDateTime.now());
        runDryRunScan("intraday_pm");
    }

    /**
     * 执行 dryRun 模式扫描（只做技术评分，不调用LLM，不推送通知）
     */
    private void runDryRunScan(String loopType) {
        long start = System.currentTimeMillis();
        loopState.onLoopStart(loopType);
        try {
            // dryRun=true: 不调用LLM，不推送，只做技术分析
            Map<String, Object> result = pipeline.runFullAnalysis(null, true, false);
            int stocks = result.containsKey("total_stocks") ?
                    ((Number) result.get("total_stocks")).intValue() : 0;
            loopState.onLoopComplete(loopType, System.currentTimeMillis() - start, stocks);
        } catch (Exception e) {
            log.warn("[{}] 盘中速扫失败: {}", loopType, e.getMessage());
            loopState.onLoopError(loopType, e.getMessage());
        }
    }

    /**
     * 判断当前是否为交易日
     */
    private boolean isTradingDay() {
        return tradingCalendar.isTradingDay(LocalDate.now());
    }

    public boolean isRunning() { return running; }
}
