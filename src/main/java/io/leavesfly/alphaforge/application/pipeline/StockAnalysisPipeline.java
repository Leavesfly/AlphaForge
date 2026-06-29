package io.leavesfly.alphaforge.application.pipeline;

import io.leavesfly.alphaforge.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.pipeline.AnalysisPostProcessor.AnalysisResult;
import io.leavesfly.alphaforge.application.pipeline.AnalysisPostProcessor.TrendAnalysisResult;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import io.leavesfly.alphaforge.domain.service.port.LlmUsagePort;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.service.port.NotificationPort;

import io.leavesfly.alphaforge.application.agent.ReActAgent;
import io.leavesfly.alphaforge.application.agent.MultiAgentOrchestrator;
import io.leavesfly.alphaforge.application.agent.debate.AgentDebateOrchestrator;
import io.leavesfly.alphaforge.application.agent.debate.DebateResult;
import io.leavesfly.alphaforge.application.service.feedback.ExperienceMemory;
import io.leavesfly.alphaforge.application.service.memory.AnalysisMemoryService;
import io.leavesfly.alphaforge.application.service.market.NewsSearchService;
import io.leavesfly.alphaforge.application.service.market.DailyMarketContextService;
import io.leavesfly.alphaforge.application.service.report.AnalysisHistoryService;
import io.leavesfly.alphaforge.application.service.signal.DecisionSignalService;
import io.leavesfly.alphaforge.application.service.loop.LoopStateManager;
import io.leavesfly.alphaforge.domain.service.NameToCodeResolver;
import io.leavesfly.alphaforge.domain.service.TechnicalAnalysisService;
import io.leavesfly.alphaforge.domain.service.TradingCalendar;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final MarketDataPort dataFetcherManager;
    private final TechnicalAnalysisService technicalAnalysisService;
    private final NewsSearchService newsSearchService;
    private final LlmPort llmService;
    private final LlmUsagePort usageTracker;
    private final NotificationPort notificationService;
    private final AnalysisHistoryService historyService;
    private final AnalysisContextBuilder contextBuilder;
    private final AnalysisResultAggregator resultAggregator;
    private final ReActAgent reactAgent;
    private final DecisionSignalService decisionSignalService;
    private final DailyMarketContextService dailyMarketContextService;
    private final TradingCalendar tradingCalendar;
    private final NameToCodeResolver nameResolver;
    private final AnalysisPostProcessor postProcessor;
    private final AnalysisContextEnhancer contextEnhancer;
    private final PipelineMetrics pipelineMetrics;
    private final SignalVerifier signalVerifier;
    private final LoopStateManager loopStateManager;
    private final ExperienceMemory experienceMemory;

    /** 可选依赖：分析记忆服务（字段注入，避免构造函数过度膨胀） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AnalysisMemoryService analysisMemoryService;

    /** 可选依赖：多 Agent 编排器 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MultiAgentOrchestrator multiAgentOrchestrator;

    /** 可选依赖：Agent 辩论编排器（当 agentMode=debate 时启用） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentDebateOrchestrator debateOrchestrator;

    /** JSON 解析器（用于从 LLM 响应提取结构化字段） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 并发线程池 */
    private final ExecutorService executorService;
    /** 单股通知锁(防止同时推送多条) */
    private final Object singleStockNotifyLock = new Object();
    /** 进度回调 */
    private BiConsumer<Integer, String> progressCallback;

    public StockAnalysisPipeline(
            AppConfig config, MarketDataPort dataFetcherManager,
            TechnicalAnalysisService technicalAnalysisService, NewsSearchService newsSearchService,
            LlmPort llmService, LlmUsagePort usageTracker,
            NotificationPort notificationService,
            AnalysisHistoryService historyService, AnalysisContextBuilder contextBuilder,
            AnalysisResultAggregator resultAggregator, ReActAgent reactAgent,
            DecisionSignalService decisionSignalService, DailyMarketContextService dailyMarketContextService,
            TradingCalendar tradingCalendar, NameToCodeResolver nameResolver,
            AnalysisPostProcessor postProcessor, AnalysisContextEnhancer contextEnhancer,
            PipelineMetrics pipelineMetrics, SignalVerifier signalVerifier,
            LoopStateManager loopStateManager, ExperienceMemory experienceMemory) {
        this.config = config;
        this.dataFetcherManager = dataFetcherManager;
        this.technicalAnalysisService = technicalAnalysisService;
        this.newsSearchService = newsSearchService;
        this.llmService = llmService;
        this.usageTracker = usageTracker;
        this.notificationService = notificationService;
        this.historyService = historyService;
        this.contextBuilder = contextBuilder;
        this.resultAggregator = resultAggregator;
        this.reactAgent = reactAgent;
        this.decisionSignalService = decisionSignalService;
        this.dailyMarketContextService = dailyMarketContextService;
        this.tradingCalendar = tradingCalendar;
        this.nameResolver = nameResolver;
        this.postProcessor = postProcessor;
        this.contextEnhancer = contextEnhancer;
        this.pipelineMetrics = pipelineMetrics;
        this.signalVerifier = signalVerifier;
        this.loopStateManager = loopStateManager;
        this.experienceMemory = experienceMemory;
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

            // ===== Step 8: LLM分析(统一使用ReactAgent) =====
            diag.stage("llm_analysis");
            AnalysisResult analysisResult;
            long llmStart = System.currentTimeMillis();

            if (dryRun) {
                analysisResult = AnalysisResult.dryRun(stockCode, stockName);
            } else {
                analysisResult = analyzeWithAgent(stockCode, stockName, enhancedContext, diag);
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
            extractAndPersistDecisionSignal(report, analysisResult);

            // ===== Step 18: 刷新诊断快照 =====
            diag.complete(elapsed);
            pipelineMetrics.recordPhase(stockCode, "analyze_stock", System.currentTimeMillis() - startTime);
            log.info("[{}] 分析完成 | 信号:{} 评分:{} 耗时:{}秒",
                    stockCode, report.getSignal(), report.getTotalScore(), String.format("%.1f", elapsed));

            // ===== Step 19: 记录跨轮次经验（供系统自我进化） =====
            try {
                String sentiment = marketContext != null
                        ? String.valueOf(marketContext.getOrDefault("market_sentiment", "")) : null;
                experienceMemory.recordExperience(
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

    // ==================== 上下文增强链(已委托给 AnalysisContextEnhancer) ====================

    // ==================== 结果后处理链(已委托给 AnalysisPostProcessor) ====================

    // ==================== Agent模式分析 ====================

    /**
     * 使用ReactAgent进行分析（LLM自主调用工具获取数据）
     */
    private AnalysisResult analyzeWithAgent(String stockCode, String stockName,
                                            Map<String, Object> context, DiagnosticContext diag) {
        // 如果配置了多 Agent 模式且编排器可用，优先使用多 Agent 并行分析
        String agentMode = config.getAgentMode();

        // 辩论模式：多 Agent 独立分析 → 交叉质询 → 裁判裁决
        if (debateOrchestrator != null && "debate".equals(agentMode)) {
            try {
                DebateResult debateResult = debateOrchestrator.orchestrateWithDebate(
                        stockCode, stockName, context, 120);
                diag.record("agent_mode", "debate");
                diag.record("agent_count", debateResult.getInitialResults().size());
                diag.record("debate_enabled", debateResult.isDebateEnabled());
                diag.record("debate_duration_ms", debateResult.getTotalDurationMs());
                if (debateResult.getVerdict() != null) {
                    diag.record("debate_consensus", debateResult.getVerdict().getConsensusLevel());
                    diag.record("debate_signal_adjusted", debateResult.getVerdict().isSignalDowngraded());
                }
                AnalysisResult result = new AnalysisResult();
                result.stockCode = stockCode;
                result.stockName = stockName;
                result.fullReport = debateResult.getFinalAnalysis();
                result.source = "debate_agent";
                extractFieldsFromLlmResponse(result, debateResult.getFinalAnalysis());
                // 辩论裁决覆盖信号和评分
                if (debateResult.getVerdict() != null) {
                    result.signal = debateResult.getVerdict().getFinalSignal();
                    result.score = debateResult.getVerdict().getFinalScore();
                    result.confidence = debateResult.getVerdict().getConfidence();
                }
                return result;
            } catch (Exception e) {
                log.warn("[{}] 辩论模式分析失败，降级到多Agent: {}", stockCode, e.getMessage());
            }
        }

        if (multiAgentOrchestrator != null && ("multi".equals(agentMode) || "full".equals(agentMode) || "debate".equals(agentMode))) {
            try {
                MultiAgentOrchestrator.OrchestrationResult orcResult =
                        multiAgentOrchestrator.orchestrate(stockCode, stockName, context, 120);
                diag.record("agent_mode", "multi_agent");
                diag.record("agent_count", orcResult.agentResults().size());
                diag.record("agent_duration_ms", orcResult.durationMs());
                AnalysisResult result = new AnalysisResult();
                result.stockCode = stockCode;
                result.stockName = stockName;
                result.fullReport = orcResult.synthesis();
                result.source = "multi_agent";
                extractFieldsFromLlmResponse(result, orcResult.synthesis());
                return result;
            } catch (Exception e) {
                log.warn("[{}] 多Agent分析失败，降级到ReactAgent: {}", stockCode, e.getMessage());
            }
        }

        try {
            ReActAgent.ReactResult reactResult = reactAgent.analyze(stockCode, stockName, context);
            diag.record("agent_mode", "react");
            diag.record("agent_tool_calls", reactResult.totalToolCalls());
            diag.record("agent_duration_ms", reactResult.durationMs());
            return reactResultToAnalysisResult(reactResult, stockCode, stockName);
        } catch (Exception e) {
            log.warn("[{}] ReactAgent分析失败，降级到传统LLM: {}", stockCode, e.getMessage());
            return analyzeWithLlm(stockCode, stockName, context);
        }
    }

    /**
     * ReactAgent结果转换为标准AnalysisResult
     */
    private AnalysisResult reactResultToAnalysisResult(ReActAgent.ReactResult reactResult,
                                                       String stockCode, String stockName) {
        AnalysisResult result = new AnalysisResult();
        result.stockCode = stockCode;
        result.stockName = stockName;
        result.fullReport = reactResult.response();
        result.source = "react_agent";
        // 从LLM回复中提取结构化字段（信号/评分）
        extractFieldsFromLlmResponse(result, reactResult.response());
        return result;
    }

    /**
     * 传统LLM直接分析
     */
    private AnalysisResult analyzeWithLlm(String stockCode, String stockName, Map<String, Object> context) {
        // 优先使用结构化输出（JSON Mode），fallback 到传统 analyzeStock
        String response;
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", (Object) "你是一位专业的股票分析师。请以JSON格式返回分析结论。"));
            messages.add(Map.of("role", "user", "content", (Object) buildLlmUserPrompt(stockCode, stockName, context)));
            response = llmService.chatForStructuredOutput(messages, null);
            if (response == null || response.isEmpty() || "{}".equals(response)) {
                response = llmService.analyzeStock(context);
            }
        } catch (Exception e) {
            log.debug("[{}] 结构化输出失败，回退到传统模式: {}", stockCode, e.getMessage());
            response = llmService.analyzeStock(context);
        }

        AnalysisResult result = new AnalysisResult();
        result.stockCode = stockCode;
        result.stockName = stockName;
        result.fullReport = response;
        result.source = "llm_structured";

        extractFieldsFromLlmResponse(result, response);
        return result;
    }

    /** 为结构化输出构建用户提示 */
    private String buildLlmUserPrompt(String stockCode, String stockName, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("请分析股票 %s(%s)。\n", stockName, stockCode));
        if (context != null) {
            Object tech = context.get("technical_analysis");
            if (tech != null) sb.append("技术分析:").append(tech).append("\n");
            Object quote = context.get("realtime_quote");
            if (quote != null) sb.append("实时行情:").append(quote).append("\n");
        }
        sb.append("请以JSON返回: {\"signal\":\"...\",\"score\":0-100,\"confidence\":\"...\",\"summary\":\"...\",\"operation_advice\":\"...\",\"risk_note\":\"...\"}");
        return sb.toString();
    }

    // ==================== 决策信号提取 ====================

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
     * 单股实时通知
     */
    public void sendSingleStockNotification(AnalysisReport report) {
        synchronized (singleStockNotifyLock) {
            if (!notificationService.shouldSend(report.getStockCode(), report.getSignal())) {
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
        if (response == null || response.isEmpty()) {
            result.signal = "neutral";
            result.score = 50;
            return;
        }

        // 优先尝试从 JSON 中提取（结构化输出模式）
        if (tryExtractFromJson(result, response)) {
            return;
        }

        // Fallback: 从文本中提取
        extractFromText(result, response);
    }

    /** 尝试从 LLM 响应中解析 JSON 并提取结构化字段 */
    private boolean tryExtractFromJson(AnalysisResult result, String response) {
        try {
            String json = extractJsonObject(response);
            if (json == null) return false;

            JsonNode node = objectMapper.readTree(json);

            // 提取信号
            String signal = node.path("signal").asText("");
            if (!signal.isEmpty()) {
                result.signal = normalizeSignal(signal);
            }

            // 提取评分（不再硬编码！从 LLM 输出中获取真实评分）
            int score = node.path("score").asInt(-1);
            if (score >= 0 && score <= 100) {
                result.score = score;
            }

            // 提取其他结构化字段
            String confidence = node.path("confidence").asText(null);
            if (confidence != null && !confidence.isEmpty()) result.confidence = confidence;

            String summary = node.path("summary").asText(null);
            if (summary != null && !summary.isEmpty()) result.summary = summary;

            String advice = node.path("operation_advice").asText(null);
            if (advice != null && !advice.isEmpty()) result.operationAdvice = advice;

            String riskNote = node.path("risk_note").asText(null);
            if (riskNote != null && !riskNote.isEmpty()) result.riskNote = riskNote;

            JsonNode targetPrice = node.path("target_price");
            if (targetPrice.isNumber()) result.targetPrice = targetPrice.asDouble();

            JsonNode stopLoss = node.path("stop_loss_price");
            if (stopLoss.isNumber()) result.stopLossPrice = stopLoss.asDouble();

            return result.signal != null && result.score != null;
        } catch (Exception e) {
            log.debug("JSON 提取失败，回退到文本匹配: {}", e.getMessage());
            return false;
        }
    }

    /** 从文本中提取 JSON 对象 */
    private String extractJsonObject(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        // 去除 markdown 代码块
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence > 0) trimmed = trimmed.substring(0, lastFence);
            trimmed = trimmed.trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return null;
    }

    /** 信号标准化 */
    private String normalizeSignal(String signal) {
        String s = signal.toLowerCase().trim();
        if (s.contains("strong") && s.contains("buy")) return "strong_buy";
        if (s.contains("strong") && s.contains("sell")) return "strong_sell";
        if (s.contains("buy") || s.contains("买入")) return "buy";
        if (s.contains("sell") || s.contains("卖出")) return "sell";
        if (s.contains("hold") || s.contains("neutral") || s.contains("中性")) return "neutral";
        return s;
    }

    /** 文本模式提取（Fallback） */
    private void extractFromText(AnalysisResult result, String response) {
        String lower = response.toLowerCase();
        if (lower.contains("强烈买入") || lower.contains("strong buy")) {
            result.signal = "strong_buy";
        } else if (lower.contains("买入") || lower.contains("buy")) {
            result.signal = "buy";
        } else if (lower.contains("卖出") || lower.contains("sell")) {
            result.signal = "sell";
        } else {
            result.signal = "neutral";
        }

        // 评分提取改进：尝试从文本中提取 "评分：85" 或 "score: 85"
        Integer extractedScore = extractScoreFromText(response);
        if (extractedScore != null) {
            result.score = extractedScore;
        } else if (result.score == null) {
            // 无 JSON 也无法提取评分时，使用更合理的默认值（不再硬编码 70/30）
            result.score = switch (result.signal) {
                case "strong_buy" -> 80;
                case "buy" -> 65;
                case "sell" -> 35;
                case "strong_sell" -> 20;
                default -> 50;
            };
        }
    }

    /** 从文本中提取评分数字 */
    private Integer extractScoreFromText(String response) {
        Pattern pattern = Pattern.compile("(?:评分|score|total_score)\\s*[:：]\\s*(\\d{1,3})");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            try {
                int score = Integer.parseInt(matcher.group(1));
                if (score >= 0 && score <= 100) return score;
            } catch (NumberFormatException ignored) {}
        }
        return null;
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
