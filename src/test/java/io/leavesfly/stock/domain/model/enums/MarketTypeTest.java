package io.leavesfly.stock.domain.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MarketType 市场类型枚举测试")
class MarketTypeTest {

    @Nested
    @DisplayName("detectFromCode - 根据股票代码自动检测市场")
    class DetectFromCodeTests {

        @Test
        @DisplayName("6位数字代码检测为A股")
        void digitsCodeReturnsA() {
            assertEquals(MarketType.A, MarketType.detectFromCode("600519"));
        }

        @Test
        @DisplayName("HK开头检测为港股")
        void hkPrefixReturnsHK() {
            assertEquals(MarketType.HK, MarketType.detectFromCode("hk00700"));
        }

        @Test
        @DisplayName(".HK结尾检测为港股")
        void hkSuffixReturnsHK() {
            assertEquals(MarketType.HK, MarketType.detectFromCode("00700.HK"));
        }

        @Test
        @DisplayName("纯字母代码检测为美股")
        void letterCodeReturnsUS() {
            assertEquals(MarketType.US, MarketType.detectFromCode("AAPL"));
        }

        @Test
        @DisplayName(".T结尾检测为日股")
        void tSuffixReturnsJP() {
            assertEquals(MarketType.JP, MarketType.detectFromCode("7203.T"));
        }

        @Test
        @DisplayName("JP开头检测为日股")
        void jpPrefixReturnsJP() {
            assertEquals(MarketType.JP, MarketType.detectFromCode("JP7203"));
        }

        @Test
        @DisplayName(".KS结尾检测为韩股")
        void ksSuffixReturnsKR() {
            assertEquals(MarketType.KR, MarketType.detectFromCode("005930.KS"));
        }

        @Test
        @DisplayName("KR开头检测为韩股")
        void krPrefixReturnsKR() {
            assertEquals(MarketType.KR, MarketType.detectFromCode("KR005930"));
        }

        @Test
        @DisplayName("null代码默认返回A股")
        void nullCodeReturnsA() {
            assertEquals(MarketType.A, MarketType.detectFromCode(null));
        }

        @Test
        @DisplayName("空字符串默认返回A股")
        void emptyCodeReturnsA() {
            assertEquals(MarketType.A, MarketType.detectFromCode(""));
        }
    }

    @Nested
    @DisplayName("fromCode - 根据code获取枚举")
    class FromCodeTests {

        @Test
        @DisplayName("A返回A股")
        void codeAReturnsA() {
            assertEquals(MarketType.A, MarketType.fromCode("A"));
        }

        @Test
        @DisplayName("HK返回港股")
        void codeHKReturnsHK() {
            assertEquals(MarketType.HK, MarketType.fromCode("HK"));
        }

        @Test
        @DisplayName("US返回美股")
        void codeUSReturnsUS() {
            assertEquals(MarketType.US, MarketType.fromCode("US"));
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            assertEquals(MarketType.A, MarketType.fromCode("a"));
            assertEquals(MarketType.HK, MarketType.fromCode("hk"));
        }

        @Test
        @DisplayName("未知code默认返回A股")
        void unknownCodeReturnsA() {
            assertEquals(MarketType.A, MarketType.fromCode("XX"));
        }
    }

    @Nested
    @DisplayName("枚举属性验证")
    class EnumPropertyTests {

        @Test
        @DisplayName("A股region为CN")
        void aShareRegion() {
            assertEquals("CN", MarketType.A.getRegion());
        }

        @Test
        @DisplayName("港股region为HK")
        void hkRegion() {
            assertEquals("HK", MarketType.HK.getRegion());
        }

        @Test
        @DisplayName("美股region为US")
        void usRegion() {
            assertEquals("US", MarketType.US.getRegion());
        }

        @Test
        @DisplayName("getCode返回正确值")
        void getCodeValues() {
            assertEquals("A", MarketType.A.getCode());
            assertEquals("HK", MarketType.HK.getCode());
            assertEquals("US", MarketType.US.getCode());
        }

        @Test
        @DisplayName("getName返回中文名")
        void getNameValues() {
            assertEquals("A股", MarketType.A.getName());
            assertEquals("港股", MarketType.HK.getName());
            assertEquals("美股", MarketType.US.getName());
        }
    }
}
