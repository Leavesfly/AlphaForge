package io.leavesfly.alphaforge.infrastructure.dataprovider;

/**
 * TTL缓存条目 — 带过期时间的内存缓存
 * 泛型化以支持 List&lt;Map&gt; 和单个 Map 等不同数据类型
 */
public class TtlCacheEntry<T> {

    private final T value;
    private final long expiryTime;

    public TtlCacheEntry(T value, long expiryTime) {
        this.value = value;
        this.expiryTime = expiryTime;
    }

    public T getValue() { return value; }

    public boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
}
