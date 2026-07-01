package io.leavesfly.alphaforge.application.pipeline;

import io.leavesfly.alphaforge.config.LlmConfig;
import io.leavesfly.alphaforge.config.AppConfig;

import io.leavesfly.alphaforge.config.SchedulerAuthConfig;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisResult;
import io.leavesfly.alphaforge.domain.model.entity.analysis.TrendAnalysisResult;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.service.port.NotificationPort;


import io.leavesfly.alphaforge.application.service.feedback.SignalLearningService;
import io.leavesfly.alphaforge.application.service.memory.AnalysisMemoryService;
import io.leavesfly.alphaforge.application.service.market.NewsSearchService;
import io.leavesfly.alphaforge.application.service.market.MarketAnalysisService;
import io.leavesfly.alphaforge.application.service.report.AnalysisHistoryService;
import io.leavesfly.alphaforge.application.service.signal.SignalExtractionService;
import io.leavesfly.alphaforge.application.service.loop.LoopStateManager;
import io.leavesfly.alphaforge.domain.service.NameToCodeResolver;
import io.leavesfly.alphaforge.domain.service.TechnicalAnalysisService;
import io.leavesfly.alphaforge.domain.service.SignalVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 股票分析流水线 - 核心编排器(完整版)
 *
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
    private final LlmConfig llmConfig;
    private final SchedulerAuthConfig schedulerAuthConfig;
    private final MarketDataPort dataFetcherManager;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final NewsSearchService newsSearchService;
    private final NotificationPort notificationService;
    private final AnalysisHistoryService historyService;
    private final AnalysisContextBuilder contextBuilder;
    private final AnalysisResultAggregator resultAggregator;
    private final AgentAnalysisService agentAnalysisService;
    private final MarketAnalysisService marketAnalysisService;
    private final NameToCodeResolver nameResolver;
    private final AnalysisPostProcessor postProcessor;
    private final AnalysisContextEnhancer contextEnhancer;
    private final PipelineMetrics pipelineMetrics;
    private final SignalVerifier signalVerifier;
    private final LoopStateManager loopStateManager;
    private final SignalLearningService signalLearningService;
    private final SignalExtractionService signalExtractionService;

    /** 可选依赖：分析记忆服务（字段注入，避免构造函数过度膨胀） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AnalysisMemoryService analysisMemoryService;



    /** 并发线程池 */
    private final ExecutorService executorService;
    /** 进度回调 */
    private BiConsumer<Integer, String> progressCallback;

    public StockAnalysisPipeline(
            AppConfig config, LlmConfig llmConfig, SchedulerAuthConfig schedulerAuthConfig, MarketDataPort dataFetcherManager,
            TechnicalAnalysisService technicalAnalysisService, NewsSearchService newsSearchService,
            NotificationPort notificationService,
            AnalysisHistoryService historyService, AnalysisContextBuilder contextBuilder,
            AnalysisResultAggregator resultAggregator, AgentAnalysisService agentAnalysisService,
            MarketAnalysisService marketAnalysisService,
            NameToCodeResolver nameResolver,
            AnalysisPostProcessor postProcessor, AnalysisContextEnhancer contextEnhancer,
            PipelineMetrics pipelineMetrics, SignalVerifier signalVerifier,
            LoopStateManager loopStateManager, SignalLearningService signalLearningService,
            SignalExtractionService signalExtractionService) {
        this.config = config;
        this.llmConfig = llmConfig;
        this.schedulerAuthConfig = schedulerAuthConfig;
        this.dataFetcherManager = dataFetcherManager;
        this.technicalAnalysisService = technicalAnalysisService;
        this.newsSearchService = newsSearchService;
        this.notificationService = notificationService;
        this.historyService = historyService;
        this.contextBuilder = contextBuilder;
        this.resultAggregator = resultAggregator;
        this.agentAnalysisService = agentAnalysisService;
        this.marketAnalysisService = marketAnalysisService;
        this.nameResolver = nameResolver;
        this.postProcessor = postProcessor;
        this.contextEnhancer = contextEnhancer;
        this.pipelineMetrics = pipelineMetrics;
        this.signalVerifier = signalVerifier;
        this.loopStateManager = loopStateManager;
        this.signalLearningService = signalLearningService;
        this.signalExtractionService = signalExtractionService;
        this.executorService = Executors.newFixedThreadPool(
                Math.min(Runtime.getRuntime().availableProcessors(), 4));
    }

    /** 优雅关闭线程池，避免应用停止时线程泄漏 */
    @PreDestroy
    public void shutdown() {
        log.info("StockAnalysisPipeline 正在关闭线程池...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                log.warn("StockAnalysisPipeline 线程池强制关闭（仍有任务未完成）");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 主入口方法 ====================

    /**
     * 执行完整分析流程
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

    // ==================== 单股分析主流程 ====================

    /**
     * 分析单只股票 - 完整30+步骤
     */
    public AnalysisReport analyzeStock(String stockCode, Map<String, Object> marketContext,
                                       boolean dryRun, boolean debug) {
        long startTime = System.currentTimeMillis();
        log.info("[{}] === 开始分析 ===", stockCode);
        DiagnosticContext diag = new DiagnosticContext(stockCode);
        // （DiagnosticContext 已提取为独立类）

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

            // ===== Step 7.5: 跨轮记忆——注入上次分析结论供 Agent 参考 =====
            try {
                List<AnalysisReport> prevReports = historyService.getRecentReports(stockCode, 2);
                if (!prevReports.isEmpty()) {
                    AnalysisReport prev = prevReports.get(0);
                    String prevSummaryText = prev.getSummary() != null ? prev.getSummary() : "";
                    String prevSummary = String.format("[历史参考|%s] 信号:%s 评分:%d %s",
                            prev.getAnalysisDate(),
                            prev.getSignal() != null ? prev.getSignal() : "-",
                            prev.getTotalScore() != null ? prev.getTotalScore() : 0,
                            prevSummaryText.substring(0, Math.min(150, prevSummaryText.length())));
                    enhancedContext.put("last_analysis_summary", prevSummary);
                    log.debug("[{}] 历史参考已注入: {}",
                            stockCode, prevSummary.substring(0, Math.min(80, prevSummary.length())));
                }
            } catch (Exception e) {
                log.debug("[{}] 历史参考加载失败，跳过: {}", stockCode, e.getMessage());
            }

            // ===== Step 7.6: 因子经验注入（因子自进化经验 + 信号级经验统一注入） =====
            try {
                String marketPhase = marketContext != null
                        ? String.valueOf(marketContext.getOrDefault("market_phase", "未知")) : "未知";
                String factorHint = signalLearningService.buildUnifiedExperienceHint(
                        stockCode, technicalResult, marketPhase);
                if (factorHint != null && !factorHint.isBlank()) {
                    enhancedContext.put("factor_experience_hint", factorHint);
                    log.debug("[{}] 因子经验提示已注入", stockCode);
                }
            } catch (Exception e) {
                log.debug("[{}] 因子经验注入失败: {}", stockCode, e.getMessage());
            }

            // ===== Step 8: LLM分析(统一使用ReactAgent) =====
            diag.stage("llm_analysis");
            AnalysisResult analysisResult;
            long llmStart = System.currentTimeMillis();

            if (dryRun) {
                analysisResult = AnalysisResult.dryRun(stockCode, stockName);
            } else {
                analysisResult = agentAnalysisService.analyze(stockCode, stockName, enhancedContext, diag);
            }

            long llmDuration = System.currentTimeMillis() - llmStart;
            diag.record("llm_duration_ms", llmDuration);

            // ===== Step 8.5: 独立信号验证 (Maker-Checker 分离) =====
            diag.stage("signal_verify");
            if (!dryRun && analysisResult != null && analysisResult.signal != null) {
                SignalVerifier.VerificationResult vr = signalVerifier.verify(
                        analysisResult, technicalResult, marketContext, historyData, null);
                signalVerifier.applyVerification(analysisResult, vr);
                diag.record("verifier_passed", vr.passed);
                diag.record("verifier_confidence", vr.confidence);
                diag.record("verifier_consensus", String.format("%.0f%%", vr.consensusRatio * 100));
                if (vr.wasAdjusted()) {
                    diag.record("verifier_adjustment", vr.originalSignal + "→" + vr.adjustedSignal);
                    log.info("[{}] Verifier调整信号: {} → {} | {}",
                            stockCode, vr.originalSignal, vr.adjustedSignal, vr.adjustmentReason);
                }
                // 通知 LoopStateManager 记录 Verifier 统计（修复之前的断路）
                loopStateManager.onSignalVerified(vr.wasAdjusted());
            }

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
            signalExtractionService.persistDecisionSignal(report, analysisResult);

            // ===== Step 18: 刷新诊断快照 =====
            diag.complete(elapsed);
            pipelineMetrics.recordPhase(stockCode, "analyze_stock", System.currentTimeMillis() - startTime);
            log.info("[{}] 分析完成 | 信号:{} 评分:{} 耗时:{}秒",
                    stockCode, report.getSignal(), report.getTotalScore(), String.format("%.1f", elapsed));

            // ===== Step 19: 记录跨轮次经验（供系统自我进化） =====
            try {
                String sentiment = marketContext != null
                        ? String.valueOf(marketContext.getOrDefault("market_sentiment", "")) : null;
                signalLearningService.recordExperience(
                        stockCode, report.getSignal(),
                        report.getTotalScore() != null ? report.getTotalScore() : 50,
                        analysisResult.confidence, technicalResult, sentiment);
            } catch (Exception e) {
                log.debug("[{}] 记录经验失败: {}", stockCode, e.getMessage());
            }

            // ===== Step 20: 索引分析报告到向量记忆（如果启用） =====
            if (analysisMemoryService != null && analysisMemoryService.isEnabled()) {
                try {
                    analysisMemoryService.indexAnalysis(report);
                } catch (Exception e) {
                    log.debug("[{}] 索引分析报告失败: {}", stockCode, e.getMessage());
                }
            }

            return report;

        } catch (Exception e) {
            log.error("[{}] 分析流程异常: {}", stockCode, e.getMessage(), e);
            diag.fail(e.getMessage());
            return null;
        }
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> loadDailyMarketContext() {
        try { return marketAnalysisService.getDailyContext(); }
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
        report.setLlmModel(llmConfig.getLlmModel());
        report.setAgentMode(schedulerAuthConfig.getAgentMode());
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

    private void emitProgress(int progress, String message) {
        if (progressCallback != null) progressCallback.accept(progress, message);
        log.debug("进度 {}%: {}", progress, message);
    }

    public void setProgressCallback(BiConsumer<Integer, String> callback) { this.progressCallback = callback; }

     /** 单股分析(兼容旧接口 - 供Bot/API调用) */
    public AnalysisReport analyzeSingleStock(String stockCode, boolean dryRun, boolean debug) {
        return analyzeStock(stockCode, loadDailyMarketContext(), dryRun, debug);
    }

    // DiagnosticContext 已提取为独立类: io.leavesfly.alphaforge.application.pipeline.DiagnosticContext
}
