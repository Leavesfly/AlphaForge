package io.leavesfly.stock.domain.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotificationChannel 通知渠道枚举测试")
class NotificationChannelTest {

    @Nested
    @DisplayName("fromCode - 根据code获取枚举")
    class FromCodeTests {

        @ParameterizedTest
        @CsvSource({
            "wecom, WECOM",
            "feishu, FEISHU",
            "dingtalk, DINGTALK",
            "email, EMAIL"
        })
        @DisplayName("已知code正确返回对应枚举")
        void knownCodesReturnCorrectEnum(String code, String enumName) {
            NotificationChannel expected = NotificationChannel.valueOf(enumName);
            assertEquals(expected, NotificationChannel.fromCode(code));
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            assertEquals(NotificationChannel.WECOM, NotificationChannel.fromCode("WECOM"));
            assertEquals(NotificationChannel.FEISHU, NotificationChannel.fromCode("Feishu"));
            assertEquals(NotificationChannel.DINGTALK, NotificationChannel.fromCode("DingTalk"));
        }

        @Test
        @DisplayName("未知code返回null")
        void unknownCodeReturnsNull() {
            assertNull(NotificationChannel.fromCode("unknown_channel"));
        }
    }

    @Nested
    @DisplayName("枚举属性验证")
    class EnumPropertyTests {

        @Test
        @DisplayName("getCode返回正确值")
        void getCodeValues() {
            assertEquals("wecom", NotificationChannel.WECOM.getCode());
            assertEquals("feishu", NotificationChannel.FEISHU.getCode());
            assertEquals("dingtalk", NotificationChannel.DINGTALK.getCode());
            assertEquals("email", NotificationChannel.EMAIL.getCode());
        }

        @Test
        @DisplayName("getName返回中文名")
        void getNameValues() {
            assertEquals("企业微信", NotificationChannel.WECOM.getName());
            assertEquals("飞书", NotificationChannel.FEISHU.getName());
            assertEquals("钉钉", NotificationChannel.DINGTALK.getName());
            assertEquals("邮件", NotificationChannel.EMAIL.getName());
        }
    }
}
