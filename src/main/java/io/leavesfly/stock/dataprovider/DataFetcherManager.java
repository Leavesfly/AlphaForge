package io.leavesfly.stock.dataprovider;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.model.entity.StockDailyData;
import io.leavesfly.stock.model.enums.DataProviderType;
import io.leavesfly.stock.model.enums.MarketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据源管理器 - 策略模式 + 熔断器
 * 
 * 对应Python版本的DataFetcherManager
 * 功能:
 * 1. 多数据源自动切换
 * 2. 故障熔断与自动恢复
 * 3. 防封禁流控
 * 4. 指数退避重试
 */
@Component
public class DataFetcherManager {

    private static final Logger log = LoggerFactory.getLogger(DataFetcherManager.class);

    private final AppConfig config;
    private final List<BaseDataFetcher> fetchers;
    
    /** 熔断器状态: 数据源名称 -> 熔断信息 */
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    /** 数据源优先级排序(根据市场类型) */
    private final Map<MarketType, List<DataProviderType>> marketProviderOrder;

    public DataFetcherManager(AppConfig config, List<BaseDataFetcher> fetchers) {
        this.config = config;
        this.fetchers = fetchers;
        this.marketProviderOrder = initMarketProviderOrder();
        log.info("数据源管理器初始化完成, 已注册 {} 个数据源", fetchers.size());
    }

    // 测试用构造器(无Spring环境)
    public DataFetcherManager(List<BaseDataFetcher> fetchers, AppConfig config) {
        this(config, fetchers);
    }

    /**
     * 获取历史数据 - 自动故障切换
     *
     * @param stockCode 股票代码
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 日K线数据列表
     */
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            
            // 检查熔断器状态
            if (isCircuitOpen(fetcherName)) {
                log.debug("数据源 {} 处于熔断状态, 跳过", fetcherName);
                continue;
            }
            
            try {
                log.debug("尝试使用数据源 {} 获取 {} 历史数据", fetcherName, stockCode);
                List<StockDailyData> data = fetcher.getHistoryData(stockCode, startDate, endDate);
                
                if (data != null && !data.isEmpty()) {
                    // 记录成功
                    recordSuccess(fetcherName);
                    log.info("数据源 {} 成功获取 {} 条历史数据: {}", fetcherName, data.size(), stockCode);
                    return data;
                }
            } catch (Exception e) {
                log.warn("数据源 {} 获取历史数据失败: {} - {}", fetcherName, stockCode, e.getMessage());
                recordFailure(fetcherName);
            }
        }
        
        log.error("所有数据源均无法获取历史数据: {}", stockCode);
        return Collections.emptyList();
    }

    /**
     * 获取实时行情 - 自动故障切换
     */
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            
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
     * 获取股票基本信息 - 自动故障切换
     */
    public Map<String, Object> getStockInfo(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        
        for (BaseDataFetcher fetcher : orderedFetchers) {
            String fetcherName = fetcher.getName();
            if (isCircuitOpen(fetcherName)) continue;
            
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
        
        // 按优先级排序
        List<BaseDataFetcher> sorted = new ArrayList<>(fetchers);
        sorted.sort(Comparator.comparingInt(BaseDataFetcher::getPriority));
        return sorted;
    }

    /**
     * 获取股票所属板块
     */
    public List<String> getStockBoards(String stockCode) {
        MarketType market = MarketType.detectFromCode(stockCode);
        List<BaseDataFetcher> orderedFetchers = getOrderedFetchers(market);
        for (BaseDataFetcher fetcher : orderedFetchers) {
            if (isCircuitOpen(fetcher.getName())) continue;
            try {
                List<String> boards = fetcher.getStockBoards(stockCode);
                if (boards != null && !boards.isEmpty()) {
                    recordSuccess(fetcher.getName());
                    return boards;
                }
            } catch (Exception e) {
                // 板块信息非关键，静默失败
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
}
