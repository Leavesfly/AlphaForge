package com.stock.core;

import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/**
 * 交易日历
 * 
 * 对应Python版本的 src/core/trading_calendar.py
 * 判断交易日、节假日、连续交易时段等
 */
@Component
public class TradingCalendar {

    /** 中国法定节假日(简化版，实际应从外部数据源加载) */
    private final Set<LocalDate> holidays = new HashSet<>();

    /** A股交易时段 */
    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    /**
     * 判断是否为交易日
     */
    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        return !holidays.contains(date);
    }

    /**
     * 判断当前是否在交易时间内
     */
    public boolean isTradingTime() {
        LocalDate today = LocalDate.now();
        if (!isTradingDay(today)) return false;
        LocalTime now = LocalTime.now();
        return (now.isAfter(MORNING_OPEN) && now.isBefore(MORNING_CLOSE)) ||
               (now.isAfter(AFTERNOON_OPEN) && now.isBefore(AFTERNOON_CLOSE));
    }

    /**
     * 获取最近N个交易日
     */
    public List<LocalDate> getRecentTradingDays(int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate date = LocalDate.now();
        while (days.size() < count) {
            if (isTradingDay(date)) days.add(date);
            date = date.minusDays(1);
        }
        return days;
    }

    /**
     * 获取下一个交易日
     */
    public LocalDate getNextTradingDay(LocalDate from) {
        LocalDate next = from.plusDays(1);
        while (!isTradingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * 获取上一个交易日
     */
    public LocalDate getPreviousTradingDay(LocalDate from) {
        LocalDate prev = from.minusDays(1);
        while (!isTradingDay(prev)) {
            prev = prev.minusDays(1);
        }
        return prev;
    }

    /**
     * 计算两个日期之间的交易日数
     */
    public int countTradingDays(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate date = start;
        while (!date.isAfter(end)) {
            if (isTradingDay(date)) count++;
            date = date.plusDays(1);
        }
        return count;
    }

    /**
     * 判断市场类型的交易时间
     */
    public boolean isMarketOpen(String market) {
        LocalTime now = LocalTime.now(getMarketTimezone(market));
        switch (market.toUpperCase()) {
            case "A": return isTradingTime();
            case "US": // 美东时间 9:30-16:00
                return now.isAfter(LocalTime.of(9, 30)) && now.isBefore(LocalTime.of(16, 0));
            case "HK": // 港股 9:30-12:00, 13:00-16:00
                return (now.isAfter(LocalTime.of(9, 30)) && now.isBefore(LocalTime.of(12, 0))) ||
                       (now.isAfter(LocalTime.of(13, 0)) && now.isBefore(LocalTime.of(16, 0)));
            default: return false;
        }
    }

    private ZoneId getMarketTimezone(String market) {
        switch (market.toUpperCase()) {
            case "US": return ZoneId.of("America/New_York");
            case "HK": return ZoneId.of("Asia/Hong_Kong");
            case "JP": return ZoneId.of("Asia/Tokyo");
            default: return ZoneId.of("Asia/Shanghai");
        }
    }
}
