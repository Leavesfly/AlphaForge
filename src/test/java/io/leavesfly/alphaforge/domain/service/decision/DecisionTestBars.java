package io.leavesfly.alphaforge.domain.service.decision;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 三灯引擎测试用 K 线构造工具（domain 与应用层测试共享）。
 */
public final class DecisionTestBars {

    private DecisionTestBars() {
    }

    public static StockDailyData bar(LocalDate date, double close) {
        return bar(date, close, 10_000L);
    }

    public static StockDailyData bar(LocalDate date, double close, long volume) {
        StockDailyData d = new StockDailyData();
        d.setTradeDate(date);
        d.setClosePrice(close);
        d.setOpenPrice(close);
        d.setHighPrice(close);
        d.setLowPrice(close);
        d.setVolume(volume);
        return d;
    }

    /** n 根每日等比变动 dailyChangePct 的序列（从 start 起，日历日连续） */
    public static List<StockDailyData> series(int n, double start, double dailyChangePct) {
        List<StockDailyData> list = new ArrayList<>();
        LocalDate date = LocalDate.of(2023, 1, 2);
        double price = start;
        for (int i = 0; i < n; i++) {
            list.add(bar(date, round2(price)));
            date = date.plusDays(1);
            price = price * (1 + dailyChangePct);
        }
        return list;
    }

    /** n 根涨跌交替序列（奇数根 upPct、偶数根 downPct），净趋势由两比例决定 */
    public static List<StockDailyData> alternating(int n, double start, double upPct, double downPct) {
        List<StockDailyData> list = new ArrayList<>();
        LocalDate date = LocalDate.of(2023, 1, 2);
        double price = start;
        for (int i = 0; i < n; i++) {
            list.add(bar(date, round2(price)));
            date = date.plusDays(1);
            price = price * (1 + (i % 2 == 0 ? upPct : downPct));
        }
        return list;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
