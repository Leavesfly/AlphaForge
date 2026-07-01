package io.leavesfly.alphaforge.infrastructure.dataprovider;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器 — 数据源故障自动隔离与恢复
 *
 * 状态机：CLOSED → OPEN（连续失败≥3次）→ HALF_OPEN（冷却后）→ CLOSED（成功恢复）
 * 指数退避：每次熔断后恢复等待时间翻倍，最大5分钟
 */
public class CircuitBreaker {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private long recoveryTime = 0;
    private int backoffSeconds = 30;

    public boolean isOpen() { return state == State.OPEN; }

    public void open() {
        state = State.OPEN;
        backoffSeconds = Math.min(backoffSeconds * 2, 300); // 指数退避，最大5分钟
        recoveryTime = System.currentTimeMillis() + (backoffSeconds * 1000L);
    }

    public void halfOpen() { state = State.HALF_OPEN; }

    public void recordSuccess() {
        state = State.CLOSED;
        failureCount.set(0);
        backoffSeconds = 30;
    }

    public void recordFailure() { failureCount.incrementAndGet(); }

    public int getFailureCount() { return failureCount.get(); }

    public long getRecoveryTime() { return recoveryTime; }

    public int getBackoffSeconds() { return backoffSeconds; }
}
