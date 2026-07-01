package io.leavesfly.alphaforge.domain.service;

import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交易日历
 *
 * 判断交易日、节假日、连续交易时段等。
 * 支持A股、港股、美股多市场时区感知。
 */
public class TradingCalendar {

    private static final Logger log = LoggerFactory.getLogger(TradingCalendar.class);

    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");

    /** A股节假日集合（懒加载，日缓存） */
    private volatile Set<LocalDate> holidays = null;
    private volatile LocalDate holidaysLoadDate = null;

    /** 市场时区映射 */
    private static final Map<MarketType, ZoneId> MARKET_TIMEZONES = Map.of(
            MarketType.A, ZoneId.of("Asia/Shanghai"),
            MarketType.HK, ZoneId.of("Asia/Hong_Kong"),
            MarketType.US, ZoneId.of("America/New_York"),
            MarketType.JP, ZoneId.of("Asia/Tokyo"),
            MarketType.KR, ZoneId.of("Asia/Seoul")
    );

    /** 各市场收盘时间（用于缓存新鲜度判断） */
    private static final Map<MarketType, LocalTime> MARKET_CLOSE_TIMES = Map.of(
            MarketType.A, LocalTime.of(15, 0),
            MarketType.HK, LocalTime.of(16, 0),
            MarketType.US, LocalTime.of(16, 0),
            MarketType.JP, LocalTime.of(15, 0),
            MarketType.KR, LocalTime.of(15, 30)
    );

    /** A股交易时段 */
    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    /** 市场收盘后数据可用缓冲时间（小时） */
    private static final int CLOSE_BUFFER_HOURS = 2;

    /** 内置A股常见节假日（作为API加载失败的降级方案） */
    private static final Set<LocalDate> BUILT_IN_HOLIDAYS_2026 = Set.of(
            LocalDate.of(2026, 1, 1),   // 元旦
            LocalDate.of(2026, 2, 16),  // 春节
            LocalDate.of(2026, 2, 17),
            LocalDate.of(2026, 2, 18),
            LocalDate.of(2026, 2, 19),
            LocalDate.of(2026, 2, 20),
            LocalDate.of(2026, 2, 23),
            LocalDate.of(2026, 4, 6),   // 清明
            LocalDate.of(2026, 5, 1),   // 劳动节
            LocalDate.of(2026, 5, 4),
            LocalDate.of(2026, 5, 5),
            LocalDate.of(2026, 6, 19),  // 端午
            LocalDate.of(2026, 9, 25),  // 中秋
            LocalDate.of(2026, 10, 1),  // 国庆
            LocalDate.of(2026, 10, 2),
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 7),
            LocalDate.of(2026, 10, 8)
    );

    /**
     * 判断是否为交易日（默认A股）
     */
    public boolean isTradingDay(LocalDate date) {
        return isTradingDay(date, MarketType.A);
    }

    /**
     * 判断是否为指定市场的交易日
     */
    public boolean isTradingDay(LocalDate date, MarketType market) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        if (market == MarketType.A) {
            return !getHolidays().contains(date);
        }
        // 港股/美股: 暂用周末判断，节假日需独立数据源（后续扩展）
        return true;
    }

    /**
     * 判断当前是否在A股交易时间内
     */
    public boolean isTradingTime() {
        LocalDate today = LocalDate.now(CN_ZONE);
        if (!isTradingDay(today)) return false;
        LocalTime now = LocalTime.now(CN_ZONE);
        return (now.isAfter(MORNING_OPEN) && now.isBefore(MORNING_CLOSE)) ||
               (now.isAfter(AFTERNOON_OPEN) && now.isBefore(AFTERNOON_CLOSE));
    }

    /**
     * 获取最近N个交易日
     */
    public List<LocalDate> getRecentTradingDays(int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate date = LocalDate.now(CN_ZONE);
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
        ZoneId tz = getMarketTimezone(market);
        LocalDate today = LocalDate.now(tz);
        LocalTime now = LocalTime.now(tz);
        switch (market.toUpperCase()) {
            case "A":
                if (!isTradingDay(today)) return false;
                return (now.isAfter(MORNING_OPEN) && now.isBefore(MORNING_CLOSE)) ||
                       (now.isAfter(AFTERNOON_OPEN) && now.isBefore(AFTERNOON_CLOSE));
            case "US": // 美东时间 9:30-16:00
                return now.isAfter(LocalTime.of(9, 30)) && now.isBefore(LocalTime.of(16, 0));
            case "HK": // 港股 9:30-12:00, 13:00-16:00
                return (now.isAfter(LocalTime.of(9, 30)) && now.isBefore(LocalTime.of(12, 0))) ||
                       (now.isAfter(LocalTime.of(13, 0)) && now.isBefore(LocalTime.of(16, 0)));
            default: return false;
        }
    }

    /**
     * 获取指定市场的收盘时间
     */
    public LocalTime getMarketCloseTime(MarketType market) {
        return MARKET_CLOSE_TIMES.getOrDefault(market, LocalTime.of(15, 0));
    }

    /**
     * 获取指定市场的时区
     */
    public ZoneId getMarketTimezone(MarketType market) {
        return MARKET_TIMEZONES.getOrDefault(market, CN_ZONE);
    }

    /**
     * 获取指定市场今天是否已收盘（含缓冲时间）
     */
    public boolean isMarketClosed(MarketType market) {
        ZoneId tz = getMarketTimezone(market);
        LocalTime now = LocalTime.now(tz);
        LocalTime closePlusBuffer = getMarketCloseTime(market).plusHours(CLOSE_BUFFER_HOURS);
        return now.isAfter(closePlusBuffer);
    }

    /**
     * 获取市场时区（字符串版本，保持向后兼容）
     */
    private ZoneId getMarketTimezone(String market) {
        switch (market.toUpperCase()) {
            case "US": return ZoneId.of("America/New_York");
            case "HK": return ZoneId.of("Asia/Hong_Kong");
            case "JP": return ZoneId.of("Asia/Tokyo");
            case "KR": return ZoneId.of("Asia/Seoul");
            default: return CN_ZONE;
        }
    }

    /**
     * 获取A股节假日集合（懒加载 + 日缓存）
     * 优先从内置节假日表获取，后续可扩展为从东财API动态加载
     */
    private Set<LocalDate> getHolidays() {
        LocalDate today = LocalDate.now(CN_ZONE);
        // 当天已加载过，直接返回缓存
        if (holidays != null && today.equals(holidaysLoadDate)) {
            return holidays;
        }
        // 加载节假日：当前使用内置节假日，后续可扩展为API加载
        Set<LocalDate> loaded = new HashSet<>(BUILT_IN_HOLIDAYS_2026);
        holidays = loaded;
        holidaysLoadDate = today;
        log.debug("A股节假日集合已加载: {} 个日期", loaded.size());
        return holidays;
    }
}
