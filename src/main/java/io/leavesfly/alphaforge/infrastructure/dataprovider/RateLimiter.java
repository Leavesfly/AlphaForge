package io.leavesfly.alphaforge.infrastructure.dataprovider;

/**
 * 差异化限流器 — 基于最小请求间隔 + 随机抖动
 *
 * 确保对同一数据源的请求间隔不小于指定毫秒数 + 随机抖动，
 * 防止高频请求触发封禁，同时避免规律性请求被识别。
 */
public class RateLimiter {

    private volatile long minIntervalMs;
    private final long jitterMs;
    private volatile long lastRequestTime = 0;

    public RateLimiter(long minIntervalMs, long jitterMs) {
        this.minIntervalMs = minIntervalMs;
        this.jitterMs = jitterMs;
    }

    public long getMinIntervalMs() { return minIntervalMs; }

    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long effectiveInterval = minIntervalMs + (long) (Math.random() * jitterMs);
        if (now - lastRequestTime >= effectiveInterval) {
            lastRequestTime = now;
            return true;
        }
        return false;
    }
}
