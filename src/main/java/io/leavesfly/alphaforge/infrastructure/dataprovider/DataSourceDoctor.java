package io.leavesfly.alphaforge.infrastructure.dataprovider;

import io.leavesfly.alphaforge.config.DataSourceDoctorConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据源主动体检 — 逐数据源真实拉取探测，区分"环境问题 vs 代码问题"。
 *
 * <p>对齐 skill doctor 语义：限流/无 Key/网络不通属环境问题（换源或补配置即可），
 * 返回数据异常才可能是代码问题。体检自身受并发上限 1 约束（synchronized 串行），
 * 结果带 TTL 缓存——遵循"限流场景缓存优先"实践，体检不得加剧上游 429。</p>
 */
@Component
public class DataSourceDoctor {

    private static final Logger log = LoggerFactory.getLogger(DataSourceDoctor.class);

    /** 错误分类：限流（环境） */
    public static final String ISSUE_RATE_LIMIT = "rate_limit";
    /** 错误分类：无 Key/Token（环境） */
    public static final String ISSUE_NO_KEY = "no_key";
    /** 错误分类：网络不通（环境） */
    public static final String ISSUE_NETWORK = "network";
    /** 错误分类：数据异常（可能代码问题） */
    public static final String ISSUE_DATA_ERROR = "data_error";

    private final List<BaseDataFetcher> fetchers;
    private final DataSourceDoctorConfig config;

    /** 体检结果缓存（volatile + synchronized 写入；force=true 旁路） */
    private volatile Map<String, Object> cachedReport;
    private volatile long cachedAtMs;

    public DataSourceDoctor(List<BaseDataFetcher> fetchers, DataSourceDoctorConfig config) {
        this.fetchers = fetchers != null ? fetchers : List.of();
        this.config = config;
    }

    /**
     * 获取体检结果：TTL 内命中缓存直接返回，过期或 force=true 时重新探测。
     *
     * @param force true 绕过 TTL 手动触发（入口层负责限流保护）
     */
    public Map<String, Object> getCachedOrProbe(boolean force) {
        Map<String, Object> report = cachedReport;
        long age = System.currentTimeMillis() - cachedAtMs;
        if (!force && report != null && age < config.getTtlSeconds() * 1000L) {
            Map<String, Object> copy = new LinkedHashMap<>(report);
            copy.put("cached", true);
            return copy;
        }
        synchronized (this) {
            // 双重检查：等锁期间可能已被并发请求填充
            if (!force && cachedReport != null
                    && System.currentTimeMillis() - cachedAtMs < config.getTtlSeconds() * 1000L) {
                Map<String, Object> copy = new LinkedHashMap<>(cachedReport);
                copy.put("cached", true);
                return copy;
            }
            Map<String, Object> fresh = probeAll();
            cachedReport = fresh;
            cachedAtMs = System.currentTimeMillis();
            Map<String, Object> copy = new LinkedHashMap<>(fresh);
            copy.put("cached", false);
            return copy;
        }
    }

    /** 逐源串行探测（synchronized 保证并发上限 1，避免体检叠加打爆上游） */
    public synchronized Map<String, Object> probeAll() {
        List<Map<String, Object>> results = new ArrayList<>();
        int ok = 0;
        int environmentIssues = 0;

        List<BaseDataFetcher> ordered = new ArrayList<>(fetchers);
        ordered.sort(Comparator.comparingInt(BaseDataFetcher::getPriority));

        for (BaseDataFetcher fetcher : ordered) {
            Map<String, Object> item = probeOne(fetcher);
            results.add(item);
            if ("ok".equals(item.get("status"))) {
                ok++;
            } else if ("environment".equals(item.get("issueType"))) {
                environmentIssues++;
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("checkedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        report.put("ttlSeconds", config.getTtlSeconds());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", results.size());
        summary.put("ok", ok);
        summary.put("failed", results.size() - ok);
        summary.put("environmentIssues", environmentIssues);
        report.put("summary", summary);
        report.put("fetchers", results);
        log.info("数据源体检完成: {}/{} 可用, 环境问题 {} 个", ok, results.size(), environmentIssues);
        return report;
    }

    /** 单源探测：选市场样本标的 → 真实拉取 → 记录耗时/末根日期/错误分类 */
    private Map<String, Object> probeOne(BaseDataFetcher fetcher) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", fetcher.getName());
        item.put("family", fetcher.getDataFamily());
        item.put("priority", fetcher.getPriority());

        String symbol = pickProbeSymbol(fetcher);
        if (symbol == null) {
            item.put("status", "unsupported");
            item.put("issueType", "environment");
            item.put("error", "无支持市场的探针标的（市场: " + fetcher.getSupportedMarkets() + "）");
            return item;
        }
        item.put("probeSymbol", symbol);

        if (!fetcher.isAvailable()) {
            item.put("status", ISSUE_NO_KEY);
            item.put("issueType", "environment");
            item.put("error", "数据源声明不可用（缺少 API Key/Token，属配置问题非代码问题）");
            return item;
        }

        long start = System.currentTimeMillis();
        try {
            LocalDate end = LocalDate.now();
            LocalDate start_ = end.minusDays(config.getProbeDays());
            List<StockDailyData> bars = fetcher.getHistoryData(symbol, start_, end);
            item.put("latencyMs", System.currentTimeMillis() - start);
            if (bars == null || bars.isEmpty()) {
                item.put("status", ISSUE_DATA_ERROR);
                item.put("issueType", "code");
                item.put("error", "接口正常但返回空数据");
                return item;
            }
            item.put("status", "ok");
            item.put("issueType", "none");
            item.put("bars", bars.size());
            LocalDate last = bars.stream()
                    .map(StockDailyData::getTradeDate)
                    .filter(d -> d != null)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            item.put("lastBarDate", last != null ? last.toString() : null);
        } catch (Exception e) {
            item.put("latencyMs", System.currentTimeMillis() - start);
            item.put("status", classify(e));
            item.put("issueType", ISSUE_RATE_LIMIT.equals(classify(e))
                    || ISSUE_NO_KEY.equals(classify(e)) || ISSUE_NETWORK.equals(classify(e))
                    ? "environment" : "code");
            item.put("error", truncate(e.getMessage()));
            log.debug("数据源 {} 探测失败: {}", fetcher.getName(), e.getMessage());
        }
        return item;
    }

    /** 按 fetcher 支持的市场选择探针标的（A 股优先，其次美股/港股） */
    private String pickProbeSymbol(BaseDataFetcher fetcher) {
        try {
            var markets = fetcher.getSupportedMarkets();
            if (markets.contains(MarketType.A)) return config.getProbeAShare();
            if (markets.contains(MarketType.US)) return config.getProbeUs();
            if (markets.contains(MarketType.HK)) return config.getProbeHk();
        } catch (Exception ignored) {
            // 能力声明异常按无探针处理
        }
        return null;
    }

    /**
     * 错误分类归因：RATE_LIMIT / NO_KEY / NETWORK 属环境问题，DATA_ERROR 可能是代码问题。
     * 包可见静态纯函数，便于单测覆盖。
     */
    static String classify(Throwable e) {
        String msg = e != null && e.getMessage() != null
                ? e.getMessage().toLowerCase(Locale.ROOT) : "";
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("ratelimit")
                || msg.contains("too many") || msg.contains("频繁") || msg.contains("限流")) {
            return ISSUE_RATE_LIMIT;
        }
        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized")
                || msg.contains("forbidden") || msg.contains("api key") || msg.contains("apikey")
                || msg.contains("token") || msg.contains("无 key") || msg.contains("鉴权")) {
            return ISSUE_NO_KEY;
        }
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("connect")
                || msg.contains("unknownhost") || msg.contains("socket") || msg.contains("network")
                || msg.contains("connection") || msg.contains("dns")) {
            return ISSUE_NETWORK;
        }
        return ISSUE_DATA_ERROR;
    }

    /** 归因到"环境 vs 代码"：环境问题换源/补配置即可，代码问题需要修复 */
    static String issueTypeOf(String status) {
        return switch (status) {
            case ISSUE_RATE_LIMIT, ISSUE_NO_KEY, ISSUE_NETWORK, "unsupported" -> "environment";
            case "ok" -> "none";
            default -> "code";
        };
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
