package io.leavesfly.alphaforge.application.evaluation;

import io.leavesfly.alphaforge.application.backtest.BacktestSimulationConfig;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.backtest.BacktestSimulator;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.application.strategy.model.WalkForwardResult;
import io.leavesfly.alphaforge.application.strategy.engine.WalkForwardValidator;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * 基准评估套件 — 统一运行策略评估并生成对比报告
 *
 * 对应论文：
 * - AlphaForgeBench: 端到端交易策略设计评测
 * - DeepFund: 实盘竞技视角的持续评估
 * - QuantCode-Bench: 评估策略可执行性
 *
 * 核心能力：
 * 1. 对单个策略执行全维度评估（回测 + 质量评分 + LLM 分析质量评估）
 * 2. 对多个策略做横向对比（按质量评分排名）
 * 3. 生成标准化基准报告（可对比、可追溯）
 *
 * 与现有系统的关系：
 * - 复用 BacktestSimulator 执行回测
 * - 复用 WalkForwardValidator 执行稳健性验证
 * - 复用 StrategyQualityScorer 生成质量评分
 * - 复用 LlmAnalysisQualityAssessor 评估 LLM 分析质量
 */
@Component
public class BenchmarkSuite {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkSuite.class);

    private final BacktestSimulator backtestSimulator;
    private final WalkForwardValidator walkForwardValidator;
    private final StrategyQualityScorer qualityScorer;
    private final LlmAnalysisQualityAssessor analysisAssessor;
    private final StrategyCatalog strategyCatalog;
    private final MarketDataPort marketDataPort;

    /** 基准评估股票池 */
    private List<String> benchmarkUniverse = List.of("600519", "000858", "601318");

    public BenchmarkSuite(BacktestSimulator backtestSimulator,
                             WalkForwardValidator walkForwardValidator,
                             StrategyQualityScorer qualityScorer,
                             LlmAnalysisQualityAssessor analysisAssessor,
                             StrategyCatalog strategyCatalog,
                             MarketDataPort marketDataPort) {
        this.backtestSimulator = backtestSimulator;
        this.walkForwardValidator = walkForwardValidator;
        this.qualityScorer = qualityScorer;
        this.analysisAssessor = analysisAssessor;
        this.strategyCatalog = strategyCatalog;
        this.marketDataPort = marketDataPort;
    }

    /**
     * 对单个策略执行全维度基准评估
     *
     * @param strategyId 策略 ID
     * @param stockCode  股票代码
     * @param days       回测天数
     * @return 基准评估报告
     */
    public BenchmarkReport evaluateStrategy(String strategyId, String stockCode, int days) {
        long startTime = System.currentTimeMillis();
        log.info("基准评估开始: strategy={} stock={} days={}", strategyId, stockCode, days);

        StrategyDefinition strategy = strategyCatalog.find(strategyId).orElse(null);
        if (strategy == null || !strategy.hasBacktest()) {
            return BenchmarkReport.error(strategyId, stockCode, "策略不存在或不支持回测");
        }

        // 1. 获取历史数据
        LocalDate end = LocalDate.now();
        List<StockDailyData> data = marketDataPort.getHistoryData(stockCode, end.minusDays(days), end);
        if (data == null || data.isEmpty()) {
            return BenchmarkReport.error(strategyId, stockCode, "无法获取历史数据");
        }

        // 2. 执行回测
        BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode(stockCode);
        BacktestSimulationResult backtestResult = backtestSimulator.simulate(data, strategy, 100000, config);

        // 3. Walk-Forward 验证（可选）
        WalkForwardResult walkForward = null;
        try {
            walkForward = walkForwardValidator.validate(strategy, data, 60, 100000);
        } catch (Exception e) {
            log.debug("Walk-Forward 验证失败: {}", e.getMessage());
        }

        // 4. 策略质量评分
        StrategyQualityScore qualityScore = qualityScorer.score(backtestResult, walkForward, strategyId);

        long duration = System.currentTimeMillis() - startTime;
        log.info("基准评估完成: {} score={} grade={} duration={}ms",
                strategyId, qualityScore.getOverallScore(), qualityScore.getGrade(), duration);

        return new BenchmarkReport(strategyId, stockCode, days,
                backtestResult, walkForward, qualityScore, duration);
    }

    /**
     * 对多个策略做横向对比评估
     *
     * @param strategyIds 策略 ID 列表
     * @param stockCode   股票代码
     * @param days        回测天数
     * @return 对比报告（按质量评分降序排列）
     */
    public BenchmarkComparison compareStrategies(List<String> strategyIds, String stockCode, int days) {
        long startTime = System.currentTimeMillis();
        log.info("策略对比评估开始: strategies={} stock={} days={}", strategyIds.size(), stockCode, days);

        List<BenchmarkReport> reports = new ArrayList<>();
        for (String strategyId : strategyIds) {
            try {
                BenchmarkReport report = evaluateStrategy(strategyId, stockCode, days);
                reports.add(report);
            } catch (Exception e) {
                log.warn("策略 {} 评估失败: {}", strategyId, e.getMessage());
                reports.add(BenchmarkReport.error(strategyId, stockCode, e.getMessage()));
            }
        }

        // 按质量评分排序
        reports.sort(Comparator.comparingDouble((BenchmarkReport r) ->
                r.getQualityScore() != null ? -r.getQualityScore().getOverallScore() : 0).reversed());

        long duration = System.currentTimeMillis() - startTime;
        log.info("策略对比完成: {} 个策略，耗时 {}ms", reports.size(), duration);

        return new BenchmarkComparison(stockCode, days, reports, duration);
    }

    /**
     * 对 LLM 分析报告做质量评估
     *
     * @param llmResponse LLM 生成的分析文本
     * @param contextData 提供给 LLM 的上下文数据
     * @return 质量评估结果
     */
    public LlmAnalysisQuality assessAnalysisQuality(String llmResponse, Map<String, Object> contextData) {
        return analysisAssessor.assess(llmResponse, contextData);
    }

    /**
     * 对整个策略目录做全面基准扫描
     *
     * @param stockCode 股票代码
     * @param days      回测天数
     * @return 扫描报告
     */
    public BenchmarkComparison scanAllStrategies(String stockCode, int days) {
        List<String> strategyIds = strategyCatalog.listAll().stream()
                .filter(StrategyDefinition::hasBacktest)
                .map(StrategyDefinition::getId)
                .toList();
        return compareStrategies(strategyIds, stockCode, days);
    }

    // ===== 配置 =====

    public void setBenchmarkUniverse(List<String> universe) {
        this.benchmarkUniverse = universe;
    }

    public List<String> getBenchmarkUniverse() {
        return benchmarkUniverse;
    }
}
