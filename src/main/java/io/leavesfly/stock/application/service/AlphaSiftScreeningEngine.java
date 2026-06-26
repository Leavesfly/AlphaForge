package io.leavesfly.stock.application.service;

import io.leavesfly.stock.application.strategy.StrategyCatalog;
import io.leavesfly.stock.application.strategy.engine.ScreeningScoreEngine;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.entity.AlphaSiftTask;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.infrastructure.persistence.AlphaSiftTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AlphaSift 智能选股引擎 — 支持自选池/配置池，异步任务追踪。
 */
@Service
public class AlphaSiftScreeningEngine {

    private static final Logger log = LoggerFactory.getLogger(AlphaSiftScreeningEngine.class);

    private final DataFetcherManager dataFetcher;
    private final AppConfig config;
    private final StrategyCatalog catalog;
    private final ScreeningScoreEngine scoreEngine;
    private final WatchlistService watchlistService;
    private final AlphaSiftTaskRepository taskRepo;
    private final ObjectMapper objectMapper;

    /** 默认兜底股票池（自选为空且未配置时使用） */
    private static final List<Map<String, String>> DEFAULT_POOL = List.of(
            Map.of("code", "600519", "name", "贵州茅台", "market", "A"),
            Map.of("code", "000858", "name", "五粮液", "market", "A"),
            Map.of("code", "601318", "name", "中国平安", "market", "A"),
            Map.of("code", "000333", "name", "美的集团", "market", "A"),
            Map.of("code", "300750", "name", "宁德时代", "market", "A")
    );

    public AlphaSiftScreeningEngine(DataFetcherManager dataFetcher,
                                    AppConfig config,
                                    StrategyCatalog catalog,
                                    ScreeningScoreEngine scoreEngine,
                                    WatchlistService watchlistService,
                                    AlphaSiftTaskRepository taskRepo,
                                    ObjectMapper objectMapper) {
        this.dataFetcher = dataFetcher;
        this.config = config;
        this.catalog = catalog;
        this.scoreEngine = scoreEngine;
        this.watchlistService = watchlistService;
        this.taskRepo = taskRepo;
        this.objectMapper = objectMapper;
    }

    /** 同步筛选（兼容旧 API） */
    public List<Map<String, Object>> screen(String strategy, String market, int maxResults) {
        return screenInternal(strategy, market, maxResults, null);
    }

    /** 提交异步筛选任务 */
    public AlphaSiftTask submitScreening(String strategy, String market, int maxResults) {
        AlphaSiftTask task = new AlphaSiftTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setStrategy(strategy);
        task.setMarket(market);
        task.setStatus("pending");
        task.setProgress(0);
        task.setCreatedAt(LocalDateTime.now());
        taskRepo.insert(task);

        CompletableFuture.runAsync(() -> runScreeningTask(task.getTaskId(), strategy, market, maxResults));
        return task;
    }

    public Map<String, Object> getTask(String taskId) {
        AlphaSiftTask task = taskRepo.findByTaskId(taskId);
        if (task == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task_id", task.getTaskId());
        m.put("strategy", task.getStrategy());
        m.put("market", task.getMarket());
        m.put("status", task.getStatus());
        m.put("progress", task.getProgress());
        m.put("result", task.getResultStocks());
        m.put("result_count", task.getResultCount());
        m.put("error_message", task.getErrorMessage());
        m.put("created_at", task.getCreatedAt());
        m.put("completed_at", task.getCompletedAt());
        return m;
    }

    private void runScreeningTask(String taskId, String strategy, String market, int maxResults) {
        try {
            taskRepo.updateStatus(taskId, "running", 10);
            List<Map<String, Object>> results = screenInternal(strategy, market, maxResults, taskId);
            taskRepo.updateCompleted(taskId, "completed",
                    objectMapper.writeValueAsString(results), results.size(),
                    null, LocalDateTime.now());
        } catch (Exception e) {
            log.error("选股任务失败: {}", e.getMessage());
            taskRepo.updateCompleted(taskId, "failed", null, 0, e.getMessage(), LocalDateTime.now());
        }
    }

    private List<Map<String, Object>> screenInternal(String strategy, String market,
                                                     int maxResults, String taskId) {
        Optional<StrategyDefinition> strategyOpt = catalog.find(strategy);
        if (strategyOpt.isEmpty() || !strategyOpt.get().hasScreening() || !strategyOpt.get().isAvailable()) {
            return List.of();
        }
        StrategyDefinition definition = strategyOpt.get();
        List<Map<String, String>> pool = resolveStockPool(market);
        List<Map<String, Object>> candidates = new ArrayList<>();
        int scanned = 0;

        for (Map<String, String> stock : pool) {
            scanned++;
            if (taskId != null && scanned % 5 == 0) {
                int progress = Math.min(95, scanned * 100 / Math.max(pool.size(), 1));
                taskRepo.updateStatus(taskId, "running", progress);
            }
            Map<String, Object> quote = dataFetcher.getRealtimeQuote(stock.get("code"));
            if (quote == null || quote.isEmpty()) {
                continue;
            }
            double score = scoreEngine.score(definition, stock.get("code"), quote);
            if (score > 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("stock_code", stock.get("code"));
                item.put("stock_name", stock.get("name"));
                item.put("market", stock.get("market"));
                item.put("score", score);
                item.put("reason", scoreEngine.reason(definition, score));
                item.put("strategy", definition.getId());
                item.put("change_pct", quote.get("change_pct"));
                candidates.add(item);
            }
        }

        candidates.sort(Comparator.comparingDouble((Map<String, Object> m) ->
                ((Number) m.get("score")).doubleValue()).reversed());
        if (candidates.size() > maxResults) {
            candidates = candidates.subList(0, maxResults);
        }
        log.info("AlphaSift 筛选完成: strategy={}, scanned={}, hits={}", strategy, scanned, candidates.size());
        return candidates;
    }

    private List<Map<String, String>> resolveStockPool(String market) {
        String universe = config.getEnv("SCREENING_UNIVERSE", "watchlist");
        List<Map<String, String>> pool = new ArrayList<>();

        if ("watchlist".equalsIgnoreCase(universe) || "all".equalsIgnoreCase(universe)) {
            pool.addAll(watchlistService.asStockPool());
        }
        if ("config".equalsIgnoreCase(universe) || "all".equalsIgnoreCase(universe)) {
            for (String code : config.getStockList()) {
                pool.add(Map.of("code", code, "name", code, "market", config.getMarket()));
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(DEFAULT_POOL);
        }
        if (market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market)) {
            String m = market.toUpperCase();
            pool = pool.stream()
                    .filter(s -> m.equalsIgnoreCase(s.get("market")) || matchesMarket(m, s.get("code")))
                    .toList();
        }
        return pool;
    }

    private boolean matchesMarket(String market, String code) {
        if ("A".equals(market) || "CN".equals(market)) {
            return code.matches("\\d{6}");
        }
        return true;
    }
}
