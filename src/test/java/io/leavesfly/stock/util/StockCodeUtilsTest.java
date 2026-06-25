package io.leavesfly.stock.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StockCodeUtils 工具类测试")
class StockCodeUtilsTest {

    // ========== normalize ==========

    @Nested
    @DisplayName("normalize - 标准化股票代码")
    class NormalizeTests {

        @Test
        @DisplayName("去除SH前缀")
        void normalizeRemovesShPrefix() {
            assertEquals("600519", StockCodeUtils.normalize("sh600519"));
        }

        @Test
        @DisplayName("去除SZ前缀")
        void normalizeRemovesSzPrefix() {
            assertEquals("000001", StockCodeUtils.normalize("sz000001"));
        }

        @Test
        @DisplayName("大写SH/SZ前缀也应去除")
        void normalizeRemovesUppercasePrefix() {
            assertEquals("600519", StockCodeUtils.normalize("SH600519"));
            assertEquals("000001", StockCodeUtils.normalize("SZ000001"));
        }

        @Test
        @DisplayName("无前缀代码不变")
        void normalizeNoPrefixUnchanged() {
            assertEquals("600519", StockCodeUtils.normalize("600519"));
        }

        @Test
        @DisplayName("null返回空字符串")
        void normalizeNullReturnsEmpty() {
            assertEquals("", StockCodeUtils.normalize(null));
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void normalizeEmptyReturnsEmpty() {
            assertEquals("", StockCodeUtils.normalize(""));
        }

        @Test
        @DisplayName("去除前后空格")
        void normalizeTrimsWhitespace() {
            assertEquals("600519", StockCodeUtils.normalize("  600519  "));
        }
    }

    // ========== isAShare ==========

    @Nested
    @DisplayName("isAShare - 判断A股代码")
    class IsAShareTests {

        @Test
        @DisplayName("6位纯数字为A股")
        void sixDigitsIsAShare() {
            assertTrue(StockCodeUtils.isAShare("600519"));
        }

        @Test
        @DisplayName("带SH前缀也是A股")
        void withShPrefixIsAShare() {
            assertTrue(StockCodeUtils.isAShare("sh600519"));
        }

        @Test
        @DisplayName("带SZ前缀也是A股")
        void withSzPrefixIsAShare() {
            assertTrue(StockCodeUtils.isAShare("sz000001"));
        }

        @Test
        @DisplayName("美股代码不是A股")
        void usCodeIsNotAShare() {
            assertFalse(StockCodeUtils.isAShare("AAPL"));
        }

        @Test
        @DisplayName("null返回false")
        void nullReturnsFalse() {
            assertFalse(StockCodeUtils.isAShare(null));
        }
    }

    // ========== isHKStock ==========

    @Nested
    @DisplayName("isHKStock - 判断港股代码")
    class IsHKStockTests {

        @Test
        @DisplayName("hk开头为港股")
        void startsWithHk() {
            assertTrue(StockCodeUtils.isHKStock("hk00700"));
        }

        @Test
        @DisplayName(".HK结尾为港股")
        void endsWithDotHK() {
            assertTrue(StockCodeUtils.isHKStock("00700.HK"));
        }

        @Test
        @DisplayName("A股代码不是港股")
        void aShareIsNotHK() {
            assertFalse(StockCodeUtils.isHKStock("600519"));
        }

        @Test
        @DisplayName("null返回false")
        void nullReturnsFalse() {
            assertFalse(StockCodeUtils.isHKStock(null));
        }
    }

    // ========== isUSStock ==========

    @Nested
    @DisplayName("isUSStock - 判断美股代码")
    class IsUSStockTests {

        @Test
        @DisplayName("字母代码为美股")
        void letterCodeIsUS() {
            assertTrue(StockCodeUtils.isUSStock("AAPL"));
        }

        @Test
        @DisplayName("5字母以内为美股")
        void fiveLettersIsUS() {
            assertTrue(StockCodeUtils.isUSStock("TSLA"));
        }

        @Test
        @DisplayName("6位数字不是美股")
        void digitsNotUS() {
            assertFalse(StockCodeUtils.isUSStock("600519"));
        }

        @Test
        @DisplayName("null返回false")
        void nullReturnsFalse() {
            assertFalse(StockCodeUtils.isUSStock(null));
        }
    }

    // ========== getExchange ==========

    @Nested
    @DisplayName("getExchange - 获取交易所")
    class GetExchangeTests {

        @Test
        @DisplayName("6开头为上交所SSE")
        void startsWith6ReturnsSSE() {
            assertEquals("SSE", StockCodeUtils.getExchange("600519"));
        }

        @Test
        @DisplayName("9开头为上交所SSE")
        void startsWith9ReturnsSSE() {
            assertEquals("SSE", StockCodeUtils.getExchange("900001"));
        }

        @Test
        @DisplayName("0开头为深交所SZSE")
        void startsWith0ReturnsSZSE() {
            assertEquals("SZSE", StockCodeUtils.getExchange("000001"));
        }

        @Test
        @DisplayName("3开头为深交所SZSE(创业板)")
        void startsWith3ReturnsSZSE() {
            assertEquals("SZSE", StockCodeUtils.getExchange("300750"));
        }

        @Test
        @DisplayName("8开头为北交所BSE")
        void startsWith8ReturnsBSE() {
            assertEquals("BSE", StockCodeUtils.getExchange("830839"));
        }

        @Test
        @DisplayName("4开头为北交所BSE")
        void startsWith4ReturnsBSE() {
            assertEquals("BSE", StockCodeUtils.getExchange("430047"));
        }

        @Test
        @DisplayName("带SH前缀也能正确识别")
        void withPrefixCorrectExchange() {
            assertEquals("SSE", StockCodeUtils.getExchange("sh600519"));
        }

        @Test
        @DisplayName("非6位数字返回UNKNOWN")
        void nonDigitsReturnsUnknown() {
            assertEquals("UNKNOWN", StockCodeUtils.getExchange("AAPL"));
        }
    }

    // ========== formatPrice ==========

    @Nested
    @DisplayName("formatPrice - 格式化价格")
    class FormatPriceTests {

        @Test
        @DisplayName("正常价格保留两位小数")
        void normalPrice() {
            assertEquals("10.50", StockCodeUtils.formatPrice(10.5));
        }

        @Test
        @DisplayName("null返回-")
        void nullReturnsDash() {
            assertEquals("-", StockCodeUtils.formatPrice(null));
        }
    }

    // ========== formatChangePct ==========

    @Nested
    @DisplayName("formatChangePct - 格式化涨跌幅")
    class FormatChangePctTests {

        @Test
        @DisplayName("正涨幅带+号和%")
        void positivePct() {
            assertEquals("+3.50%", StockCodeUtils.formatChangePct(3.5));
        }

        @Test
        @DisplayName("负跌幅带-号和%")
        void negativePct() {
            assertEquals("-2.30%", StockCodeUtils.formatChangePct(-2.3));
        }

        @Test
        @DisplayName("null返回-")
        void nullReturnsDash() {
            assertEquals("-", StockCodeUtils.formatChangePct(null));
        }
    }

    // ========== formatVolume ==========

    @Nested
    @DisplayName("formatVolume - 格式化成交量")
    class FormatVolumeTests {

        @Test
        @DisplayName("亿级别格式化为X亿")
        void yiLevel() {
            assertEquals("1.50亿", StockCodeUtils.formatVolume(150000000L));
        }

        @Test
        @DisplayName("万级别格式化为X万")
        void wanLevel() {
            assertEquals("5.00万", StockCodeUtils.formatVolume(50000L));
        }

        @Test
        @DisplayName("万以下直接显示数字")
        void belowWan() {
            assertEquals("9999", StockCodeUtils.formatVolume(9999L));
        }

        @Test
        @DisplayName("null返回-")
        void nullReturnsDash() {
            assertEquals("-", StockCodeUtils.formatVolume(null));
        }
    }

    // ========== formatAmount ==========

    @Nested
    @DisplayName("formatAmount - 格式化金额")
    class FormatAmountTests {

        @Test
        @DisplayName("亿级别格式化为X亿")
        void yiLevel() {
            assertEquals("2.00亿", StockCodeUtils.formatAmount(200000000.0));
        }

        @Test
        @DisplayName("万级别格式化为X万")
        void wanLevel() {
            assertEquals("3.50万", StockCodeUtils.formatAmount(35000.0));
        }

        @Test
        @DisplayName("万以下保留0位小数")
        void belowWan() {
            assertEquals("5000", StockCodeUtils.formatAmount(5000.0));
        }

        @Test
        @DisplayName("null返回-")
        void nullReturnsDash() {
            assertEquals("-", StockCodeUtils.formatAmount(null));
        }
    }
}
