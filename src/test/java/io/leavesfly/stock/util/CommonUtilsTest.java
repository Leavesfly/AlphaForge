package io.leavesfly.stock.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommonUtils 通用工具类测试")
class CommonUtilsTest {

    // ========== toJson / fromJson ==========

    @Nested
    @DisplayName("toJson & fromJson - JSON序列化与反序列化")
    class JsonTests {

        @Test
        @DisplayName("对象转JSON字符串")
        void toJsonSimpleObject() {
            Map<String, Object> obj = new HashMap<>();
            obj.put("name", "test");
            obj.put("value", 42);
            String json = CommonUtils.toJson(obj);
            assertNotNull(json);
            assertTrue(json.contains("\"name\":\"test\""));
            assertTrue(json.contains("\"value\":42"));
        }

        @Test
        @DisplayName("null对象转JSON返回null字符串")
        void toJsonNull() {
            assertEquals("null", CommonUtils.toJson(null));
        }

        @Test
        @DisplayName("JSON字符串转对象")
        void fromJsonToObj() {
            String json = "{\"name\":\"test\",\"age\":30}";
            TestPerson person = CommonUtils.fromJson(json, TestPerson.class);
            assertNotNull(person);
            assertEquals("test", person.getName());
            assertEquals(30, person.getAge());
        }

        @Test
        @DisplayName("非法JSON返回null")
        void fromJsonInvalid() {
            TestPerson person = CommonUtils.fromJson("not a json", TestPerson.class);
            assertNull(person);
        }

        @Test
        @DisplayName("parseJson返回JsonNode")
        void parseJsonReturnsNode() {
            JsonNode node = CommonUtils.parseJson("{\"key\":\"value\"}");
            assertNotNull(node);
            assertEquals("value", node.get("key").asText());
        }

        @Test
        @DisplayName("parseJson非法字符串返回null")
        void parseJsonInvalid() {
            assertNull(CommonUtils.parseJson("not json"));
        }

        @Test
        @DisplayName("prettyJson格式化输出")
        void prettyJsonFormats() {
            Map<String, Object> obj = new HashMap<>();
            obj.put("key", "value");
            String pretty = CommonUtils.prettyJson(obj);
            assertNotNull(pretty);
            assertTrue(pretty.contains("\n"));
        }
    }

    // ========== md5 ==========

    @Nested
    @DisplayName("md5 - MD5哈希计算")
    class Md5Tests {

        @Test
        @DisplayName("已知字符串的MD5值")
        void md5KnownValue() {
            // MD5("hello") = 5d41402abc4b2a76b9719d911017c592
            assertEquals("5d41402abc4b2a76b9719d911017c592", CommonUtils.md5("hello"));
        }

        @Test
        @DisplayName("空字符串的MD5值")
        void md5EmptyString() {
            // MD5("") = d41d8cd98f00b204e9800998ecf8427e
            assertEquals("d41d8cd98f00b204e9800998ecf8427e", CommonUtils.md5(""));
        }

        @Test
        @DisplayName("相同输入相同输出")
        void md5SameInput() {
            String result1 = CommonUtils.md5("test123");
            String result2 = CommonUtils.md5("test123");
            assertEquals(result1, result2);
        }

        @Test
        @DisplayName("不同输入不同输出")
        void md5DifferentInput() {
            assertNotEquals(CommonUtils.md5("abc"), CommonUtils.md5("xyz"));
        }
    }

    // ========== getString / getDouble ==========

    @Nested
    @DisplayName("getString & getDouble - 安全获取Map值")
    class MapAccessTests {

        @Test
        @DisplayName("getString正常获取")
        void getStringNormal() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "hello");
            assertEquals("hello", CommonUtils.getString(map, "name", "default"));
        }

        @Test
        @DisplayName("getString键不存在返回默认值")
        void getStringMissing() {
            Map<String, Object> map = new HashMap<>();
            assertEquals("default", CommonUtils.getString(map, "name", "default"));
        }

        @Test
        @DisplayName("getString null map返回默认值")
        void getStringNullMap() {
            assertEquals("default", CommonUtils.getString(null, "name", "default"));
        }

        @Test
        @DisplayName("getDouble Number类型正常获取")
        void getDoubleFromNumber() {
            Map<String, Object> map = new HashMap<>();
            map.put("price", 3.14);
            assertEquals(3.14, CommonUtils.getDouble(map, "price", 0.0));
        }

        @Test
        @DisplayName("getDouble 字符串数字解析")
        void getDoubleFromString() {
            Map<String, Object> map = new HashMap<>();
            map.put("price", "99.9");
            assertEquals(99.9, CommonUtils.getDouble(map, "price", 0.0));
        }

        @Test
        @DisplayName("getDouble 键不存在返回默认值")
        void getDoubleMissing() {
            Map<String, Object> map = new HashMap<>();
            assertEquals(42.0, CommonUtils.getDouble(map, "price", 42.0));
        }

        @Test
        @DisplayName("getDouble null map返回默认值")
        void getDoubleNullMap() {
            assertEquals(1.0, CommonUtils.getDouble(null, "price", 1.0));
        }
    }

    // ========== retryWithBackoff ==========

    @Nested
    @DisplayName("retryWithBackoff - 重试机制")
    class RetryTests {

        @Test
        @DisplayName("首次成功直接返回")
        void retrySucceedsFirstTime() {
            String result = CommonUtils.retryWithBackoff(() -> "success", 3, 100);
            assertEquals("success", result);
        }

        @Test
        @DisplayName("重试后成功返回")
        void retrySucceedsAfterRetries() {
            int[] attempt = {0};
            String result = CommonUtils.retryWithBackoff(() -> {
                attempt[0]++;
                if (attempt[0] < 3) throw new RuntimeException("fail");
                return "success";
            }, 3, 10);
            assertEquals("success", result);
            assertEquals(3, attempt[0]);
        }

        @Test
        @DisplayName("达到最大重试次数后抛异常")
        void retryThrowsAfterMaxRetries() {
            assertThrows(RuntimeException.class, () ->
                CommonUtils.retryWithBackoff(() -> {
                    throw new RuntimeException("always fail");
                }, 2, 10)
            );
        }
    }

    /** 测试用辅助类 */
    static class TestPerson {
        private String name;
        private int age;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }
}
