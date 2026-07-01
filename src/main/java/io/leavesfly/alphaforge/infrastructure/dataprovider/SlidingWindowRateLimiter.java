package io.leavesfly.alphaforge.infrastructure.dataprovider;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 滑动窗口限流器 — 基于1分钟/5分钟窗口的请求总量控制
 *
 * 东财风控阈值（社区实测）：
 * - 每秒 > 5 次 → 高风险
 * - 1分钟 ≥ 200 次 → 中高风险
 * - 5分钟 ≥ 300 次 → 触发封禁
 *
 * 本限流器在单次间隔限流之上叠加滑动窗口计数，
 * 确保在批量场景下不触发东财的风控阈值。
 */
public class SlidingWindowRateLimiter {

    private final long minIntervalMs;
    private final int maxPerMinute;
    private final int maxPerFiveMinutes;

    private volatile long lastRequestTime = 0;
    private final ConcurrentLinkedDeque<Long> requestTimestamps = new ConcurrentLinkedDeque<>();

    /** 默认东财风控阈值 */
    public SlidingWindowRateLimiter(long minIntervalMs) {
        this(minIntervalMs, 180, 280);
    }

    public SlidingWindowRateLimiter(long minIntervalMs, int maxPerMinute, int maxPerFiveMinutes) {
        this.minIntervalMs = minIntervalMs;
        this.maxPerMinute = maxPerMinute;
        this.maxPerFiveMinutes = maxPerFiveMinutes;
    }

    /**
     * 尝试获取请求许可
     * 同时检查：单次间隔 + 1分钟窗口 + 5分钟窗口
     */
    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();

        // 1. 单次间隔检查
        if (now - lastRequestTime < minIntervalMs) {
            return false;
        }

        // 2. 清理过期时间戳
        long oneMinuteAgo = now - 60_000L;
        long fiveMinutesAgo = now - 300_000L;

        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() < fiveMinutesAgo) {
            requestTimestamps.pollFirst();
        }

        // 3. 1分钟窗口检查
        int countInMinute = 0;
        for (Long ts : requestTimestamps) {
            if (ts >= oneMinuteAgo) countInMinute++;
        }
        if (countInMinute >= maxPerMinute) {
            return false;
        }

        // 4. 5分钟窗口检查
        if (requestTimestamps.size() >= maxPerFiveMinutes) {
            return false;
        }

        // 5. 通过所有检查，记录请求
        lastRequestTime = now;
        requestTimestamps.addLast(now);
        return true;
    }

    public long getMinIntervalMs() {
        return minIntervalMs;
    }
}
