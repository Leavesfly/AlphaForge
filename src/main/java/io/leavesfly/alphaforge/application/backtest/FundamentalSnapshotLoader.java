package io.leavesfly.alphaforge.application.backtest;

import io.leavesfly.alphaforge.application.simulation.PointInTimeFundamentals;
import io.leavesfly.alphaforge.application.strategy.simulation.FundamentalSnapshotProvider;

import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 为需要基本面条件的回测策略加载点时财务指标。
 */
@Component
public class FundamentalSnapshotLoader implements FundamentalSnapshotProvider {

    private static final Logger log = LoggerFactory.getLogger(FundamentalSnapshotLoader.class);
    /** 报告期末到可交易使用的滞后天数（季报披露近似） */
    public static final int DEFAULT_PUBLISH_LAG_DAYS = 45;

    private final MarketDataPort marketData;

    public FundamentalSnapshotLoader(MarketDataPort marketData) {
        this.marketData = marketData;
    }

    /** 策略是否声明了基本面入场/出场条件 */
    public static boolean needsFundamentals(StrategyDefinition strategy) {
        if (strategy == null || strategy.getBacktest() == null) {
            return false;
        }
        return hasFundamentalCondition(strategy.getBacktest().getEntryConditions())
                || hasFundamentalCondition(strategy.getBacktest().getExitConditions());
    }

    private static boolean hasFundamentalCondition(List<Map<String, Object>> conditions) {
        if (conditions == null) {
            return false;
        }
        for (Map<String, Object> c : conditions) {
            Object type = c.get("type");
            if (type != null) {
                String t = String.valueOf(type);
                if ("fundamental_filter".equals(t) || "fundamental_deterioration".equals(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 拉取关键指标并构建点时序列；失败或无需时返回 empty。
     */
    public PointInTimeFundamentals load(String stockCode, StrategyDefinition strategy) {
        if (!needsFundamentals(strategy)) {
            return PointInTimeFundamentals.empty();
        }
        try {
            List<Map<String, Object>> rows = marketData.getKeyIndicators(stockCode);
            PointInTimeFundamentals series = PointInTimeFundamentals.fromKeyIndicators(
                    rows, DEFAULT_PUBLISH_LAG_DAYS);
            log.info("点时基本面已加载: {} reports={} strategy={}",
                    stockCode, series.size(), strategy.getId());
            return series;
        } catch (Exception e) {
            log.warn("点时基本面加载失败 {}: {}", stockCode, e.getMessage());
            return PointInTimeFundamentals.empty();
        }
    }
}
