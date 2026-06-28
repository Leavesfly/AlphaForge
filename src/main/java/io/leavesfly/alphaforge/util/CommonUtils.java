package io.leavesfly.alphaforge.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.Map;

/**
 * 通用工具类
 */
public class CommonUtils {

    private static final Logger log = LoggerFactory.getLogger(CommonUtils.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 对象转JSON字符串
     */
    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * JSON字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            log.error("JSON反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JSON字符串转JsonNode
     */
    public static JsonNode parseJson(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 格式化JSON(美化输出)
     */
    public static String prettyJson(Object obj) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    /**
     * 计算MD5
     */
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 安全获取Map中的字符串值
     */
    public static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 安全获取Map中的数值
     */
    public static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        if (map == null) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 重试执行(指数退避)
     */
    public static <T> T retryWithBackoff(RetryableTask<T> task, int maxRetries, long initialDelayMs) {
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return task.execute();
            } catch (Exception e) {
                if (i == maxRetries) {
                    log.error("重试 {} 次后仍然失败: {}", maxRetries, e.getMessage());
                    throw new RuntimeException("重试失败", e);
                }
                long delay = initialDelayMs * (long) Math.pow(2, i);
                log.warn("第 {} 次重试，等待 {}ms: {}", i + 1, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
            }
        }
        return null;
    }

    /**
     * 可重试任务接口
     */
    @FunctionalInterface
    public interface RetryableTask<T> {
        T execute() throws Exception;
    }
}
