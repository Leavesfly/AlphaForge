package io.leavesfly.alphaforge.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NameToCodeResolver 股票名称解析器测试")
class NameToCodeResolverTest {

    private NameToCodeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new NameToCodeResolver();
    }

    @Nested
    @DisplayName("resolve - 解析股票名称")
    class ResolveTests {

        @Test
        @DisplayName("茅台解析为600519")
        void resolveMaotai() {
            assertEquals("600519", resolver.resolve("茅台"));
        }

        @Test
        @DisplayName("贵州茅台解析为600519")
        void resolveGuizhouMaotai() {
            assertEquals("600519", resolver.resolve("贵州茅台"));
        }

        @Test
        @DisplayName("腾讯解析为港股代码")
        void resolveTencent() {
            assertEquals("hk00700", resolver.resolve("腾讯"));
        }

        @Test
        @DisplayName("苹果解析为AAPL")
        void resolveApple() {
            assertEquals("AAPL", resolver.resolve("苹果"));
        }

        @Test
        @DisplayName("标准A股代码直接返回")
        void standardAShareCodeReturnedDirectly() {
            assertEquals("600519", resolver.resolve("600519"));
        }

        @Test
        @DisplayName("标准美股代码直接返回")
        void standardUSCodeReturnedDirectly() {
            assertEquals("AAPL", resolver.resolve("AAPL"));
        }

        @Test
        @DisplayName("标准港股代码直接返回")
        void standardHKCodeReturnedDirectly() {
            assertEquals("hk00700", resolver.resolve("hk00700"));
        }

        @Test
        @DisplayName("模糊匹配包含关系")
        void fuzzyMatch() {
            // 输入是别名的子串
            assertEquals("600519", resolver.resolve("茅台"));
        }

        @Test
        @DisplayName("无法解析返回原始输入")
        void unresolvableReturnsInput() {
            String input = "某不存在的股票";
            assertEquals(input, resolver.resolve(input));
        }

        @Test
        @DisplayName("null输入返回null")
        void nullInputReturnsNull() {
            assertNull(resolver.resolve(null));
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void emptyInputReturnsEmpty() {
            assertEquals("", resolver.resolve(""));
        }

        @Test
        @DisplayName("前后空格被trim")
        void trimsInput() {
            assertEquals("600519", resolver.resolve("  茅台  "));
        }

        @Test
        @DisplayName("华为返回未上市")
        void huaweiReturnsNotListed() {
            assertEquals("未上市", resolver.resolve("华为"));
        }
    }

    @Nested
    @DisplayName("resolveBatch - 批量解析")
    class ResolveBatchTests {

        @Test
        @DisplayName("逗号分隔批量解析")
        void commaSeparated() {
            List<String> codes = resolver.resolveBatch("茅台,苹果,腾讯");
            assertEquals(3, codes.size());
            assertEquals("600519", codes.get(0));
            assertEquals("AAPL", codes.get(1));
            assertEquals("hk00700", codes.get(2));
        }

        @Test
        @DisplayName("中文逗号分隔批量解析")
        void chineseCommaSeparated() {
            List<String> codes = resolver.resolveBatch("茅台，苹果，腾讯");
            assertEquals(3, codes.size());
        }

        @Test
        @DisplayName("顿号分隔批量解析")
        void dunhaoSeparated() {
            List<String> codes = resolver.resolveBatch("茅台、苹果、腾讯");
            assertEquals(3, codes.size());
        }

        @Test
        @DisplayName("空格分隔批量解析")
        void spaceSeparated() {
            List<String> codes = resolver.resolveBatch("茅台 苹果 腾讯");
            assertEquals(3, codes.size());
        }

        @Test
        @DisplayName("混合分隔符批量解析")
        void mixedSeparators() {
            List<String> codes = resolver.resolveBatch("茅台, 苹果、腾讯 百度");
            assertEquals(4, codes.size());
            assertEquals("600519", codes.get(0));
            assertEquals("AAPL", codes.get(1));
            assertEquals("hk00700", codes.get(2));
            assertEquals("BIDU", codes.get(3));
        }

        @Test
        @DisplayName("单个输入返回单元素列表")
        void singleInputReturnsSingleElement() {
            List<String> codes = resolver.resolveBatch("茅台");
            assertEquals(1, codes.size());
            assertEquals("600519", codes.get(0));
        }

        @Test
        @DisplayName("空字符串返回空列表")
        void emptyStringReturnsEmptyList() {
            List<String> codes = resolver.resolveBatch("");
            assertTrue(codes.isEmpty());
        }
    }

    @Nested
    @DisplayName("isValidCode - 验证代码格式")
    class IsValidCodeTests {

        @Test
        @DisplayName("6位数字是有效A股代码")
        void validAShareCode() {
            assertTrue(resolver.isValidCode("600519"));
        }

        @Test
        @DisplayName("1-5位大写字母是有效美股代码")
        void validUSCode() {
            assertTrue(resolver.isValidCode("AAPL"));
            assertTrue(resolver.isValidCode("TSLA"));
            assertTrue(resolver.isValidCode("A"));
        }

        @Test
        @DisplayName("hk+4-5位数字是有效港股代码")
        void validHKCode() {
            assertTrue(resolver.isValidCode("hk00700"));
            assertTrue(resolver.isValidCode("hk0070"));
        }

        @Test
        @DisplayName("6位字母不是有效代码")
        void sixLettersInvalid() {
            assertFalse(resolver.isValidCode("ABCDEF"));
        }

        @Test
        @DisplayName("null不是有效代码")
        void nullInvalid() {
            assertFalse(resolver.isValidCode(null));
        }

        @Test
        @DisplayName("空字符串不是有效代码")
        void emptyInvalid() {
            assertFalse(resolver.isValidCode(""));
        }

        @Test
        @DisplayName("带小写的hk前缀也有效")
        void lowercaseHKPrefixValid() {
            assertTrue(resolver.isValidCode("HK00700"));
        }
    }
}
