package io.leavesfly.alphaforge.infrastructure.dataprovider;

import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;

import io.leavesfly.alphaforge.config.DataProviderConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.AdjustType;
import io.leavesfly.alphaforge.domain.model.enums.DataProviderType;
import io.leavesfly.alphaforge.domain.model.enums.KLineFrequency;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import io.leavesfly.alphaforge.domain.service.TradingCalendar;
import io.leavesfly.alphaforge.domain.repository.market.StockDailyDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据源管理器 - 策略模式 + 熔断器 + 限流 + 数据质量校验
 *
 * 功能:
 * 1. 多数据源自动切换
 * 2. 故障熔断与自动恢复
 * 3. 防封禁流控 — 每数据源请求频率控制
 * 4. 指数退避重试
 * 5. 交易日感知缓存 — 区分交易日/非交易日，增量更新
 * 6. 数据质量校验 — 缺失交易日检测 + 异常价格过滤
 */
@Component
public class DataFetcherManager implements MarketDataPort {

    private static final Logger log = LoggerFactory.getLogger(DataFetcherManager.class);

    private final DataProviderConfig dataProviderConfig;
    private final List<BaseDataFetcher> fetchers;
    private final StockDailyDataRepository dailyDataRepo;
    private final TradingCalendar tradingCalendar;
    private final DataQualityValidator qualityValidator;
    
    /** 熔断器状态: 数据源名称 -> 熔断信息 */
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /** 限流器状态: 数据源名称 -> 限流器 */
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    /** 滑动窗口限流器: 东财系数据源专用（名称 -> 限流器） */
    private final Map<String, SlidingWindowRateLimiter> slidingWindowLimiters = new ConcurrentHashMap<>();

    /** TTL缓存: 缓存键 -> 缓存条目 */
    private final Map<String, TtlCacheEntry> cache = new ConcurrentHashMap<>();
    
    /** 随机抖动范围(毫秒)，叠加在限流间隔之上，防止规律性请求被识别 */
    private static final long JITTER_MS = 200;

    // 缓存TTL常量（毫秒）
    private static final long CACHE_TTL_HOUR = 3600_000L;       // 1小时
    private static final long CACHE_TTL_DAY = 86400_000L;        // 24小时

    @Autowired
    public DataFetcherManager(DataProviderConfig dataProviderConfig,
                              List<BaseDataFetcher> fetchers,
                              StockDailyDataRepository dailyDataRepo,
                              @Autowired(required = false) TradingCalendar tradingCalendar,
                              @Autowired(required = false) DataQualityValidator qualityValidator) {
        this.dataProviderConfig = dataProviderConfig;
        this.fetchers = fetchers;
        this.dailyDataRepo = dailyDataRepo;
        this.tradingCalendar = tradingCalendar;
        this.qualityValidator = qualityValidator;
        log.info("数据源管理器初始化完成, 已注册 {} 个数据源, 交易日历: {}, 质量校验: {}",
                fetchers.size(), tradingCalendar != null, qualityValidator != null);
    }

    // 测试用构造器(无Spring环境)
    public DataFetcherManager(List<BaseDataFetcher> fetchers, DataProviderConfig dataProviderConfig) {
        this(dataProviderConfig, fetchers, null, null, null);
    }

    /**
     * 获取历史数据 - 自动故障切换 + 增量更新 + 质量校验
     *
     * 流程:
     * 1. 查缓存 → 缓存有效则直接返回
     * 2. 缓存部分命中 → 仅拉取缺失日期的增量数据，合并后返回
     * 3. 缓存未命中 → 全量拉取，校验质量后写缓存
     *
     * @param stockCode 股票代码
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 日K线数据列表
     */
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        // 1. 先查缓存（交易日感知）
        List<StockDailyData> cached = getFromCache(stockCode, startDate, endDate);
        if (cached != null && !cached.isEmpty() && isCacheComplete(cached, startDate, endDate)) {
            log.debug("缓存完整命中: {} ({} 条数据)", stockCode, cached.size());
            return cached;
        }

        // 2. 增量更新：缓存有部分数据，只拉取缺失部分
        LocalDate fetchStart = startDate;
        List<StockDailyData> baseData = new ArrayList<>();
        if (cached != null && !cached.isEmpty()) {
            baseData.addAll(cached);
            LocalDate maxCachedDate = cached.stream()
                    .map(StockDailyData::getTradeDate)
                    .max(LocalDate::compareTo)
                    .orElse(startDate);
            fetchStart = maxCachedDate.plusDays(1);
            if (!fetchStart.isAfter(endDate)) {
                log.debug("增量拉取: {} 从 {} 到 {} (缓存已有 {} 条)", stockCode, fetchStart, endDate, cached.size());
            } else {
                // 缓存已覆盖全部范围
                return cached;
            }
        }

        // 3. 调用数据源获取数据
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            
            // 检查熔断器状态
            if (isCircuitOpen(fetcherName)) {
                log.debug("数据源 {} 处于熔断状态, 跳过", fetcherName);
                continue;
            }

            // 限流检查
            if (!tryAcquire(fetcher)) {
                log.debug("数据源 {} 限流等待中, 跳过本轮", fetcherName);
                continue;
            }
            
            try {
                log.debug("尝试使用数据源 {} 获取 {} 历史数据", fetcherName, stockCode);
                List<StockDailyData> data = fetcher.getHistoryData(stockCode, fetchStart, endDate);
                
                if (data != null && !data.isEmpty()) {
                    // 记录成功
                    recordSuccess(fetcherName);
                    // 写入缓存
                    saveToCache(data);
                    // 合并增量数据
                    List<StockDailyData> merged = mergeData(baseData, data);
                    // 数据质量校验
                    merged = validateAndFilter(merged, stockCode);
                    log.info("数据源 {} 成功获取 {} 条历史数据: {} (增量:{}, 合并:{})",
                            fetcherName, data.size(), stockCode, fetchStart.isAfter(startDate), merged.size());
                    return merged;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取历史数据失败: {} - {}", fetcherName, stockCode, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        
        // 4. 所有数据源失败时，返回缓存中的部分数据（降级）
        if (!baseData.isEmpty()) {
            log.warn("所有数据源失败, 降级返回缓存数据: {} ({} 条)", stockCode, baseData.size());
            return baseData;
        }
        log.error("所有数据源均无法获取历史数据: {}", stockCode);
        return Collections.emptyList();
    }

    // ==================== 通用故障切换模板 ====================

    /**
     * 通用数据获取模板 — 统一封装熔断器 + 限流 + 故障切换逻辑
     * 消除 17 个方法中的重复代码
     */
    private <T> T executeWithFailover(String stockCode,
                                       java.util.function.Function<BaseDataFetcher, T> fetcherCall,
                                       java.util.function.Predicate<T> isEmpty,
                                       T emptyDefault) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                T result = fetcherCall.apply(fetcher);
                if (!isEmpty.test(result)) {
                    recordSuccess(fetcherName);
                    return result;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取数据失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return emptyDefault;
    }

    /** 通用数据获取模板（无 stockCode 版本，用于北向资金等全局数据） */
    private <T> T executeWithFailoverNoStock(
            java.util.function.Function<BaseDataFetcher, T> fetcherCall,
            java.util.function.Predicate<T> isEmpty,
            T emptyDefault) {
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(MarketType.A);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                T result = fetcherCall.apply(fetcher);
                if (!isEmpty.test(result)) {
                    recordSuccess(fetcherName);
                    return result;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取数据失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return emptyDefault;
    }

    // ==================== 行情数据 ====================

    /** 获取实时行情 */
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        return executeWithFailover(stockCode,
                f -> f.getRealtimeQuote(stockCode),
                r -> r == null || r.isEmpty(),
                Collections.emptyMap());
    }

    /** 获取股票基本信息 */
    public Map<String, Object> getStockInfo(String stockCode) {
        return executeWithFailover(stockCode,
                f -> f.getStockInfo(stockCode),
                r -> r == null || r.isEmpty(),
                Collections.emptyMap());
    }

    /**
     * 获取排序后的数据源列表(根据市场类型和优先级)
     * 能力感知路由：过滤掉不支持当前市场的数据源，避免无效请求
     */
    private List<BaseDataFetcher> getOrderedFetchers(MarketType market) {
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

    // ==================== 板块与分钟数据 ====================

    /** 获取股票所属板块 */
    public List<String> getStockBoards(String stockCode) {
        return executeWithFailover(stockCode,
                f -> f.getStockBoards(stockCode),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    /** 获取分钟级K线数据 */
    public List<Map<String, Object>> getMinuteData(String stockCode, int period, int count) {
        return executeWithFailover(stockCode,
                f -> f.getMinuteData(stockCode, period, count),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    /**
     * 获取多频率K线数据（支持日/周/月/分钟级 + 复权类型）
     * 非日频数据不走DB缓存，直接走数据源获取
     */
    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate,
                                                  KLineFrequency frequency, AdjustType adjust) {
        if (frequency == KLineFrequency.DAILY) {
            return getHistoryData(stockCode, startDate, endDate);
        }
        // 非日频：直接走数据源故障切换，不走DB缓存
        return executeWithFailover(stockCode,
                f -> f.getHistoryData(stockCode, startDate, endDate, frequency, adjust),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    // ==================== 资金面数据 ====================

    /** 获取日级资金流数据（带缓存） */
    public List<Map<String, Object>> getFundFlow(String stockCode, int days) {
        return getOrFetch("fundflow:" + stockCode + ":" + days, CACHE_TTL_HOUR, () ->
                executeWithFailover(stockCode,
                        f -> f.getFundFlow(stockCode, days),
                        r -> r == null || r.isEmpty(),
                        Collections.emptyList()));
    }

    /**
     * 获取资金流数据（支持日级/分钟级）
     * 分钟级数据不缓存（实时性要求高），直接走数据源
     */
    @Override
    public List<Map<String, Object>> getFundFlow(String stockCode, int days, boolean minuteLevel) {
        if (!minuteLevel) {
            return getFundFlow(stockCode, days);
        }
        // 分钟级：直接走数据源故障切换
        return executeWithFailover(stockCode,
                f -> {
                    if (f instanceof io.leavesfly.alphaforge.infrastructure.dataprovider.impl.EFinanceFetcher ef) {
                        return ef.getFundFlow(stockCode, days, true);
                    }
                    return List.of();
                },
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    // ==================== 基本面数据 ====================

    /** 获取财报三表数据（带缓存） */
    public List<Map<String, Object>> getFinancialStatements(String stockCode, String statementType) {
        return getOrFetch("financials:" + stockCode + ":" + statementType, CACHE_TTL_DAY, () ->
                executeWithFailover(stockCode,
                        f -> f.getFinancialStatements(stockCode, statementType),
                        r -> r == null || r.isEmpty(),
                        Collections.emptyList()));
    }

    /** 获取关键财务指标 */
    public List<Map<String, Object>> getKeyIndicators(String stockCode) {
        return executeWithFailover(stockCode,
                f -> f.getKeyIndicators(stockCode),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    // ==================== 信号层数据 ====================

    /** 获取龙虎榜数据 */
    public List<Map<String, Object>> getDragonTigerList(String stockCode, int days) {
        return executeWithFailover(stockCode,
                f -> f.getDragonTigerList(stockCode, days),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    /** 获取北向资金流向 */
    public List<Map<String, Object>> getNorthboundFlow(int days) {
        return executeWithFailoverNoStock(
                f -> f.getNorthboundFlow(days),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    /** 获取个股板块归属详情 */
    public List<Map<String, Object>> getStockBoardsDetail(String stockCode) {
        return executeWithFailover(stockCode,
                f -> f.getStockBoardsDetail(stockCode),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    // ==================== 杠杆与筹码数据 ====================

    /** 获取融资融券明细 */
    public List<Map<String, Object>> getMarginTrading(String stockCode, int days) {
        return executeWithFailover(stockCode,
                f -> f.getMarginTrading(stockCode, days),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    /** 获取股东户数变化 */
    public List<Map<String, Object>> getShareholderCount(String stockCode) {
        return executeWithFailover(stockCode,
                f -> f.getShareholderCount(stockCode),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    /** 获取分红送转历史 */
    public List<Map<String, Object>> getDividendHistory(String stockCode) {
        return executeWithFailover(stockCode,
                f -> f.getDividendHistory(stockCode),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    // ==================== 研报与公告数据 ====================

    /** 获取个股研报列表 */
    public List<Map<String, Object>> getResearchReports(String stockCode, int count) {
        return executeWithFailover(stockCode,
                f -> f.getResearchReports(stockCode, count),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    /** 获取机构一致预期EPS */
    public List<Map<String, Object>> getConsensusEPS(String stockCode) {
        return executeWithFailover(stockCode,
                f -> f.getConsensusEPS(stockCode),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    /** 获取个股公告列表 */
    public List<Map<String, Object>> getAnnouncements(String stockCode, int count) {
        return executeWithFailover(stockCode,
                f -> f.getAnnouncements(stockCode, count),
                r -> r == null || r.isEmpty(),
                Collections.emptyList());
    }

    // ==================== 事件驱动数据 ====================

    /** 获取大宗交易数据 */
    public List<Map<String, Object>> getBlockTrades(String stockCode, int days) {
        return getOrFetch("blocktrade:" + stockCode + ":" + days, CACHE_TTL_DAY, () ->
                executeWithFailover(stockCode,
                        f -> f.getBlockTrades(stockCode, days),
                        r -> r == null || r.isEmpty(),
                        Collections.emptyList()));
    }

    /** 获取限售解禁日历 */
    public List<Map<String, Object>> getRestrictedShareUnlock(String stockCode, int days) {
        return getOrFetch("unlock:" + stockCode + ":" + days, CACHE_TTL_DAY, () ->
                executeWithFailover(stockCode,
                        f -> f.getRestrictedShareUnlock(stockCode, days),
                        r -> r == null || r.isEmpty(),
                        Collections.emptyList()));
    }

    /** 获取行业板块排名 */
    public List<Map<String, Object>> getIndustryRanking() {
        return getOrFetch("industry_ranking", CACHE_TTL_HOUR, () ->
                executeWithFailoverNoStock(
                        f -> f.getIndustryRanking(),
                        r -> r == null || r.isEmpty(),
                        Collections.emptyList()));
    }

    /** 获取全市场龙虎榜 */
    public List<Map<String, Object>> getMarketDragonTiger(LocalDate date) {
        return getOrFetch("market_dragon_tiger:" + date, CACHE_TTL_DAY, () ->
                executeWithFailoverNoStock(
                        f -> f.getMarketDragonTiger(date),
                        r -> r == null || r.isEmpty(),
                        Collections.emptyList()));
    }

    // ========== 熔断器逻辑 ==========

    /**
     * 检查熔断器是否打开
     */
    private boolean isCircuitOpen(String fetcherName) {
        CircuitBreaker cb = circuitBreakers.get(fetcherName);
        if (cb == null) return false;
        
        // 检查是否到了恢复时间
        if (cb.isOpen() && System.currentTimeMillis() > cb.getRecoveryTime()) {
            cb.halfOpen();
            return false;
        }
        return cb.isOpen();
    }

    /**
     * 记录成功调用
     */
    private void recordSuccess(String fetcherName) {
        CircuitBreaker cb = circuitBreakers.get(fetcherName);
        if (cb != null) {
            cb.recordSuccess();
        }
    }

    /**
     * 记录失败调用
     */
    private void recordFailure(String fetcherName) {
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
    private boolean tryAcquire(BaseDataFetcher fetcher) {
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

    // ========== 缓存层（交易日感知） ==========

    /**
     * 从缓存获取历史数据 — 交易日感知的有效性判断
     *
     * 缓存有效策略:
     * - 交易日盘中（9:30-15:00+缓冲）: 缓存可能不完整，仅当已有当日数据时有效
     * - 交易日收盘后: 缓存需包含当日数据
     * - 非交易日（周末/节假日）: 缓存包含最近交易日即可
     */
    private List<StockDailyData> getFromCache(String stockCode, LocalDate startDate, LocalDate endDate) {
        if (dailyDataRepo == null) return null;
        try {
            LocalDate maxDate = dailyDataRepo.findMaxTradeDate(stockCode);
            if (maxDate == null) return null;

            // 交易日感知的缓存有效性判断（多市场感知）
            MarketType marketType = MarketType.detectFromCode(stockCode);
            if (!isCacheFresh(maxDate, stockCode, marketType)) {
                return null; // 缓存过期
            }

            List<StockDailyData> cached = dailyDataRepo.findByStockCodeAndDateRange(stockCode, startDate, endDate);
            if (cached != null && !cached.isEmpty()) return cached;
        } catch (Exception e) {
            log.debug("缓存查询异常(忽略): {}", e.getMessage());
        }
        return null;
    }

    /**
     * 判断缓存是否新鲜（交易日感知 + 多市场感知）
     *
     * 各市场收盘缓冲时间不同：
     * - A股: 15:00 + 2h = 17:00 CST
     * - 港股: 16:00 + 2h = 18:00 HKT
     * - 美股: 16:00 + 2h = 18:00 ET（即北京时间次日06:00）
     */
    private boolean isCacheFresh(LocalDate maxCachedDate, String stockCode, MarketType market) {
        LocalDate today = LocalDate.now();

        if (tradingCalendar == null) {
            // 无交易日历时，回退到简单策略：1天内有效
            return maxCachedDate.plusDays(1).isAfter(today);
        }

        // 获取最近一个交易日（A股用A股日历，其他市场暂用周末判断）
        LocalDate lastTradingDay = tradingCalendar.getPreviousTradingDay(today.plusDays(1));

        // 如果缓存的最大日期 >= 最近交易日，则缓存有效
        if (!maxCachedDate.isBefore(lastTradingDay)) {
            return true;
        }

        // 如果今天是交易日且市场已收盘超过缓冲时间，缓存应包含今日数据
        if (tradingCalendar.isTradingDay(today)) {
            // 使用市场对应的时区判断收盘时间
            if (tradingCalendar.isMarketClosed(market)) {
                return false; // 市场已收盘+缓冲，期望有当日数据，但缓存没有
            }
            // 盘中或盘前，缓存有上一交易日数据即可
            return maxCachedDate.isEqual(lastTradingDay);
        }

        return false;
    }

    /**
     * 判断缓存数据是否完整覆盖请求范围
     */
    private boolean isCacheComplete(List<StockDailyData> cached, LocalDate startDate, LocalDate endDate) {
        if (cached == null || cached.isEmpty()) return false;
        LocalDate minDate = cached.stream().map(StockDailyData::getTradeDate).min(LocalDate::compareTo).orElse(null);
        LocalDate maxDate = cached.stream().map(StockDailyData::getTradeDate).max(LocalDate::compareTo).orElse(null);
        if (minDate == null || maxDate == null) return false;
        return !minDate.isAfter(startDate) && !maxDate.isBefore(endDate.minusDays(1));
    }

    /**
     * 合并缓存数据与增量数据（去重）
     */
    private List<StockDailyData> mergeData(List<StockDailyData> base, List<StockDailyData> incremental) {
        if (incremental == null || incremental.isEmpty()) return base;
        if (base == null || base.isEmpty()) return incremental;

        Map<LocalDate, StockDailyData> merged = new TreeMap<>();
        for (StockDailyData d : base) merged.put(d.getTradeDate(), d);
        for (StockDailyData d : incremental) merged.put(d.getTradeDate(), d); // 覆盖同日期数据
        return new ArrayList<>(merged.values());
    }

    /**
     * 数据质量校验与过滤
     * - 过滤掉明显异常的数据条目（价格<=0等）
     * - 记录质量问题日志
     */
    private List<StockDailyData> validateAndFilter(List<StockDailyData> data, String stockCode) {
        if (data == null || data.isEmpty()) return data;

        // 过滤掉明显异常的条目
        List<StockDailyData> filtered = new ArrayList<>();
        int removed = 0;
        for (StockDailyData bar : data) {
            if (bar.getClosePrice() == null || bar.getClosePrice() <= 0) {
                removed++;
                continue;
            }
            if (bar.getVolume() != null && bar.getVolume() < 0) {
                removed++;
                continue;
            }
            filtered.add(bar);
        }
        if (removed > 0) {
            log.warn("[{}] 数据质量过滤: 移除 {} 条异常数据", stockCode, removed);
        }

        // 执行深度质量校验（如果校验器可用）
        if (qualityValidator != null) {
            DataQualityValidator.ValidationResult result = qualityValidator.validate(filtered, stockCode);
            if (!result.isValid()) {
                log.warn("[{}] 数据质量校验发现 {} 个问题，已记录但不过滤",
                        stockCode, result.getIssues().size());
            }
        }

        return filtered;
    }

    /** 保存数据到缓存 */
    private void saveToCache(List<StockDailyData> data) {
        if (dailyDataRepo == null || data == null || data.isEmpty()) return;
        try {
            dailyDataRepo.batchInsert(data);
        } catch (Exception e) {
            // 可能是重复插入，忽略
            log.debug("缓存写入异常(不影响功能): {}", e.getMessage());
        }
    }

    // ========== TTL缓存层 ==========

    /**
     * 带缓存的获取数据 — 先查缓存，未命中再调远程
     *
     * @param cacheKey 缓存键
     * @param ttlMs    缓存有效期(毫秒)
     * @param supplier 缓存未命中时的数据获取函数
     * @return 数据列表
     */
    private List<Map<String, Object>> getOrFetch(String cacheKey, long ttlMs,
                                                  java.util.function.Supplier<List<Map<String, Object>>> supplier) {
        TtlCacheEntry entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            log.debug("缓存命中: {}", cacheKey);
            return entry.getValue();
        }
        List<Map<String, Object>> data = supplier.get();
        if (data != null && !data.isEmpty()) {
            cache.put(cacheKey, new TtlCacheEntry(data, System.currentTimeMillis() + ttlMs));
        }
        return data != null ? data : Collections.emptyList();
    }

}
