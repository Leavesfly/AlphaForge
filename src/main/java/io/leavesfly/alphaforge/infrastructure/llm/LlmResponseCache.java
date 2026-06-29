package io.leavesfly.alphaforge.infrastructure.llm;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * LLM 响应缓存 — 短期缓存相同请求的结果，降低 Token 消耗
 *
 * 缓存策略：
 * - TTL: 5 分钟（可配置）
 * - 最大条目: 200
 * - Key: model + messages 内容哈希
 * - 仅缓存纯文本对话（不含工具调用的结果）
 *
 * 适用场景：
 * - 定时分析中短时间内重复请求相同股票
 * - 用户连续提问相似问题
 *
 * 不缓存场景：
 * - 流式调用（streamChatWithMessages）
 * - 含工具调用的结果（可能有副作用）
 * - 结构化输出（chatForStructuredOutput，不同 schema 结果不同）
 */
@Component
public class LlmResponseCache {

    private static final Logger log = LoggerFactory.getLogger(LlmResponseCache.class);

    private final Cache<String, String> cache;
    private final boolean enabled;

    public LlmResponseCache() {
        this(true, 5, TimeUnit.MINUTES, 200);
    }

    /**
     * @param enabled       是否启用缓存
     * @param duration      TTL 时长
     * @param unit          TTL 时间单位
     * @param maxSize       最大缓存条目数
     */
    public LlmResponseCache(boolean enabled, long duration, TimeUnit unit, int maxSize) {
        this.enabled = enabled;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(duration, unit)
                .maximumSize(maxSize)
                .recordStats()
                .build();
        log.info("LlmResponseCache 初始化: enabled={}, ttl={}{}", enabled, duration, unit);
    }

    /**
     * 生成缓存 Key
     *
     * @param model    模型名称
     * @param messages 消息列表
     * @return 缓存 key，null 表示不应缓存
     */
    public String buildKey(String model, List<Map<String, String>> messages) {
        if (!enabled || messages == null || messages.isEmpty()) {
            return null;
        }
        // 使用 model + messages 内容的哈希作为 key
        int hash = Objects.hash(model, messages.toString());
        return model + ":" + hash;
    }

    /**
     * 查询缓存
     *
     * @param key 缓存 key
     * @return 缓存的结果，null 表示未命中
     */
    public String get(String key) {
        if (!enabled || key == null) return null;
        String cached = cache.getIfPresent(key);
        if (cached != null) {
            log.debug("LLM缓存命中: key={}", key);
        }
        return cached;
    }

    /**
     * 写入缓存
     *
     * @param key    缓存 key
     * @param result 要缓存的结果
     */
    public void put(String key, String result) {
        if (!enabled || key == null || result == null || result.isEmpty()) return;
        // 不缓存错误消息
        if (result.startsWith("[错误]") || result.startsWith("Error")) return;
        cache.put(key, result);
        log.debug("LLM缓存写入: key={}", key);
    }

    /** 是否启用缓存 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 获取缓存统计信息 */
    public Map<String, Object> getStats() {
        return Map.of(
                "size", cache.size(),
                "hitCount", cache.stats().hitCount(),
                "missCount", cache.stats().missCount(),
                "hitRate", cache.stats().hitRate(),
                "evictionCount", cache.stats().evictionCount()
        );
    }

    /** 清空缓存 */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("LLM缓存已清空");
    }
}
