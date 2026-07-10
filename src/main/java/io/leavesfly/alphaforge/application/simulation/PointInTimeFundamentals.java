package io.leavesfly.alphaforge.application.backtest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 点时基本面序列：按财报可用日对齐，回测时按交易日取「当时可知」的最新一期。
 *
 * <p>数据来自 {@code MarketDataPort#getKeyIndicators}。可用日近似为
 * {@code report_date + publish_lag_days}（默认 45 天），避免用到未披露信息。</p>
 */
public final class PointInTimeFundamentals {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    private final List<Snapshot> snapshots;

    private PointInTimeFundamentals(List<Snapshot> snapshots) {
        this.snapshots = List.copyOf(snapshots);
    }

    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    public int size() {
        return snapshots.size();
    }

    /**
     * 从关键指标行构建点时序列。
     *
     * @param rows            getKeyIndicators 返回行（可乱序）
     * @param publishLagDays  报告期末到可交易使用的滞后天数
     */
    public static PointInTimeFundamentals fromKeyIndicators(List<Map<String, Object>> rows, int publishLagDays) {
        if (rows == null || rows.isEmpty()) {
            return new PointInTimeFundamentals(List.of());
        }
        List<RawReport> reports = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            LocalDate reportDate = parseDate(row.get("report_date"));
            if (reportDate == null) {
                continue;
            }
            reports.add(new RawReport(
                    reportDate,
                    num(row, "operate_income_yoy"),
                    num(row, "roe_avg", "roe"),
                    num(row, "basic_eps_yoy"),
                    num(row, "basic_eps"),
                    num(row, "bps")
            ));
        }
        reports.sort(Comparator.comparing(RawReport::reportDate));

        List<Snapshot> snaps = new ArrayList<>();
        for (int i = 0; i < reports.size(); i++) {
            RawReport cur = reports.get(i);
            RawReport prev = i > 0 ? reports.get(i - 1) : null;
            boolean revenueDecline = prev != null
                    && !Double.isNaN(cur.revenueYoy) && !Double.isNaN(prev.revenueYoy)
                    && cur.revenueYoy < prev.revenueYoy && cur.revenueYoy < 0;
            boolean profitDecline = prev != null
                    && !Double.isNaN(cur.epsYoy) && !Double.isNaN(prev.epsYoy)
                    && cur.epsYoy < prev.epsYoy && cur.epsYoy < 0;
            boolean roeDecline = prev != null
                    && !Double.isNaN(cur.roe) && !Double.isNaN(prev.roe)
                    && cur.roe < prev.roe - 1.0; // ROE 下降超过 1 个百分点
            LocalDate availableFrom = cur.reportDate.plusDays(Math.max(0, publishLagDays));
            snaps.add(new Snapshot(availableFrom, cur.revenueYoy, cur.roe, cur.eps, cur.bps,
                    revenueDecline, profitDecline, roeDecline));
        }
        snaps.sort(Comparator.comparing(Snapshot::availableFrom));
        return new PointInTimeFundamentals(snaps);
    }

    public static PointInTimeFundamentals empty() {
        return new PointInTimeFundamentals(List.of());
    }

    /**
     * 返回可合并进回测 parameters 的点时字段。
     * 无可用财报时标记 {@code fundamentals_available=false}，使基本面入场条件失败。
     */
    public Map<String, Object> asOf(LocalDate tradeDate, Double closePrice) {
        Map<String, Object> out = new LinkedHashMap<>();
        Snapshot snap = latestOnOrBefore(tradeDate);
        if (snap == null) {
            out.put("fundamentals_available", false);
            return out;
        }
        out.put("fundamentals_available", true);
        if (!Double.isNaN(snap.revenueGrowth)) {
            out.put("actual_revenue_growth", snap.revenueGrowth);
        }
        if (!Double.isNaN(snap.roe)) {
            out.put("actual_roe", snap.roe);
        }
        if (closePrice != null && closePrice > 0 && !Double.isNaN(snap.eps) && snap.eps > 0) {
            out.put("actual_pe", closePrice / snap.eps);
        }
        if (closePrice != null && closePrice > 0 && !Double.isNaN(snap.bps) && snap.bps > 0) {
            out.put("actual_pb", closePrice / snap.bps);
        }
        out.put("revenue_decline", snap.revenueDecline);
        out.put("profit_decline", snap.profitDecline);
        out.put("roe_decline", snap.roeDecline);
        return out;
    }

    private Snapshot latestOnOrBefore(LocalDate tradeDate) {
        Snapshot latest = null;
        for (Snapshot snap : snapshots) {
            if (!snap.availableFrom.isAfter(tradeDate)) {
                latest = snap;
            } else {
                break;
            }
        }
        return latest;
    }

    private static LocalDate parseDate(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        if (text.length() >= 10) {
            text = text.substring(0, 10);
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, fmt);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static double num(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object val = row.get(key);
            if (val instanceof Number number) {
                return number.doubleValue();
            }
            if (val != null) {
                try {
                    return Double.parseDouble(String.valueOf(val));
                } catch (NumberFormatException ignored) {
                    // next
                }
            }
        }
        return Double.NaN;
    }

    private record RawReport(LocalDate reportDate, double revenueYoy, double roe,
                             double epsYoy, double eps, double bps) {
    }

    private record Snapshot(LocalDate availableFrom, double revenueGrowth, double roe,
                            double eps, double bps,
                            boolean revenueDecline, boolean profitDecline, boolean roeDecline) {
    }
}
