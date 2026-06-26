package io.leavesfly.stock.application.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 流水线阶段耗时记录（结构化日志，便于后续接入 Micrometer）。
 */
@Component
public class PipelineMetrics {

    private static final Logger log = LoggerFactory.getLogger(PipelineMetrics.class);

    public void recordPhase(String stockCode, String phase, long durationMs) {
        log.info("pipeline_phase stock={} phase={} duration_ms={}", stockCode, phase, durationMs);
    }
}
