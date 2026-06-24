package io.leavesfly.stock.config;

import io.leavesfly.stock.domain.model.enums.MarketType;
import io.leavesfly.stock.domain.model.enums.NotificationChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置与枚举测试
 */
class ConfigAndEnumTest {

    // ===== MarketType 测试 =====

    @Test
    @DisplayName("MarketType: 6位数字识别为A股")
    void testAShareDetection() {
        Assertions.assertEquals(MarketType.A, MarketType.detectFromCode("600519"));
        assertEquals(MarketType.A, MarketType.detectFromCode("000001"));
        assertEquals(MarketType.A, MarketType.detectFromCode("300750"));
        assertEquals(MarketType.A, MarketType.detectFromCode("688001"));
    }

    @Test
    @DisplayName("MarketType: HK前缀识别为港股")
    void testHKDetection() {
        assertEquals(MarketType.HK, MarketType.detectFromCode("HK00700"));
        assertEquals(MarketType.HK, MarketType.detectFromCode("hk09988"));
    }

    @Test
    @DisplayName("MarketType: 纯字母识别为美股")
    void testUSDetection() {
        assertEquals(MarketType.US, MarketType.detectFromCode("AAPL"));
        assertEquals(MarketType.US, MarketType.detectFromCode("MSFT"));
        assertEquals(MarketType.US, MarketType.detectFromCode("GOOG"));
    }

    @Test
    @DisplayName("MarketType: 空值返回A股(默认)")
    void testNullDefaults() {
        assertEquals(MarketType.A, MarketType.detectFromCode(null));
        assertEquals(MarketType.A, MarketType.detectFromCode(""));
    }

    @Test
    @DisplayName("MarketType: fromCode映射")
    void testFromCode() {
        assertEquals(MarketType.A, MarketType.fromCode("A"));
        assertEquals(MarketType.HK, MarketType.fromCode("HK"));
        assertEquals(MarketType.US, MarketType.fromCode("US"));
    }

    // ===== NotificationChannel 测试 =====

    @Test
    @DisplayName("NotificationChannel: 全部13个渠道")
    void testAllChannels() {
        Assertions.assertEquals(13, NotificationChannel.values().length);
    }

    @Test
    @DisplayName("NotificationChannel: fromCode正确映射")
    void testChannelFromCode() {
        assertEquals(NotificationChannel.WECOM, NotificationChannel.fromCode("wecom"));
        assertEquals(NotificationChannel.TELEGRAM, NotificationChannel.fromCode("telegram"));
        assertEquals(NotificationChannel.FEISHU, NotificationChannel.fromCode("feishu"));
        assertEquals(NotificationChannel.SLACK, NotificationChannel.fromCode("slack"));
    }

    @Test
    @DisplayName("NotificationChannel: 不存在的code返回null")
    void testChannelNotFound() {
        assertNull(NotificationChannel.fromCode("nonexistent"));
    }

    @Test
    @DisplayName("NotificationChannel: 大小写不敏感")
    void testChannelCaseInsensitive() {
        assertEquals(NotificationChannel.WECOM, NotificationChannel.fromCode("WECOM"));
        assertEquals(NotificationChannel.TELEGRAM, NotificationChannel.fromCode("Telegram"));
    }
}
