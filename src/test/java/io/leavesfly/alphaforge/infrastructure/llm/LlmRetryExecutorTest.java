package io.leavesfly.alphaforge.infrastructure.llm;

import io.leavesfly.alphaforge.domain.service.exception.LlmException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlmRetryExecutor 单元测试
 */
class LlmRetryExecutorTest {

    private final LlmRetryExecutor executor = new LlmRetryExecutor(2, 10, 2.0, 1000, 0.0);

    @Test
    @DisplayName("首次成功不重试")
    void testSuccessOnFirstAttempt() {
        AtomicInteger calls = new AtomicInteger(0);
        String result = executor.executeWithRetry(() -> {
            calls.incrementAndGet();
            return "ok";
        }, "test");
        assertEquals("ok", result);
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("可重试异常后成功")
    void testRetryThenSuccess() {
        AtomicInteger calls = new AtomicInteger(0);
        String result = executor.executeWithRetry(() -> {
            if (calls.incrementAndGet() < 2) {
                throw new LlmException.LlmTimeoutException("timeout", "test-model");
            }
            return "ok";
        }, "test");
        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("不可重试异常立即抛出")
    void testNonRetryableException() {
        AtomicInteger calls = new AtomicInteger(0);
        assertThrows(LlmException.LlmAuthException.class, () -> {
            executor.executeWithRetry(() -> {
                calls.incrementAndGet();
                throw new LlmException.LlmAuthException("auth failed", "test-model");
            }, "test");
        });
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("超过最大重试次数后抛出")
    void testMaxRetriesExceeded() {
        AtomicInteger calls = new AtomicInteger(0);
        assertThrows(LlmException.LlmTimeoutException.class, () -> {
            executor.executeWithRetry(() -> {
                calls.incrementAndGet();
                throw new LlmException.LlmTimeoutException("timeout", "test-model");
            }, "test");
        });
        assertEquals(3, calls.get()); // 1 initial + 2 retries
    }

    @Test
    @DisplayName("限流异常使用 Retry-After 延迟")
    void testRateLimitWithRetryAfter() {
        LlmRetryExecutor fastExecutor = new LlmRetryExecutor(1, 10, 2.0, 5000, 0.0);
        AtomicInteger calls = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        assertThrows(LlmException.LlmRateLimitException.class, () -> {
            fastExecutor.executeWithRetry(() -> {
                calls.incrementAndGet();
                throw new LlmException.LlmRateLimitException("rate limited", "model", 50);
            }, "test");
        });

        long elapsed = System.currentTimeMillis() - startTime;
        assertEquals(2, calls.get()); // 1 initial + 1 retry
        assertTrue(elapsed >= 40, "应该等待至少 40ms (Retry-After=50ms 但受 maxDelay 限制)");
    }
}
