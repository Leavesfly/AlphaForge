package io.leavesfly.stock.infrastructure.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.leavesfly.stock.domain.model.enums.NotificationChannel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationRouter 通知路由器测试")
class NotificationRouterTest {

    @Mock
    private NotificationDedupStore dedupStore;

    private NotificationRouter router;

    @BeforeEach
    void setUp() {
        when(dedupStore.recentlySent(anyString(), anyInt())).thenReturn(false);
        router = new NotificationRouter(dedupStore);
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
            assertEquals(4, routed.size());
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
        @DisplayName("null内容返回空字符串")
        void nullContentReturnsEmpty() {
            String adapted = router.adaptContent(null, NotificationChannel.WECOM);
            assertEquals("", adapted);
        }
    }
}
