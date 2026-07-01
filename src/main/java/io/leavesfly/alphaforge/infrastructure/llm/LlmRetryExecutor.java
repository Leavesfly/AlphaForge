package io.leavesfly.alphaforge.infrastructure.llm;

import io.leavesfly.alphaforge.domain.service.exception.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * LLM 调用重试执行器 — 指数退避 + 抖动
 *
 * 策略：
 * - 可重试异常：LlmTimeoutException、LlmRateLimitException、网络 IO 异常
 * - 不可重试异常：LlmAuthException、LlmParseException、LlmUnavailableException
 * - 退避公式：baseDelay * 2^(attempt-1) + jitter（随机抖动，避免惊群效应）
 *
 * 线程安全：无状态，可被多线程共享调用。
 */
public class LlmRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(LlmRetryExecutor.class);

    /** 最大重试次数（不含首次调用） */
    private final int maxRetries;
    /** 初始退避延迟（毫秒） */
    private final long baseDelayMillis;
    /** 退避倍率 */
    private final double backoffMultiplier;
    /** 最大退避延迟上限（毫秒），避免等待过久 */
    private final long maxDelayMillis;
    /** 抖动比例（0.0~1.0），在退避时间上叠加随机抖动 */
    private final double jitterRatio;

    public LlmRetryExecutor() {
        this(2, 1000, 2.0, 16000, 0.3);
    }

    /**
     * @param maxRetries        最大重试次数（不含首次调用）
     * @param baseDelayMillis   初始退避延迟（毫秒）
     * @param backoffMultiplier 退避倍率（每次重试延迟乘以此系数）
     * @param maxDelayMillis    最大退避延迟上限（毫秒）
     * @param jitterRatio       抖动比例（0.0~1.0）
     */
    public LlmRetryExecutor(int maxRetries, long baseDelayMillis,
                            double backoffMultiplier, long maxDelayMillis,
                            double jitterRatio) {
        this.maxRetries = maxRetries;
        this.baseDelayMillis = baseDelayMillis;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMillis = maxDelayMillis;
        this.jitterRatio = jitterRatio;
    }

    /**
     * 执行带重试的调用
     *
     * @param action    要执行的调用（首次 + 重试）
     * @param operationName 操作名称（用于日志）
     * @return 调用结果
     * @throws LlmException 当所有重试均失败时抛出最后一次异常
     */
    public <T> T executeWithRetry(Supplier<T> action, String operationName) {
        LlmException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return action.get();
            } catch (LlmException e) {
                if (!isRetryable(e) || attempt >= maxRetries) {
                    throw e;
                }
                lastException = e;
                long delay = computeDelay(attempt, e);
                log.warn("[{}] 第{}次调用失败（{}），{}ms 后重试（{}/{}）",
                        operationName, attempt + 1, e.getClass().getSimpleName(),
                        delay, attempt + 1, maxRetries);
                sleep(delay);
            }
        }

        // 理论上不会走到这里，但防御性处理
        throw lastException != null ? lastException
                : new LlmException.LlmUnavailableException("重试执行器异常：未知错误");
    }

    /** 判断异常是否值得重试 */
    private boolean isRetryable(LlmException e) {
        if (e instanceof LlmException.LlmTimeoutException) return true;
        if (e instanceof LlmException.LlmRateLimitException) return true;
        // LlmUnavailableException 可能是暂时的网络问题，允许重试
        if (e instanceof LlmException.LlmUnavailableException) return true;
        // 认证错误和解析错误不可重试
        return false;
    }

    /** 计算退避延迟（含抖动） */
    private long computeDelay(int attempt, LlmException e) {
        // 如果是限流异常且服务端返回了 Retry-After，优先使用
        if (e instanceof LlmException.LlmRateLimitException rateLimitEx) {
            long retryAfter = rateLimitEx.getRetryAfterMillis();
            if (retryAfter > 0) {
                return Math.min(retryAfter, maxDelayMillis);
            }
        }

        long baseDelay = (long) (baseDelayMillis * Math.pow(backoffMultiplier, attempt));
        long delay = Math.min(baseDelay, maxDelayMillis);

        // 添加抖动：delay * (1 ± jitterRatio)
        if (jitterRatio > 0) {
            long jitter = (long) (delay * jitterRatio);
            delay = delay - jitter + ThreadLocalRandom.current().nextLong(0, 2 * jitter + 1);
        }

        return Math.max(100, delay);
    }

    /** 线程休眠（响应中断） */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("重试等待被中断", e);
        }
    }

    // ===== Getter =====

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getBaseDelayMillis() {
        return baseDelayMillis;
    }
}
