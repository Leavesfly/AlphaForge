package io.leavesfly.alphaforge.infrastructure.dataprovider;

import io.leavesfly.alphaforge.config.DataProviderConfig;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 数据源故障切换执行器 — 高可用取数的统一协调者。
 *
 * <p>从 {@code DataFetcherManager} 中抽出的高内聚职责单元，封装：
 * 数据源排序（能力/优先级感知）、熔断器、差异化限流、限流等待与自动重试的故障切换模板。
 * {@code DataFetcherManager} 专注缓存、增量合并、质量校验与 {@code MarketDataPort} 语义，
 * 将上述韧性关切委托给本执行器。</p>
 */
@Component
public class FetcherFailoverExecutor {

    private static final Logger log = LoggerFactory.getLogger(FetcherFailoverExecutor.class);

    /** 单数据源最大重试次数 */
    private static final int MAX_RETRY = 1;
    /** 限流等待最大毫秒数 */
    private static final long RATE_LIMIT_WAIT_MS = 2000;
    /** 随机抖动范围(毫秒)，叠加在限流间隔之上，防止规律性请求被识别 */
    private static final long JITTER_MS = 200;

    private final DataProviderConfig dataProviderConfig;
    private final List<BaseDataFetcher> fetchers;

    /** 熔断器状态: 数据源名称 -> 熔断信息 */
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    /** 限流器状态: 数据源名称 -> 限流器 */
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    /** 滑动窗口限流器: 东财系数据源专用（名称 -> 限流器） */
    private final Map<String, SlidingWindowRateLimiter> slidingWindowLimiters = new ConcurrentHashMap<>();

    public FetcherFailoverExecutor(List<BaseDataFetcher> fetchers, DataProviderConfig dataProviderConfig) {
        this.fetchers = fetchers;
        this.dataProviderConfig = dataProviderConfig;
    }

    /** 已注册数据源数量 */
    public int fetcherCount() {
        return fetchers != null ? fetchers.size() : 0;
    }

    // ==================== 通用故障切换模板 ====================

    /**
     * 通用数据获取模板 — 统一封装熔断器 + 限流等待 + 自动重试 + 故障切换
     *
     * 高可用策略：
     * 1. 限流器返回 false 时等待而非跳过（最多等 2s）
     * 2. 单数据源异常时重试 1 次再切换
     * 3. 熔断器状态下跳过该数据源
     * 4. 所有数据源失败时返回空默认值
     */
    public <T> T executeWithFailover(String stockCode,
                                     Function<BaseDataFetcher, T> fetcherCall,
                                     Predicate<T> isEmpty,
                                     T emptyDefault) {
        return executeWithFailover(MarketType.detectFromCode(stockCode), fetcherCall, isEmpty, emptyDefault);
    }

    /** 通用数据获取模板（显式指定市场类型） */
    public <T> T executeWithFailover(MarketType market,
                                     Function<BaseDataFetcher, T> fetcherCall,
                                     Predicate<T> isEmpty,
                                     T emptyDefault) {
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) {
                log.debug("数据源 {} 熔断中，跳过", fetcherName);
                continue;
            }
            // 限流等待：tryAcquire 返回 false 时短暂等待重试，而非直接跳过
            if (!tryAcquire(fetcher)) {
                if (!waitForRateLimit(fetcher, RATE_LIMIT_WAIT_MS)) {
                    log.debug("数据源 {} 限流等待超时，切换到下一个数据源", fetcherName);
                    continue;
                }
            }
            // 调用 + 重试
            for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
                try {
                    T result = fetcherCall.apply(fetcher);
                    if (!isEmpty.test(result)) {
                        recordSuccess(fetcherName);
                        return result;
                    }
                    // 空结果不算失败，直接切换
                    break;
                } catch (Exception e) {
                    log.warn("数据源 {} 获取数据失败(尝试 {}/{}): {}", fetcherName, attempt + 1, MAX_RETRY + 1, e.getMessage());
                    if (attempt < MAX_RETRY) {
                        sleepQuiet(fetcher.getRateLimitMs());
                    } else {
                        recordFailure(fetcherName);
                    }
                }
            }
        }
        return emptyDefault;
    }

    /** 通用数据获取模板（无 stockCode 版本，用于北向资金等全局数据） */
    public <T> T executeWithFailoverNoStock(Function<BaseDataFetcher, T> fetcherCall,
                                            Predicate<T> isEmpty,
                                            T emptyDefault) {
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(MarketType.A);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) {
                if (!waitForRateLimit(fetcher, RATE_LIMIT_WAIT_MS)) continue;
            }
            for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
                try {
                    T result = fetcherCall.apply(fetcher);
                    if (!isEmpty.test(result)) {
                        recordSuccess(fetcherName);
                        return result;
                    }
                    break;
                } catch (Exception e) {
                    log.warn("数据源 {} 获取数据失败(尝试 {}/{}): {}", fetcherName, attempt + 1, MAX_RETRY + 1, e.getMessage());
                    if (attempt < MAX_RETRY) {
                        sleepQuiet(fetcher.getRateLimitMs());
                    } else {
                        recordFailure(fetcherName);
                    }
                }
            }
        }
        return emptyDefault;
    }

    /**
     * 限流等待 — 在指定时间内循环尝试获取许可
     * @return true=获取成功，false=超时
     */
    public boolean waitForRateLimit(BaseDataFetcher fetcher, long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            sleepQuiet(Math.min(fetcher.getRateLimitMs(), 200));
            if (tryAcquire(fetcher)) return true;
        }
        return false;
    }

    /** 限流等待（使用默认最大等待时间 {@link #RATE_LIMIT_WAIT_MS}） */
    public boolean waitForRateLimit(BaseDataFetcher fetcher) {
        return waitForRateLimit(fetcher, RATE_LIMIT_WAIT_MS);
    }

    /** 静默睡眠 */
    public void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ==================== 数据源排序 ====================

    public List<BaseDataFetcher> getOrderedFetchers(MarketType market) {
        // 如果配置了指定数据源
        String configProvider = dataProviderConfig.getDataProvider();
        if (!"auto".equalsIgnoreCase(configProvider)) {
            return fetchers.stream()
                    .filter(f -> f.getName().equalsIgnoreCase(configProvider))
                    .findFirst()
                    .map(List::of)
                    .orElse(fetchers);
        }

        // 按优先级排序 + 能力感知过滤（仅保留支持当前市场的数据源）
        List<BaseDataFetcher> sorted = new ArrayList<>(fetchers);
        sorted.removeIf(f -> !f.getSupportedMarkets().contains(market));
        sorted.sort(Comparator.comparingInt(BaseDataFetcher::getPriority));

        if (sorted.isEmpty()) {
            // 降级：如果按市场过滤后为空，回退到全部数据源
            log.warn("无数据源支持市场 {}，回退到全部数据源", market);
            sorted = new ArrayList<>(fetchers);
            sorted.sort(Comparator.comparingInt(BaseDataFetcher::getPriority));
        }
        return sorted;
    }

    // ========== 熔断器逻辑 ==========

    public boolean isCircuitOpen(String fetcherName) {
        CircuitBreaker cb = circuitBreakers.get(fetcherName);
        if (cb == null) return false;

        // 检查是否到了恢复时间
        if (cb.isOpen() && System.currentTimeMillis() > cb.getRecoveryTime()) {
            cb.halfOpen();
            return false;
        }
        return cb.isOpen();
    }

    /** 记录成功调用 */
    public void recordSuccess(String fetcherName) {
        CircuitBreaker cb = circuitBreakers.get(fetcherName);
        if (cb != null) {
            cb.recordSuccess();
        }
    }

    /** 记录失败调用 */
    public void recordFailure(String fetcherName) {
        CircuitBreaker cb = circuitBreakers.computeIfAbsent(fetcherName, k -> new CircuitBreaker());
        cb.recordFailure();

        if (cb.getFailureCount() >= 3) {
            cb.open();
            log.warn("数据源 {} 触发熔断, 将在 {}秒 后恢复", fetcherName, cb.getBackoffSeconds());
        }
    }

    // ========== 限流器逻辑 ==========

    /**
     * 尝试获取请求许可（差异化限流）
     * - 东财系数据源（名称含 efinance）：使用滑动窗口限流器（1分钟≤180次/5分钟≤280次）
     * - 其他数据源：使用简单限流器（单次间隔 + 随机抖动）
     */
    public boolean tryAcquire(BaseDataFetcher fetcher) {
        String fetcherName = fetcher.getName();
        long rateLimitMs = fetcher.getRateLimitMs();

        // 东财系数据源使用滑动窗口限流器
        if (fetcherName != null && fetcherName.toLowerCase().contains("efinance")) {
            SlidingWindowRateLimiter swLimiter = slidingWindowLimiters.computeIfAbsent(
                    fetcherName, k -> new SlidingWindowRateLimiter(rateLimitMs));
            if (swLimiter.getMinIntervalMs() != rateLimitMs) {
                swLimiter = new SlidingWindowRateLimiter(rateLimitMs);
                slidingWindowLimiters.put(fetcherName, swLimiter);
            }
            return swLimiter.tryAcquire();
        }

        // 其他数据源使用简单限流器
        RateLimiter limiter = rateLimiters.computeIfAbsent(fetcherName, k -> new RateLimiter(rateLimitMs, JITTER_MS));
        if (limiter.getMinIntervalMs() != rateLimitMs) {
            limiter = new RateLimiter(rateLimitMs, JITTER_MS);
            rateLimiters.put(fetcherName, limiter);
        }
        return limiter.tryAcquire();
    }
}
