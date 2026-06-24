package io.leavesfly.stock.presentation.scheduler;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.application.pipeline.StockAnalysisPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 分析调度器
 * 
 * 对应Python版本的 src/scheduler.py
 * 支持定时自动执行分析任务，工作日执行
 */
@Component
public class AnalysisScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalysisScheduler.class);
    private final AppConfig config;
    private final StockAnalysisPipeline pipeline;
    private volatile boolean running = false;

    public AnalysisScheduler(AppConfig config, StockAnalysisPipeline pipeline) {
        this.config = config;
        this.pipeline = pipeline;
    }

    /**
     * 启动调度器(由CommandLineRunner在schedule模式调用)
     */
    public void start() {
        running = true;
        log.info("调度器已启动, cron: {}", config.getScheduleCron());
    }

    /**
     * 停止调度器
     */
    public void stop() {
        running = false;
        log.info("调度器已停止");
    }

    /**
     * 定时任务 - 工作日下午6点执行
     * 默认 cron: 0 0 18 * * MON-FRI
     */
    @Scheduled(cron = "${SCHEDULE_CRON:0 0 18 * * MON-FRI}")
    public void scheduledAnalysis() {
        if (!running) return;
        
        // 检查是否工作日
        if (!isTradingDay()) {
            log.info("非交易日，跳过定时分析");
            return;
        }

        log.info("========== 定时分析任务触发 ==========");
        log.info("触发时间: {}", LocalDateTime.now());

        try {
            pipeline.runFullAnalysis(null, false, false);
        } catch (Exception e) {
            log.error("定时分析任务执行失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 判断当前是否为交易日
     */
    private boolean isTradingDay() {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        // 排除周末
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        // TODO: 接入节假日日历排除法定假日
        return true;
    }

    public boolean isRunning() {
        return running;
    }
}
