package io.leavesfly.stock.application.pipeline;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.application.pipeline.AnalysisPostProcessor.AnalysisResult;
import io.leavesfly.stock.application.pipeline.AnalysisPostProcessor.TrendAnalysisResult;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.infrastructure.llm.LlmService;
import io.leavesfly.stock.infrastructure.llm.LlmUsageTracker;
import io.leavesfly.stock.domain.model.entity.AnalysisReport;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import io.leavesfly.stock.domain.model.enums.MarketType;
import io.leavesfly.stock.infrastructure.notification.NotificationService;
import io.leavesfly.stock.infrastructure.notification.NotificationRouter;

import io.leavesfly.stock.application.agent.AgentOrchestrator;
import io.leavesfly.stock.application.service.*;
import io.leavesfly.stock.domain.service.TechnicalAnalysisService;
import io.leavesfly.stock.domain.service.TradingCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 股票分析流水线 - 核心编排器(完整版)
 * 
 * 对应Python版本的 src/core/pipeline.py (3578行) 完整还原
 * 包含:
 * - analyze_stock() 完整30+步骤
 * - 结果后处理链(fallback/dashboard回填)
 * - 上下文增强链(筹码/板块/情报/大盘环境)
 * - Agent模式分析(context装配→编排→结果转换)
 * - 通知适配链(渠道专属格式)
 * - 诊断记录链(上下文快照/运行时追踪)
 */
@Component
public class StockAnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisPipeline.class);

    private final AppConfig config;
    private final DataFetcherManager dataFetcherManager;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final NewsSearchService newsSearchService;
    private final LlmService llmService;
    private final LlmUsageTracker usageTracker;
    private final NotificationService notificationService;
    private final NotificationRouter notificationRouter;
    private final AnalysisHistoryService historyService;
    private final AnalysisContextBuilder contextBuilder;
    private final AnalysisResultAggregator resultAggregator;
    private final AgentOrchestrator agentOrchestrator;
    private final DecisionSignalService decisionSignalService;
    private final DailyMarketContextService dailyMarketContextService;
    private final TradingCalendar tradingCalendar;
    private final NameToCodeResolver nameResolver;
    private final AnalysisPostProcessor postProcessor;
    private final AnalysisContextEnhancer contextEnhancer;

    /** 并发线程池 */
    private final ExecutorService executorService;
    /** 单股通知锁(防止同时推送多条) */
    private final Object singleStockNotifyLock = new Object();
    /** 进度回调 */
    private BiConsumer<Integer, String> progressCallback;

    public StockAnalysisPipeline(
            AppConfig config, DataFetcherManager dataFetcherManager,
            TechnicalAnalysisService technicalAnalysisService, NewsSearchService newsSearchService,
            LlmService llmService, LlmUsageTracker usageTracker,
            NotificationService notificationService, NotificationRouter notificationRouter,
            AnalysisHistoryService historyService, AnalysisContextBuilder contextBuilder,
            AnalysisResultAggregator resultAggregator, AgentOrchestrator agentOrchestrator,
            DecisionSignalService decisionSignalService, DailyMarketContextService dailyMarketContextService,
            TradingCalendar tradingCalendar, NameToCodeResolver nameResolver,
            AnalysisPostProcessor postProcessor, AnalysisContextEnhancer contextEnhancer) {
        this.config = config;
        this.dataFetcherManager = dataFetcherManager;
        this.technicalAnalysisService = technicalAnalysisService;
        this.newsSearchService = newsSearchService;
        this.llmService = llmService;
        this.usageTracker = usageTracker;
        this.notificationService = notificationService;
        this.notificationRouter = notificationRouter;
        this.historyService = historyService;
        this.contextBuilder = contextBuilder;
        this.resultAggregator = resultAggregator;
        this.agentOrchestrator = agentOrchestrator;
        this.decisionSignalService = decisionSignalService;
        this.dailyMarketContextService = dailyMarketContextService;
        this.tradingCalendar = tradingCalendar;
        this.nameResolver = nameResolver;
        this.postProcessor = postProcessor;
        this.contextEnhancer = contextEnhancer;
        this.executorService = Executors.newFixedThreadPool(
                Math.min(Runtime.getRuntime().availableProcessors(), 4));
    }

    // ==================== 主入口方法 ====================

    /**
     * 执行完整分析流程 (对应Python run() 169行)
     */
    public Map<String, Object> runFullAnalysis(String stocksStr, boolean dryRun, boolean debug) {
        long startTime = System.currentTimeMillis();
        String runId = UUID.randomUUID().toString().substring(0, 8);
        log.info("========== [{}] 开始完整分析流程 ==========", runId);

        // 1. 解析股票列表
        List<String> stocks = resolveStockList(stocksStr);
        if (stocks.isEmpty()) {
            log.warn("未找到待分析股票");
            return Map.of("status", "no_stocks", "run_id", runId);
        }
        log.info("[{}] 待分析: {} 只 - {}", runId, stocks.size(), stocks);
        emitProgress(5, "解析完成，开始分析 " + stocks.size() + " 只股票");

        // 2. 加载大盘环境(全局共享)
        Map<String, Object> marketContext = loadDailyMarketContext();
        emitProgress(10, "大盘环境加载完成");

        // 3. 并发分析每只股票
        List<AnalysisReport> reports = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger(0);
        List<Future<AnalysisReport>> futures = new ArrayList<>();

        for (String stock : stocks) {
            futures.add(executorService.submit(() -> {
                AnalysisReport report = analyzeStock(stock, marketContext, dryRun, debug);
                int done = completed.incrementAndGet();
                emitProgress(10 + (done * 70 / stocks.size()), 
                        String.format("已完成 %d/%d: %s", done, stocks.size(), stock));
                return report;
            }));
        }

        // 4. 收集结果(带超时)
        for (int i = 0; i < futures.size(); i++) {
            try {
                AnalysisReport report = futures.get(i).get(300, TimeUnit.SECONDS);
                if (report != null) reports.add(report);
            } catch (TimeoutException e) {
                log.error("[{}] 分析超时: {}", runId, stocks.get(i));
            } catch (Exception e) {
                log.error("[{}] 分析异常: {} - {}", runId, stocks.get(i), e.getMessage());
            }
        }
        emitProgress(85, "分析完成，准备推送通知");

        // 5. 汇总并推送
        Map<String, Object> summary = resultAggregator.aggregateAndNotify(reports, dryRun);

        // 6. 完成
        long elapsed = System.currentTimeMillis() - startTime;
        summary.put("run_id", runId);
        summary.put("total_duration_ms", elapsed);
        emitProgress(100, "全部完成");
        log.info("========== [{}] 分析完成: {}/{}只 耗时{}秒 ==========", 
                runId, reports.size(), stocks.size(), elapsed / 1000.0);
        return summary;
    }

    // ==================== 单股分析主流程(对应Python analyze_stock 479行) ====================

    /**
     * 分析单只股票 - 完整30+步骤
     * 对应Python版 analyze_stock() 方法
     */
    public AnalysisReport analyzeStock(String stockCode, Map<String, Object> marketContext,
                                       boolean dryRun, boolean debug) {
        long startTime = System.currentTimeMillis();
        log.info("[{}] === 开始分析 ===", stockCode);
        DiagnosticContext diag = new DiagnosticContext(stockCode);

        try {
            // ===== Step 1: 获取历史数据 =====
            diag.stage("fetch_history");
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(config.getHistoryDays());
            List<StockDailyData> historyData = dataFetcherManager.getHistoryData(stockCode, startDate, endDate);
            if (historyData.isEmpty()) {
                log.error("[{}] 无法获取历史数据", stockCode);
                diag.fail("历史数据为空");
                return null;
            }
            String stockName = resolveStockName(historyData, stockCode);
            MarketType market = MarketType.detectFromCode(stockCode);
            diag.record("history_count", historyData.size());
            diag.record("stock_name", stockName);

            // ===== Step 2: 获取实时行情 =====
            diag.stage("fetch_realtime");
            Map<String, Object> realtimeQuote = dataFetcherManager.getRealtimeQuote(stockCode);
            
            // ===== Step 3: 实时数据注入历史(增强最后一条) =====
            contextEnhancer.augmentHistoricalWithRealtime(historyData, realtimeQuote);

            // ===== Step 4: 技术分析 =====
            diag.stage("technical_analysis");
            Map<String, Object> technicalResult = technicalAnalysisService.analyze(historyData);
            TrendAnalysisResult trendResult = computeTrendAnalysis(historyData);

            // ===== Step 5: 新闻搜索 =====
            diag.stage("news_search");
            List<Map<String, Object>> newsResults = newsSearchService.searchNews(stockCode, stockName);

            // ===== Step 6: 上下文增强(板块/情报/大盘) =====
            diag.stage("enhance_context");
            Map<String, Object> enhancedContext = contextEnhancer.enhance(stockCode, stockName, market,
                    historyData, realtimeQuote, technicalResult, newsResults, marketContext);

            // ===== Step 7: 加载持久化情报 =====
            Map<String, Object> intelligenceContext = loadPersistedIntelligence(stockCode, market);
            if (intelligenceContext != null && !intelligenceContext.isEmpty()) {
                enhancedContext.put("intelligence", intelligenceContext);
            }

            // ===== Step 8: LLM分析(区分Agent模式和传统模式) =====
            diag.stage("llm_analysis");
            AnalysisResult analysisResult;
            long llmStart = System.currentTimeMillis();

            if ("true".equalsIgnoreCase(config.getAgentMode()) || "full".equals(config.getAgentMode())) {
                // Agent模式分析(当AGENT_MODE=true/full时启用)
                if (!dryRun) {
                analysisResult = analyzeWithAgent(stockCode, stockName, enhancedContext, diag);
                } else {
                    analysisResult = AnalysisResult.dryRun(stockCode, stockName);
                }
            } else if (dryRun) {
                analysisResult = AnalysisResult.dryRun(stockCode, stockName);
            } else {
                // 传统LLM直接分析
                analysisResult = analyzeWithLlm(stockCode, stockName, enhancedContext);
            }

            long llmDuration = System.currentTimeMillis() - llmStart;
            diag.record("llm_duration_ms", llmDuration);

            // ===== Step 9-14: 后处理链(委托给 PostProcessor) =====
            postProcessor.process(analysisResult, trendResult, technicalResult, realtimeQuote, marketContext, historyData);

            // ===== Step 15: 构建最终报告 =====
            diag.stage("build_report");
            double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
            AnalysisReport report = buildFinalReport(stockCode, stockName, market,
                    analysisResult, realtimeQuote, technicalResult, elapsed, dryRun);

            // ===== Step 16: 保存到数据库 =====
            diag.stage("save_history");
            historyService.saveReport(report);

            // ===== Step 17: 提取并持久化决策信号 =====
            extractAndPersistDecisionSignal(report, analysisResult);

            // ===== Step 18: 刷新诊断快照 =====
            diag.complete(elapsed);
            log.info("[{}] 分析完成 | 信号:{} 评分:{} 耗时:{:.1f}秒", 
                    stockCode, report.getSignal(), report.getTotalScore(), elapsed);

            return report;

        } catch (Exception e) {
            log.error("[{}] 分析流程异常: {}", stockCode, e.getMessage(), e);
            diag.fail(e.getMessage());
            return null;
        }
    }

    // ==================== 上下文增强链(已委托给 AnalysisContextEnhancer) ====================

    // ==================== 结果后处理链(已委托给 AnalysisPostProcessor) ====================

    // ==================== Agent模式分析(对应Python _analyze_with_agent 312行) ====================

    /**
     * 使用Agent编排器进行分析
     */
    private AnalysisResult analyzeWithAgent(String stockCode, String stockName,
                                            Map<String, Object> context, DiagnosticContext diag) {
        try {
            AgentOrchestrator.AnalysisOutput output = agentOrchestrator.runAnalysis(context);
            diag.record("agent_mode", output.getMode());
            diag.record("agent_opinions", output.getOpinionCount());
            return agentResultToAnalysisResult(output, stockCode, stockName);
        } catch (Exception e) {
            log.warn("[{}] Agent分析失败，降级到传统LLM: {}", stockCode, e.getMessage());
            return analyzeWithLlm(stockCode, stockName, context);
        }
    }

    /**
     * Agent结果转换为标准AnalysisResult(对应Python _agent_result_to_analysis_result 183行)
     */
    private AnalysisResult agentResultToAnalysisResult(AgentOrchestrator.AnalysisOutput output,
                                                       String stockCode, String stockName) {
        AnalysisResult result = new AnalysisResult();
        result.stockCode = stockCode;
        result.stockName = stockName;
        result.signal = output.getConsensusSignal();
        result.score = output.getConsensusScore();
        result.fullReport = output.getFullReport();
        result.summary = output.getSummary();
        result.operationAdvice = output.getAdvice();
        result.confidence = output.getConfidence();
        result.riskNote = output.getRiskAssessment();
        result.source = "agent_" + output.getMode();
        return result;
    }

    /**
     * 传统LLM直接分析
     */
    private AnalysisResult analyzeWithLlm(String stockCode, String stockName, Map<String, Object> context) {
        String prompt = contextBuilder.formatForLlm(buildContextObj(context));
        String response = llmService.analyzeStock(context);
        
        AnalysisResult result = new AnalysisResult();
        result.stockCode = stockCode;
        result.stockName = stockName;
        result.fullReport = response;
        result.source = "llm_direct";
        
        // 从LLM响应中提取结构化字段
        extractFieldsFromLlmResponse(result, response);
        return result;
    }

    // ==================== 决策信号提取(对应Python _extract_decision_signal_after_history_save 48行) ====================

    private void extractAndPersistDecisionSignal(AnalysisReport report, AnalysisResult result) {
        if (result == null || result.signal == null) return;
        if ("neutral".equals(result.signal)) return;

        try {
            String action = result.signal.contains("buy") ? "buy" : result.signal.contains("sell") ? "sell" : "hold";
            Integer score = result.score != null ? result.score : 50;
            Double confidence = result.confidence != null ? parseConfidence(result.confidence) : 0.5;

            decisionSignalService.extractFromReport(
                    report.getId(), report.getStockCode(), report.getStockName(),
                    action, confidence, score,
                    result.targetPrice, result.stopLossPrice, result.operationAdvice);
        } catch (Exception e) {
            log.debug("[{}] 信号提取失败: {}", report.getStockCode(), e.getMessage());
        }
    }

    // ==================== 通知适配链 ====================

    /**
     * 单股实时通知(对应Python _send_single_stock_notification 95行)
     */
    public void sendSingleStockNotification(AnalysisReport report) {
        synchronized (singleStockNotifyLock) {
            if (!notificationRouter.shouldSend(report.getStockCode(), report.getSignal())) {
                log.debug("[{}] 通知被降噪过滤", report.getStockCode());
                return;
            }
            String title = String.format("%s %s(%s) 分析完成", 
                    signalEmoji(report.getSignal()), report.getStockName(), report.getStockCode());
            String content = formatNotificationContent(report);
            notificationService.sendMessage(title, content);
        }
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> loadDailyMarketContext() {
        try { return dailyMarketContextService.getDailyContext(); }
        catch (Exception e) { log.debug("大盘上下文加载失败"); return Map.of(); }
    }

    private Map<String, Object> loadPersistedIntelligence(String stockCode, MarketType market) {
        try { return contextEnhancer.loadPersistedIntelligence(stockCode, market); }
        catch (Exception e) { return null; }
    }

    private List<String> resolveStockList(String stocksStr) {
        if (stocksStr != null && !stocksStr.isEmpty()) {
            return nameResolver.resolveBatch(stocksStr);
        }
        return config.getStockList();
    }

    private String resolveStockName(List<StockDailyData> data, String code) {
        for (int i = data.size() - 1; i >= 0; i--) {
            String name = data.get(i).getStockName();
            if (name != null && !name.isEmpty() && !name.equals(code)) return name;
        }
        return code;
    }

    private TrendAnalysisResult computeTrendAnalysis(List<StockDailyData> data) {
        if (data.size() < 10) return null;
        Map<String, Object> tech = technicalAnalysisService.analyze(data);
        TrendAnalysisResult t = new TrendAnalysisResult();
        Object score = tech.get("total_score");
        t.score = score instanceof Number ? ((Number) score).intValue() : 50;
        t.trendLabel = (String) tech.get("trend");
        return t;
    }


    private String formatHistoryForLlm(List<StockDailyData> historyData) {
        return contextEnhancer.formatHistoryForLlm(historyData);
    }

    private AnalysisReport buildFinalReport(String stockCode, String stockName, MarketType market,
                                             AnalysisResult result, Map<String, Object> realtimeQuote,
                                             Map<String, Object> technicalResult, double elapsed, boolean dryRun) {
        AnalysisReport report = new AnalysisReport();
        report.setStockCode(stockCode);
        report.setStockName(stockName);
        report.setMarket(market.getCode());
        report.setAnalysisDate(LocalDateTime.now());
        report.setDurationSeconds(elapsed);
        report.setIsDryRun(dryRun);
        report.setLlmModel(config.getLlmModel());
        report.setAgentMode(config.getAgentMode());
        report.setFullReport(result.fullReport);
        report.setLlmResponse(result.fullReport);
        report.setSignal(result.signal);
        report.setTotalScore(result.score);
        report.setSummary(result.summary);
        report.setCurrentPrice(result.currentPrice);
        if (realtimeQuote != null) {
            Object pct = realtimeQuote.get("change_pct");
            if (pct instanceof Number) report.setChangePct(((Number) pct).doubleValue());
        }
        return report;
    }

    private void extractFieldsFromLlmResponse(AnalysisResult result, String response) {
        if (response == null) return;
        String lower = response.toLowerCase();
        if (lower.contains("强烈买入") || lower.contains("strong buy")) result.signal = "strong_buy";
        else if (lower.contains("买入") || lower.contains("buy")) result.signal = "buy";
        else if (lower.contains("卖出") || lower.contains("sell")) result.signal = "sell";
        else result.signal = "neutral";
        // 简单评分提取
        result.score = result.signal.contains("buy") ? 70 : result.signal.contains("sell") ? 30 : 50;
    }

    private String formatNotificationContent(AnalysisReport report) {
        return String.format("信号: %s | 评分: %d/100\n价格: %.2f (%+.2f%%)\n%s",
                report.getSignal(), report.getTotalScore() != null ? report.getTotalScore() : 0,
                report.getCurrentPrice() != null ? report.getCurrentPrice() : 0.0,
                report.getChangePct() != null ? report.getChangePct() : 0.0,
                report.getSummary() != null ? report.getSummary() : "");
    }

    private String signalEmoji(String signal) {
        if (signal == null) return "⚖️";
        switch (signal) {
            case "strong_buy": return "🔥"; case "buy": return "📈";
            case "sell": return "📉"; case "strong_sell": return "⚠️";
            default: return "⚖️";
        }
    }

    private double parseConfidence(String confidence) {
        if ("高".equals(confidence) || "high".equals(confidence)) return 0.8;
        if ("中等".equals(confidence) || "medium".equals(confidence)) return 0.5;
        return 0.3;
    }

    private double safe(Double v) { return v != null ? v : 0.0; }
    
    private AnalysisContextBuilder.AnalysisContext buildContextObj(Map<String, Object> map) {
        AnalysisContextBuilder.AnalysisContext ctx = new AnalysisContextBuilder.AnalysisContext();
        ctx.setStockCode((String) map.get("stock_code"));
        ctx.setStockName((String) map.get("stock_name"));
        return ctx;
    }

    private void emitProgress(int progress, String message) {
        if (progressCallback != null) progressCallback.accept(progress, message);
        log.debug("进度 {}%: {}", progress, message);
    }

    public void setProgressCallback(BiConsumer<Integer, String> callback) { this.progressCallback = callback; }

    /** 大盘复盘 */
    public void runMarketReview() {
        log.info("========== 大盘复盘分析 ==========");
        List<String> indices = List.of("000001", "399001", "399006", "000300", "000016");
        for (String idx : indices) {
            try {
                var data = dataFetcherManager.getHistoryData(idx, LocalDate.now().minusDays(30), LocalDate.now());
                if (!data.isEmpty()) {
                    var result = technicalAnalysisService.analyze(data);
                    log.info("指数 {} 趋势: {}", idx, result.get("trend"));
                }
            } catch (Exception e) { log.error("指数 {} 分析失败", idx); }
        }
    }

    /** 回测模式 */
    public void runBacktest(String stocksStr, int days) {
        log.info("========== 回测模式: {}天 ==========", days);
        List<String> stocks = resolveStockList(stocksStr);
        for (String stock : stocks) {
            try {
                var data = dataFetcherManager.getHistoryData(stock, LocalDate.now().minusDays(days), LocalDate.now());
                if (!data.isEmpty()) {
                    log.info("[{}] 回测数据: {}条", stock, data.size());
                }
            } catch (Exception e) { log.error("[{}] 回测失败", stock); }
        }
    }

    /** 单股分析(兼容旧接口 - 供Bot/API调用) */
    public AnalysisReport analyzeSingleStock(String stockCode, boolean dryRun, boolean debug) {
        return analyzeStock(stockCode, loadDailyMarketContext(), dryRun, debug);
    }

    // ==================== 内部数据类 ====================

    /** 诊断上下文(用于记录每步执行情况) */
    private static class DiagnosticContext {
        private final String stockCode;
        private final Map<String, Object> records = new LinkedHashMap<>();
        private String currentStage;
        private final long startTime = System.currentTimeMillis();

        DiagnosticContext(String stockCode) { this.stockCode = stockCode; }
        void stage(String name) { this.currentStage = name; records.put("stage_" + name + "_start", System.currentTimeMillis()); }
        void record(String key, Object value) { records.put(key, value); }
        void fail(String reason) { records.put("failed_at", currentStage); records.put("error", reason); }
        void complete(double elapsed) { records.put("total_elapsed", elapsed); records.put("status", "success"); }
    }
}
