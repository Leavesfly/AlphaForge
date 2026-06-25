package io.leavesfly.stock.infrastructure.notification;

import io.leavesfly.stock.domain.model.enums.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotificationRouter 通知路由器测试")
class NotificationRouterTest {

    private NotificationRouter router;

    @BeforeEach
    void setUp() {
        router = new NotificationRouter();
    }

    @Nested
    @DisplayName("shouldSend - 降噪过滤")
    class ShouldSendTests {

        @Test
        @DisplayName("首次发送返回true")
        void firstSendReturnsTrue() {
            assertTrue(router.shouldSend("测试标题", "测试内容"));
        }

        @Test
        @DisplayName("相同内容30分钟内返回false")
        void sameContentWithin30MinsReturnsFalse() {
            assertTrue(router.shouldSend("标题", "内容A"));
            assertFalse(router.shouldSend("标题", "内容A"));
        }

        @Test
        @DisplayName("不同内容都返回true")
        void differentContentReturnsTrue() {
            assertTrue(router.shouldSend("标题", "内容A"));
            assertTrue(router.shouldSend("标题", "内容B"));
        }

        @Test
        @DisplayName("null标题不抛异常")
        void nullTitleDoesNotThrow() {
            assertDoesNotThrow(() -> router.shouldSend(null, "内容"));
        }

        @Test
        @DisplayName("null内容不抛异常")
        void nullContentDoesNotThrow() {
            assertDoesNotThrow(() -> router.shouldSend("标题", null));
        }
    }

    @Nested
    @DisplayName("route - 渠道路由")
    class RouteTests {

        @Test
        @DisplayName("普通消息返回所有渠道")
        void normalMessageReturnsAllChannels() {
            List<NotificationChannel> channels = Arrays.asList(
                NotificationChannel.WECOM, NotificationChannel.FEISHU,
                NotificationChannel.EMAIL, NotificationChannel.DINGTALK
            );
            List<NotificationChannel> routed = router.route("report", channels);
            assertEquals(4, routed.size());
        }

        @Test
        @DisplayName("image消息仅返回支持图片的渠道(EMAIL和FEISHU)")
        void imageMessageReturnsImageSupportingChannels() {
            List<NotificationChannel> channels = Arrays.asList(
                NotificationChannel.WECOM, NotificationChannel.FEISHU,
                NotificationChannel.EMAIL, NotificationChannel.DINGTALK
            );
            List<NotificationChannel> routed = router.route("image", channels);
            assertEquals(2, routed.size());
            assertTrue(routed.contains(NotificationChannel.FEISHU));
            assertTrue(routed.contains(NotificationChannel.EMAIL));
        }

        @Test
        @DisplayName("long_report仅返回支持长文本的渠道(>4000字符)")
        void longReportReturnsLongTextChannels() {
            List<NotificationChannel> channels = Arrays.asList(
                NotificationChannel.WECOM, NotificationChannel.FEISHU,
                NotificationChannel.EMAIL, NotificationChannel.DINGTALK
            );
            List<NotificationChannel> routed = router.route("long_report", channels);
            // WECOM=4096, FEISHU=30000, DINGTALK=20000, EMAIL=100000, 全部>4000
            assertEquals(4, routed.size());
            assertTrue(routed.contains(NotificationChannel.WECOM));
            assertTrue(routed.contains(NotificationChannel.FEISHU));
            assertTrue(routed.contains(NotificationChannel.DINGTALK));
            assertTrue(routed.contains(NotificationChannel.EMAIL));
        }

        @Test
        @DisplayName("null渠道列表返回空列表")
        void nullChannelsReturnsEmpty() {
            List<NotificationChannel> routed = router.route("report", null);
            assertNotNull(routed);
            assertTrue(routed.isEmpty());
        }

        @Test
        @DisplayName("空渠道列表返回空列表")
        void emptyChannelsReturnsEmpty() {
            List<NotificationChannel> routed = router.route("report", Collections.emptyList());
            assertNotNull(routed);
            assertTrue(routed.isEmpty());
        }
    }

    @Nested
    @DisplayName("getChannelCapabilities - 渠道能力")
    class GetChannelCapabilitiesTests {

        @Test
        @DisplayName("企业微信能力")
        void wecomCapabilities() {
            Map<String, Object> caps = router.getChannelCapabilities(NotificationChannel.WECOM);
            assertNotNull(caps);
            assertEquals("wecom", caps.get("channel"));
            assertEquals(true, caps.get("supports_markdown"));
            assertEquals(false, caps.get("supports_image"));
            assertEquals(4096, caps.get("max_length"));
            assertEquals(true, caps.get("supports_card"));
        }

        @Test
        @DisplayName("飞书能力")
        void feishuCapabilities() {
            Map<String, Object> caps = router.getChannelCapabilities(NotificationChannel.FEISHU);
            assertNotNull(caps);
            assertEquals("feishu", caps.get("channel"));
            assertEquals(true, caps.get("supports_markdown"));
            assertEquals(true, caps.get("supports_image"));
            assertEquals(30000, caps.get("max_length"));
            assertEquals(true, caps.get("supports_card"));
        }

        @Test
        @DisplayName("钉钉能力")
        void dingtalkCapabilities() {
            Map<String, Object> caps = router.getChannelCapabilities(NotificationChannel.DINGTALK);
            assertNotNull(caps);
            assertEquals("dingtalk", caps.get("channel"));
            assertEquals(true, caps.get("supports_markdown"));
            assertEquals(false, caps.get("supports_image"));
            assertEquals(20000, caps.get("max_length"));
            assertEquals(true, caps.get("supports_card"));
        }

        @Test
        @DisplayName("邮件能力")
        void emailCapabilities() {
            Map<String, Object> caps = router.getChannelCapabilities(NotificationChannel.EMAIL);
            assertNotNull(caps);
            assertEquals("email", caps.get("channel"));
            assertEquals(false, caps.get("supports_markdown"));
            assertEquals(true, caps.get("supports_image"));
            assertEquals(100000, caps.get("max_length"));
            assertEquals(false, caps.get("supports_card"));
        }
    }

    @Nested
    @DisplayName("adaptContent - 内容截断")
    class AdaptContentTests {

        @Test
        @DisplayName("短内容不截断")
        void shortContentNotTruncated() {
            String content = "这是一段简短的通知内容。";
            String adapted = router.adaptContent(content, NotificationChannel.WECOM);
            assertEquals(content, adapted);
        }

        @Test
        @DisplayName("超长内容截断并添加提示")
        void longContentTruncated() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5000; i++) sb.append("A");
            String longContent = sb.toString();
            String adapted = router.adaptContent(longContent, NotificationChannel.WECOM);
            assertTrue(adapted.length() < longContent.length());
            assertTrue(adapted.contains("内容已截断"));
        }

        @Test
        @DisplayName("null内容返回空字符串")
        void nullContentReturnsEmpty() {
            String adapted = router.adaptContent(null, NotificationChannel.WECOM);
            assertEquals("", adapted);
        }

        @Test
        @DisplayName("飞书渠道支持更长的内容")
        void feishuSupportsLongerContent() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5000; i++) sb.append("B");
            String content = sb.toString();
            String wecomAdapted = router.adaptContent(content, NotificationChannel.WECOM);
            String feishuAdapted = router.adaptContent(content, NotificationChannel.FEISHU);
            // 飞书最大30000 > 5000, 不截断
            assertEquals(content, feishuAdapted);
            assertTrue(wecomAdapted.length() < content.length());
        }
    }
}
