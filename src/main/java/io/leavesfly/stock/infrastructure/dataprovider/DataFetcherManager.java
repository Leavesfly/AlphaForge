package io.leavesfly.stock.infrastructure.dataprovider;

import io.leavesfly.stock.domain.service.port.MarketDataPort;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import io.leavesfly.stock.domain.model.enums.DataProviderType;
import io.leavesfly.stock.domain.model.enums.MarketType;
import io.leavesfly.stock.domain.service.TradingCalendar;
import io.leavesfly.stock.infrastructure.persistence.market.StockDailyDataRepository;
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

    private final AppConfig config;
    private final List<BaseDataFetcher> fetchers;
    private final StockDailyDataRepository dailyDataRepo;
    private final TradingCalendar tradingCalendar;
    private final DataQualityValidator qualityValidator;
    
    /** 熔断器状态: 数据源名称 -> 熔断信息 */
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /** 限流器状态: 数据源名称 -> 限流信息 */
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    /** TTL缓存: 缓存键 -> 缓存条目 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    /** 数据源优先级排序(根据市场类型) */
    private final Map<MarketType, List<DataProviderType>> marketProviderOrder;

    /** 随机抖动范围(毫秒)，叠加在限流间隔之上，防止规律性请求被识别 */
    private static final long JITTER_MS = 200;
    /** A股收盘时间后的缓冲(小时)，收盘后2小时内缓存视为最新 */
    private static final int POST_CLOSE_BUFFER_HOURS = 2;

    // 缓存TTL常量（毫秒）
    private static final long CACHE_TTL_HOUR = 3600_000L;       // 1小时
    private static final long CACHE_TTL_HALF_HOUR = 1800_000L;   // 30分钟
    private static final long CACHE_TTL_DAY = 86400_000L;        // 24小时

    @Autowired
    public DataFetcherManager(AppConfig config, List<BaseDataFetcher> fetchers,
                              StockDailyDataRepository dailyDataRepo,
                              @Autowired(required = false) TradingCalendar tradingCalendar,
                              @Autowired(required = false) DataQualityValidator qualityValidator) {
        this.config = config;
        this.fetchers = fetchers;
        this.dailyDataRepo = dailyDataRepo;
        this.tradingCalendar = tradingCalendar;
        this.qualityValidator = qualityValidator;
        this.marketProviderOrder = initMarketProviderOrder();
        log.info("数据源管理器初始化完成, 已注册 {} 个数据源, 交易日历: {}, 质量校验: {}",
                fetchers.size(), tradingCalendar != null, qualityValidator != null);
    }

    // 测试用构造器(无Spring环境)
    public DataFetcherManager(List<BaseDataFetcher> fetchers, AppConfig config) {
        this(config, fetchers, null, null, null);
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

    /**
     * 获取实时行情 - 自动故障切换 + 限流
     */
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            
            try {
                Map<String, Object> quote = fetcher.getRealtimeQuote(stockCode);
                if (quote != null && !quote.isEmpty()) {
                    recordSuccess(fetcherName);
                    return quote;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取实时行情失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        
        return Collections.emptyMap();
    }

    /**
     * 获取股票基本信息 - 自动故障切换 + 限流
     */
    public Map<String, Object> getStockInfo(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            
            try {
                Map<String, Object> info = fetcher.getStockInfo(stockCode);
                if (info != null && !info.isEmpty()) {
                    recordSuccess(fetcherName);
                    return info;
                }
            } catch (Exception e) {
                recordFailure(fetcherName);
            }
        }
        
        return Collections.emptyMap();
    }

    /**
     * 获取排序后的数据源列表(根据市场类型和优先级)
     * 能力感知路由：过滤掉不支持当前市场的数据源，避免无效请求
     */
    private List<BaseDataFetcher> getOrderedFetchers(MarketType market) {
        // 如果配置了指定数据源
        String configProvider = config.getDataProvider();
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

    /**
     * 获取股票所属板块 - 自动故障切换 + 限流
     */
    public List<String> getStockBoards(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                List<String> boards = fetcher.getStockBoards(stockCode);
                if (boards != null && !boards.isEmpty()) {
                    recordSuccess(fetcherName);
                    return boards;
                }
            } catch (Exception e) {
                // 板块信息非关键，静默失败
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取分钟级K线数据 - 自动故障切换 + 限流
     *
     * @param stockCode 股票代码
     * @param period    周期(1/5/15/30/60分钟)
     * @param count     数据条数
     * @return K线数据列表
     */
    public List<Map<String, Object>> getMinuteData(String stockCode, int period, int count) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);

        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;

            try {
                List<Map<String, Object>> minuteData = fetcher.getMinuteData(stockCode, period, count);
                if (minuteData != null && !minuteData.isEmpty()) {
                    recordSuccess(fetcherName);
                    return minuteData;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取分钟数据失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }

        log.warn("所有数据源均无法获取分钟数据: {}", stockCode);
        return Collections.emptyList();
    }

    // ========== 资金面数据路由 ==========

    /**
     * 获取日级资金流数据 — 自动故障切换 + 限流
     *
     * @param stockCode 股票代码
     * @param days      返回最近天数
     * @return 每日资金流数据 [{date, main_net, big_net, mid_net, small_net, ...}]
     */
    public List<Map<String, Object>> getFundFlow(String stockCode, int days) {
        return getOrFetch("fundflow:" + stockCode + ":" + days, CACHE_TTL_HOUR, () -> {
            MarketType market = MarketType.detectFromCode(stockCode);
            List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
            for (BaseDataFetcher fetcher : orderedFetchers) {
                String fetcherName = fetcher.getName();
                if (isCircuitOpen(fetcherName)) continue;
                if (!tryAcquire(fetcher)) continue;
                try {
                    List<Map<String, Object>> flow = fetcher.getFundFlow(stockCode, days);
                    if (flow != null && !flow.isEmpty()) {
                        recordSuccess(fetcherName);
                        return flow;
                    }
                } catch (Exception e) {
                    log.warn("数据源 {} 获取资金流失败: {}", fetcherName, e.getMessage());
                    recordFailure(fetcherName);
                }
            }
            return Collections.emptyList();
        });
    }

    // ========== 基本面数据路由 ==========

    /**
     * 获取财报三表数据 — 自动故障切换 + 限流
     *
     * @param stockCode      股票代码
     * @param statementType 报表类型: "balance" / "income" / "cashflow"
     * @return 财务数据列表
     */
    public List<Map<String, Object>> getFinancialStatements(String stockCode, String statementType) {
        return getOrFetch("financials:" + stockCode + ":" + statementType, CACHE_TTL_DAY, () -> {
            MarketType market = MarketType.detectFromCode(stockCode);
            List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
            for (BaseDataFetcher fetcher : orderedFetchers) {
                String fetcherName = fetcher.getName();
                if (isCircuitOpen(fetcherName)) continue;
                if (!tryAcquire(fetcher)) continue;
                try {
                    List<Map<String, Object>> stmts = fetcher.getFinancialStatements(stockCode, statementType);
                    if (stmts != null && !stmts.isEmpty()) {
                        recordSuccess(fetcherName);
                        return stmts;
                    }
                } catch (Exception e) {
                    log.warn("数据源 {} 获取财报失败: {}", fetcherName, e.getMessage());
                    recordFailure(fetcherName);
                }
            }
            return Collections.emptyList();
        });
    }

    /**
     * 获取关键财务指标 — 自动故障切换 + 限流
     *
     * @param stockCode 股票代码
     * @return 关键指标列表
     */
    public List<Map<String, Object>> getKeyIndicators(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);

        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;

            try {
                List<Map<String, Object>> indicators = fetcher.getKeyIndicators(stockCode);
                if (indicators != null && !indicators.isEmpty()) {
                    recordSuccess(fetcherName);
                    return indicators;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取关键指标失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }

        log.warn("所有数据源均无法获取关键指标: {}", stockCode);
        return Collections.emptyList();
    }

    // ========== 信号层数据路由 ==========

    /**
     * 获取龙虎榜数据 — 自动故障切换 + 限流
     */
    public List<Map<String, Object>> getDragonTigerList(String stockCode, int days) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                List<Map<String, Object>> data = fetcher.getDragonTigerList(stockCode, days);
                if (data != null && !data.isEmpty()) {
                    recordSuccess(fetcherName);
                    return data;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取龙虎榜失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取北向资金流向 — 自动故障切换 + 限流
     */
    public List<Map<String, Object>> getNorthboundFlow(int days) {
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(MarketType.A);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                List<Map<String, Object>> data = fetcher.getNorthboundFlow(days);
                if (data != null && !data.isEmpty()) {
                    recordSuccess(fetcherName);
                    return data;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取北向资金失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取个股板块归属详情 — 自动故障切换 + 限流
     */
    public List<Map<String, Object>> getStockBoardsDetail(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                List<Map<String, Object>> data = fetcher.getStockBoardsDetail(stockCode);
                if (data != null && !data.isEmpty()) {
                    recordSuccess(fetcherName);
                    return data;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取板块归属失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return Collections.emptyList();
    }

    // ========== 杠杆与筹码数据路由 ==========

    /**
     * 获取融资融券明细 — 自动故障切换 + 限流
     */
    public List<Map<String, Object>> getMarginTrading(String stockCode, int days) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                List<Map<String, Object>> data = fetcher.getMarginTrading(stockCode, days);
                if (data != null && !data.isEmpty()) {
                    recordSuccess(fetcherName);
                    return data;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取融资融券失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取股东户数变化 — 自动故障切换 + 限流
     */
    public List<Map<String, Object>> getShareholderCount(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                List<Map<String, Object>> data = fetcher.getShareholderCount(stockCode);
                if (data != null && !data.isEmpty()) {
                    recordSuccess(fetcherName);
                    return data;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取股东户数失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取分红送转历史 — 自动故障切换 + 限流
     */
    public List<Map<String, Object>> getDividendHistory(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                List<Map<String, Object>> data = fetcher.getDividendHistory(stockCode);
                if (data != null && !data.isEmpty()) {
                    recordSuccess(fetcherName);
                    return data;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取分红送转失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return Collections.emptyList();
    }

    // ========== 研报与公告数据路由 ==========

    /**
     * 获取个股研报列表 — 自动故障切换 + 限流
     */
    public List<Map<String, Object>> getResearchReports(String stockCode, int count) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                List<Map<String, Object>> data = fetcher.getResearchReports(stockCode, count);
                if (data != null && !data.isEmpty()) {
                    recordSuccess(fetcherName);
                    return data;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取研报失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取机构一致预期EPS — 自动故障切换 + 限流
     */
    public List<Map<String, Object>> getConsensusEPS(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                List<Map<String, Object>> data = fetcher.getConsensusEPS(stockCode);
                if (data != null && !data.isEmpty()) {
                    recordSuccess(fetcherName);
                    return data;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取一致预期失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取个股公告列表 — 自动故障切换 + 限流
     */
    public List<Map<String, Object>> getAnnouncements(String stockCode, int count) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            if (!tryAcquire(fetcher)) continue;
            try {
                List<Map<String, Object>> data = fetcher.getAnnouncements(stockCode, count);
                if (data != null && !data.isEmpty()) {
                    recordSuccess(fetcherName);
                    return data;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取公告失败: {}", fetcherName, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        return Collections.emptyList();
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

    /**
     * 初始化市场对应的数据源优先级
     */
    private Map<MarketType, List<DataProviderType>> initMarketProviderOrder() {
        Map<MarketType, List<DataProviderType>> order = new EnumMap<>(MarketType.class);
        order.put(MarketType.A, List.of(
                DataProviderType.AKSHARE, DataProviderType.EFINANCE, 
                DataProviderType.TUSHARE, DataProviderType.TENCENT,
                DataProviderType.BAOSTOCK, DataProviderType.PYTDX));
        order.put(MarketType.HK, List.of(
                DataProviderType.YFINANCE, DataProviderType.LONGBRIDGE,
                DataProviderType.FINNHUB, DataProviderType.ALPHAVANTAGE));
        order.put(MarketType.US, List.of(
                DataProviderType.YFINANCE, DataProviderType.FINNHUB,
                DataProviderType.ALPHAVANTAGE, DataProviderType.LONGBRIDGE));
        order.put(MarketType.JP, List.of(
                DataProviderType.YFINANCE, DataProviderType.ALPHAVANTAGE));
        order.put(MarketType.KR, List.of(
                DataProviderType.YFINANCE, DataProviderType.ALPHAVANTAGE));
        return order;
    }

    /**
     * 熔断器内部类
     */
    private static class CircuitBreaker {
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

    // ========== 限流器逻辑 ==========

    /**
     * 尝试获取请求许可（差异化限流）
     * 使用数据源自身的限流间隔 + 随机抖动，防止规律性请求被封禁
     */
    private boolean tryAcquire(BaseDataFetcher fetcher) {
        String fetcherName = fetcher.getName();
        long rateLimitMs = fetcher.getRateLimitMs();
        RateLimiter limiter = rateLimiters.computeIfAbsent(fetcherName, k -> new RateLimiter(rateLimitMs, JITTER_MS));
        // 如果限流间隔变了（配置更新），更新限流器
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

            // 交易日感知的缓存有效性判断
            if (!isCacheFresh(maxDate, stockCode)) {
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
     * 判断缓存是否新鲜（交易日感知）
     */
    private boolean isCacheFresh(LocalDate maxCachedDate, String stockCode) {
        LocalDate today = LocalDate.now();

        if (tradingCalendar == null) {
            // 无交易日历时，回退到简单策略：1天内有效
            return maxCachedDate.plusDays(1).isAfter(today);
        }

        // 获取最近一个交易日
        LocalDate lastTradingDay = tradingCalendar.getPreviousTradingDay(today.plusDays(1));

        // 如果缓存的最大日期 >= 最近交易日，则缓存有效
        // （收盘后已有当日数据，或非交易日时已有上一交易日数据）
        if (!maxCachedDate.isBefore(lastTradingDay)) {
            return true;
        }

        // 如果今天是交易日且市场已收盘超过缓冲时间，缓存应包含今日数据
        if (tradingCalendar.isTradingDay(today)) {
            LocalDateTime now = LocalDateTime.now();
            // A股15:00收盘 + 2小时缓冲 = 17:00后期望有当日数据
            if (now.getHour() >= 17) {
                return false; // 期望有今日数据，但缓存没有
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

    // ========== 限流器内部类 ==========

    /**
     * 差异化限流器 — 基于最小请求间隔 + 随机抖动
     *
     * 确保对同一数据源的请求间隔不小于指定毫秒数 + 随机抖动，
     * 防止高频请求触发封禁，同时避免规律性请求被识别。
     */
    private static class RateLimiter {
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
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            log.debug("缓存命中: {}", cacheKey);
            return entry.getValue();
        }
        List<Map<String, Object>> data = supplier.get();
        if (data != null && !data.isEmpty()) {
            cache.put(cacheKey, new CacheEntry(data, System.currentTimeMillis() + ttlMs));
        }
        return data != null ? data : Collections.emptyList();
    }

    /** TTL缓存条目 */
    private static class CacheEntry {
        private final List<Map<String, Object>> value;
        private final long expiryTime;

        public CacheEntry(List<Map<String, Object>> value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }

        public List<Map<String, Object>> getValue() { return value; }
        public boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
    }
}
