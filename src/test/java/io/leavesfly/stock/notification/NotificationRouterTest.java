package io.leavesfly.stock.notification;

import io.leavesfly.stock.domain.model.enums.NotificationChannel;
import io.leavesfly.stock.infrastructure.notification.NotificationRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通知路由和降噪测试
 */
class NotificationRouterTest {

    private NotificationRouter router;

    @BeforeEach
    void setUp() {
        router = new NotificationRouter();
    }

    @Test
    @DisplayName("首次发送应通过")
    void testFirstSendAllowed() {
        assertTrue(router.shouldSend("测试标题", "测试内容"));
    }

    @Test
    @DisplayName("重复内容在30分钟内应被拦截")
    void testDuplicateBlocked() {
        router.shouldSend("标题A", "内容A");
        assertFalse(router.shouldSend("标题A", "内容A"));
    }

    @Test
    @DisplayName("不同内容不会互相拦截")
    void testDifferentContentAllowed() {
        router.shouldSend("标题A", "内容A");
        assertTrue(router.shouldSend("标题B", "内容B"));
    }

    @Test
    @DisplayName("频率限制: 每小时最多20条")
    void testHourlyRateLimit() {
        for (int i = 0; i < 20; i++) {
            assertTrue(router.shouldSend("标题" + i, "内容" + i));
        }
        // 第21条应被拦截
        assertFalse(router.shouldSend("超限标题", "超限内容"));
    }

    @Test
    @DisplayName("路由: 图片消息只路由到支持图片的渠道")
    void testRouteImageMessage() {
        List<NotificationChannel> all = List.of(
                NotificationChannel.TELEGRAM, NotificationChannel.WECOM,
                NotificationChannel.DISCORD, NotificationChannel.PUSHPLUS);
        List<NotificationChannel> result = router.route("image", all);
        assertTrue(result.contains(NotificationChannel.TELEGRAM));
        assertTrue(result.contains(NotificationChannel.DISCORD));
        assertFalse(result.contains(NotificationChannel.PUSHPLUS));
    }

    @Test
    @DisplayName("内容截断: 超长内容自动截断")
    void testContentTruncation() {
        String longContent = "a".repeat(10000);
        String adapted = router.adaptContent(longContent, NotificationChannel.DISCORD);
        assertTrue(adapted.length() <= 2000);
        assertTrue(adapted.contains("内容已截断"));
    }

    @Test
    @DisplayName("短内容不截断")
    void testShortContentNotTruncated() {
        String shortContent = "这是一条短消息";
        String adapted = router.adaptContent(shortContent, NotificationChannel.TELEGRAM);
        assertEquals(shortContent, adapted);
    }

    @Test
    @DisplayName("渠道能力查询")
    void testChannelCapabilities() {
        var caps = router.getChannelCapabilities(NotificationChannel.FEISHU);
        assertEquals("feishu", caps.get("channel"));
        assertEquals(true, caps.get("supports_markdown"));
        assertEquals(30000, caps.get("max_length"));
    }
}
