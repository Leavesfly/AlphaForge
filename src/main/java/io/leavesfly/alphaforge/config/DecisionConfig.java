package io.leavesfly.alphaforge.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 买点三灯决策配置 — DECISION_* 前缀环境变量。
 *
 * <p>三灯阈值与 ATR 倍数为 domain 纪律预设常量（与 skill 口径一致），
 * 此处仅暴露数据窗口、基准与仓位上限等运行参数。</p>
 */
@Component
public class DecisionConfig {

    private static final Logger log = LoggerFactory.getLogger(DecisionConfig.class);

    private final EnvVarProvider envVarProvider;

    /** 拉取 K 线的日历日窗口（默认 500 天 ≈ 340 交易日，满足 MIN_BARS=250） */
    private int historyDays = 500;

    /** A 股基准指数（势灯相对强度与大盘 risk-off 判定） */
    private String benchmarkCode = "000300";

    /** 单票建议仓位市值占可用资金的上限（1.0 = 仅受"市值≤资金"约束） */
    private double maxPositionPct = 1.0;

    public DecisionConfig(EnvVarProvider envVarProvider) {
        this.envVarProvider = envVarProvider;
    }

    @PostConstruct
    public void init() {
        historyDays = Math.max(300, envVarProvider.getInt("DECISION_HISTORY_DAYS", 500));
        benchmarkCode = envVarProvider.get("DECISION_BENCHMARK_CODE", "000300").trim();
        maxPositionPct = envVarProvider.getDouble("DECISION_MAX_POSITION_PCT", 1.0);
        if (maxPositionPct <= 0 || maxPositionPct > 1.0) {
            log.warn("DECISION_MAX_POSITION_PCT={} 非法（需 0~1），回退 1.0", maxPositionPct);
            maxPositionPct = 1.0;
        }
        log.info("决策配置加载完成: historyDays={}, benchmark={}, maxPositionPct={}",
                historyDays, benchmarkCode, maxPositionPct);
    }

    public int getHistoryDays() {
        return historyDays;
    }

    public String getBenchmarkCode() {
        return benchmarkCode;
    }

    public double getMaxPositionPct() {
        return maxPositionPct;
    }
}
