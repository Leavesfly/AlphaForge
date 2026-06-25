package io.leavesfly.stock.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradingCalendar 交易日历测试")
class TradingCalendarTest {

    private TradingCalendar calendar;

    @BeforeEach
    void setUp() {
        calendar = new TradingCalendar();
    }

    @Nested
    @DisplayName("isTradingDay - 判断是否为交易日")
    class IsTradingDayTests {

        @Test
        @DisplayName("周一为交易日")
        void mondayIsTradingDay() {
            LocalDate monday = LocalDate.of(2024, 1, 15); // 周一
            assertEquals(DayOfWeek.MONDAY, monday.getDayOfWeek());
            assertTrue(calendar.isTradingDay(monday));
        }

        @Test
        @DisplayName("周五为交易日")
        void fridayIsTradingDay() {
            LocalDate friday = LocalDate.of(2024, 1, 19); // 周五
            assertEquals(DayOfWeek.FRIDAY, friday.getDayOfWeek());
            assertTrue(calendar.isTradingDay(friday));
        }

        @Test
        @DisplayName("周六非交易日")
        void saturdayIsNotTradingDay() {
            LocalDate saturday = LocalDate.of(2024, 1, 20); // 周六
            assertEquals(DayOfWeek.SATURDAY, saturday.getDayOfWeek());
            assertFalse(calendar.isTradingDay(saturday));
        }

        @Test
        @DisplayName("周日非交易日")
        void sundayIsNotTradingDay() {
            LocalDate sunday = LocalDate.of(2024, 1, 21); // 周日
            assertEquals(DayOfWeek.SUNDAY, sunday.getDayOfWeek());
            assertFalse(calendar.isTradingDay(sunday));
        }
    }

    @Nested
    @DisplayName("getNextTradingDay - 获取下一个交易日")
    class GetNextTradingDayTests {

        @Test
        @DisplayName("周五之后下一个交易日为下周一")
        void afterFridayReturnsMonday() {
            LocalDate friday = LocalDate.of(2024, 1, 19); // 周五
            LocalDate next = calendar.getNextTradingDay(friday);
            assertEquals(DayOfWeek.MONDAY, next.getDayOfWeek());
            assertEquals(LocalDate.of(2024, 1, 22), next);
        }

        @Test
        @DisplayName("周三之后下一个交易日为周四")
        void afterWednesdayReturnsThursday() {
            LocalDate wednesday = LocalDate.of(2024, 1, 17); // 周三
            LocalDate next = calendar.getNextTradingDay(wednesday);
            assertEquals(LocalDate.of(2024, 1, 18), next); // 周四
        }
    }

    @Nested
    @DisplayName("getPreviousTradingDay - 获取上一个交易日")
    class GetPreviousTradingDayTests {

        @Test
        @DisplayName("周一之前上一个交易日为上周五")
        void beforeMondayReturnsFriday() {
            LocalDate monday = LocalDate.of(2024, 1, 15); // 周一
            LocalDate prev = calendar.getPreviousTradingDay(monday);
            assertEquals(DayOfWeek.FRIDAY, prev.getDayOfWeek());
            assertEquals(LocalDate.of(2024, 1, 12), prev);
        }

        @Test
        @DisplayName("周四之前上一个交易日为周三")
        void beforeThursdayReturnsWednesday() {
            LocalDate thursday = LocalDate.of(2024, 1, 18); // 周四
            LocalDate prev = calendar.getPreviousTradingDay(thursday);
            assertEquals(LocalDate.of(2024, 1, 17), prev); // 周三
        }
    }

    @Nested
    @DisplayName("countTradingDays - 计算交易日数")
    class CountTradingDaysTests {

        @Test
        @DisplayName("一周有5个交易日")
        void oneWeekHasFiveTradingDays() {
            LocalDate monday = LocalDate.of(2024, 1, 15); // 周一
            LocalDate friday = LocalDate.of(2024, 1, 19); // 周五
            assertEquals(5, calendar.countTradingDays(monday, friday));
        }

        @Test
        @DisplayName("含周末的一周有5个交易日")
        void weekWithWeekendHasFiveTradingDays() {
            LocalDate monday = LocalDate.of(2024, 1, 15); // 周一
            LocalDate sunday = LocalDate.of(2024, 1, 21); // 周日
            assertEquals(5, calendar.countTradingDays(monday, sunday));
        }

        @Test
        @DisplayName("单天有1个交易日")
        void singleDayHasOne() {
            LocalDate wednesday = LocalDate.of(2024, 1, 17); // 周三
            assertEquals(1, calendar.countTradingDays(wednesday, wednesday));
        }
    }

    @Nested
    @DisplayName("getRecentTradingDays - 获取最近N个交易日")
    class GetRecentTradingDaysTests {

        @Test
        @DisplayName("获取最近5个交易日")
        void getRecentFiveDays() {
            List<LocalDate> days = calendar.getRecentTradingDays(5);
            assertNotNull(days);
            assertEquals(5, days.size());
            // 每一天都不应是周末
            for (LocalDate day : days) {
                assertTrue(calendar.isTradingDay(day));
            }
        }

        @Test
        @DisplayName("获取最近1个交易日")
        void getRecentOneDay() {
            List<LocalDate> days = calendar.getRecentTradingDays(1);
            assertNotNull(days);
            assertEquals(1, days.size());
        }
    }
}
