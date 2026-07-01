package io.leavesfly.alphaforge.infrastructure.dataprovider;

import java.util.List;
import java.util.Map;

/**
 * TTL缓存条目 — 带过期时间的内存缓存
 */
public class TtlCacheEntry {

    private final List<Map<String, Object>> value;
    private final long expiryTime;

    public TtlCacheEntry(List<Map<String, Object>> value, long expiryTime) {
        this.value = value;
        this.expiryTime = expiryTime;
    }

    public List<Map<String, Object>> getValue() { return value; }

    public boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
}
