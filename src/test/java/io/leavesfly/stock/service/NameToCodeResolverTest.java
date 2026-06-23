package io.leavesfly.stock.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 名称到代码解析器测试
 */
class NameToCodeResolverTest {

    private final NameToCodeResolver resolver = new NameToCodeResolver();

    @Test
    @DisplayName("标准A股代码直接返回")
    void testStandardAShareCode() {
        assertEquals("600519", resolver.resolve("600519"));
        assertEquals("002594", resolver.resolve("002594"));
    }

    @Test
    @DisplayName("标准美股代码直接返回")
    void testStandardUSCode() {
        assertEquals("AAPL", resolver.resolve("AAPL"));
        assertEquals("TSLA", resolver.resolve("TSLA"));
    }

    @Test
    @DisplayName("中文名解析为代码")
    void testChineseNameResolve() {
        assertEquals("600519", resolver.resolve("茅台"));
        assertEquals("600519", resolver.resolve("贵州茅台"));
        assertEquals("hk00700", resolver.resolve("腾讯"));
        assertEquals("AAPL", resolver.resolve("苹果"));
    }

    @Test
    @DisplayName("批量解析: 逗号分隔")
    void testBatchResolve() {
        List<String> codes = resolver.resolveBatch("茅台,AAPL,600036");
        assertEquals(3, codes.size());
        assertEquals("600519", codes.get(0));
        assertEquals("AAPL", codes.get(1));
        assertEquals("600036", codes.get(2));
    }

    @Test
    @DisplayName("批量解析: 中文顿号")
    void testBatchResolveChineseSeparator() {
        List<String> codes = resolver.resolveBatch("腾讯、比亚迪");
        assertEquals(2, codes.size());
    }

    @Test
    @DisplayName("代码格式验证")
    void testIsValidCode() {
        assertTrue(resolver.isValidCode("600519"));
        assertTrue(resolver.isValidCode("AAPL"));
        assertTrue(resolver.isValidCode("hk00700"));
        assertFalse(resolver.isValidCode(""));
        assertFalse(resolver.isValidCode("贵州茅台"));
    }
}
