package io.leavesfly.alphaforge.infrastructure.dataprovider;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 多源交叉校验器
 *
 * <p>对主源日K与备源同区间数据按交易日对齐，比较 close / OHLC 相对偏差。
 * 用于发现单源脏数据（错价、复权口径异常、缺日等），避免仅靠单源自洽校验漏检。</p>
 *
 * <p>注意：不同源复权口径、停牌处理可能不一致，阈值需留余量；同源族（如东财系）
 * 不应互为校验源。</p>
 */
@Component
public class CrossSourceValidator {

    private static final Logger log = LoggerFactory.getLogger(CrossSourceValidator.class);

    /** 默认收盘价相对偏差阈值（0.5%） */
    public static final double DEFAULT_CLOSE_TOLERANCE = 0.005;
    /** 默认 OHLC 相对偏差阈值（1%） */
    public static final double DEFAULT_OHLC_TOLERANCE = 0.01;
    /** 默认抽检最近交易日数 */
    public static final int DEFAULT_SAMPLE_DAYS = 20;
    /** 默认拒绝阈值：可疑日占比 */
    public static final double DEFAULT_REJECT_RATIO = 0.10;

    /**
     * 交叉校验结果
     */
    public static class CrossCheckResult {
        private final boolean passed;
        private final int comparedDays;
        private final int mismatchDays;
        private final List<String> issues;
        private final String primarySource;
        private final String secondarySource;

        public CrossCheckResult(boolean passed, int comparedDays, int mismatchDays,
                                List<String> issues, String primarySource, String secondarySource) {
            this.passed = passed;
            this.comparedDays = comparedDays;
            this.mismatchDays = mismatchDays;
            this.issues = issues != null ? List.copyOf(issues) : List.of();
            this.primarySource = primarySource;
            this.secondarySource = secondarySource;
        }

        public boolean isPassed() { return passed; }
        public int getComparedDays() { return comparedDays; }
        public int getMismatchDays() { return mismatchDays; }
        public List<String> getIssues() { return issues; }
        public String getPrimarySource() { return primarySource; }
        public String getSecondarySource() { return secondarySource; }

        public double mismatchRatio() {
            return comparedDays == 0 ? 0.0 : (double) mismatchDays / comparedDays;
        }

        public static CrossCheckResult skipped(String reason) {
            return new CrossCheckResult(true, 0, 0, List.of(reason), "", "");
        }
    }

    /**
     * 对主源与备源日K做交叉校验（全量对齐后按 sampleDays 截取尾部）。
     */
    public CrossCheckResult validate(List<StockDailyData> primary,
                                     List<StockDailyData> secondary,
                                     String stockCode,
                                     String primarySource,
                                     String secondarySource,
                                     double closeTolerance,
                                     double ohlcTolerance,
                                     int sampleDays,
                                     double rejectRatio) {
        if (primary == null || primary.isEmpty()) {
            return CrossCheckResult.skipped("主源数据为空，跳过交叉校验");
        }
        if (secondary == null || secondary.isEmpty()) {
            return CrossCheckResult.skipped("备源数据为空，跳过交叉校验");
        }

        Map<LocalDate, StockDailyData> secondaryByDate = indexByDate(secondary);
        List<StockDailyData> sample = sampleTail(primary, sampleDays);

        List<String> issues = new ArrayList<>();
        int compared = 0;
        int mismatches = 0;

        for (StockDailyData p : sample) {
            LocalDate date = p.getTradeDate();
            if (date == null) continue;
            StockDailyData s = secondaryByDate.get(date);
            if (s == null) {
                // 备源缺日：记 issue 但不计入 mismatch（可能是停牌/覆盖差异）
                issues.add(String.format("[%s] 备源缺失该交易日", date));
                continue;
            }
            if (!hasValidClose(p) || !hasValidClose(s)) {
                continue;
            }

            compared++;
            List<String> dayIssues = compareBar(date, p, s, closeTolerance, ohlcTolerance);
            if (!dayIssues.isEmpty()) {
                mismatches++;
                issues.addAll(dayIssues);
            }
        }

        boolean passed = compared == 0 || ((double) mismatches / compared) <= rejectRatio;
        if (!passed || mismatches > 0) {
            log.warn("[{}] 交叉校验 {} vs {}: 对比{}日, 可疑{}日, 通过={}, 问题前5条: {}",
                    stockCode, primarySource, secondarySource, compared, mismatches, passed,
                    issues.stream().limit(5).collect(Collectors.toList()));
        } else {
            log.debug("[{}] 交叉校验通过 {} vs {}: 对比{}日",
                    stockCode, primarySource, secondarySource, compared);
        }

        return new CrossCheckResult(passed, compared, mismatches, issues, primarySource, secondarySource);
    }

    /** 便捷重载：使用默认阈值 */
    public CrossCheckResult validate(List<StockDailyData> primary,
                                     List<StockDailyData> secondary,
                                     String stockCode,
                                     String primarySource,
                                     String secondarySource) {
        return validate(primary, secondary, stockCode, primarySource, secondarySource,
                DEFAULT_CLOSE_TOLERANCE, DEFAULT_OHLC_TOLERANCE, DEFAULT_SAMPLE_DAYS, DEFAULT_REJECT_RATIO);
    }

    private List<String> compareBar(LocalDate date, StockDailyData primary, StockDailyData secondary,
                                    double closeTolerance, double ohlcTolerance) {
        List<String> issues = new ArrayList<>();

        double closeDiff = relativeDiff(primary.getClosePrice(), secondary.getClosePrice());
        if (closeDiff > closeTolerance) {
            issues.add(String.format("[%s] close 偏差 %.2f%% (主:%.4f 备:%.4f)",
                    date, closeDiff * 100, primary.getClosePrice(), secondary.getClosePrice()));
        }

        compareField(issues, date, "open", primary.getOpenPrice(), secondary.getOpenPrice(), ohlcTolerance);
        compareField(issues, date, "high", primary.getHighPrice(), secondary.getHighPrice(), ohlcTolerance);
        compareField(issues, date, "low", primary.getLowPrice(), secondary.getLowPrice(), ohlcTolerance);

        return issues;
    }

    private void compareField(List<String> issues, LocalDate date, String field,
                              Double primary, Double secondary, double tolerance) {
        if (primary == null || secondary == null || primary <= 0 || secondary <= 0) {
            return;
        }
        double diff = relativeDiff(primary, secondary);
        if (diff > tolerance) {
            issues.add(String.format("[%s] %s 偏差 %.2f%% (主:%.4f 备:%.4f)",
                    date, field, diff * 100, primary, secondary));
        }
    }

    static double relativeDiff(double a, double b) {
        double denom = Math.max(Math.abs(a), Math.abs(b));
        if (denom == 0) return 0.0;
        return Math.abs(a - b) / denom;
    }

    private static boolean hasValidClose(StockDailyData bar) {
        return bar.getClosePrice() != null && bar.getClosePrice() > 0;
    }

    private static Map<LocalDate, StockDailyData> indexByDate(List<StockDailyData> data) {
        Map<LocalDate, StockDailyData> map = new HashMap<>();
        for (StockDailyData bar : data) {
            if (bar.getTradeDate() != null) {
                map.put(bar.getTradeDate(), bar);
            }
        }
        return map;
    }

    /**
     * 取按日期升序后的尾部 sampleDays 条（不足则全取）。
     */
    static List<StockDailyData> sampleTail(List<StockDailyData> data, int sampleDays) {
        if (data == null || data.isEmpty() || sampleDays <= 0) {
            return List.of();
        }
        List<StockDailyData> sorted = data.stream()
                .filter(Objects::nonNull)
                .filter(d -> d.getTradeDate() != null)
                .sorted(Comparator.comparing(StockDailyData::getTradeDate))
                .collect(Collectors.toCollection(ArrayList::new));
        if (sorted.size() <= sampleDays) {
            return sorted;
        }
        return sorted.subList(sorted.size() - sampleDays, sorted.size());
    }
}
