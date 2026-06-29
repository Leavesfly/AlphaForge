package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.application.factor.evolution.model.*;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 因子进化编排器实现 — 驱动 生成→评估→选择→变异 的完整进化闭环
 */
@Component
public class FactorEvolutionOrchestratorImpl implements FactorEvolutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FactorEvolutionOrchestratorImpl.class);

    private final FactorGeneratorAgent generatorAgent;
    private final FactorEvaluator evaluator;
    private final FactorEvolutionMemory evolutionMemory;
    private final EvolvableFactorLibrary evolvableLibrary;
    private final MarketDataPort marketDataPort;

    /** 评估股票池（默认使用沪深300成分股的子集） */
    private List<String> evaluationUniverse = List.of(
            "600519", "000858", "601318", "600036", "000333",
            "600276", "000651", "601166", "002415", "300750"
    );

    public FactorEvolutionOrchestratorImpl(FactorGeneratorAgent generatorAgent,
                                             FactorEvaluator evaluator,
                                             FactorEvolutionMemory evolutionMemory,
                                             EvolvableFactorLibrary evolvableLibrary,
                                             MarketDataPort marketDataPort) {
        this.generatorAgent = generatorAgent;
        this.evaluator = evaluator;
        this.evolutionMemory = evolutionMemory;
        this.evolvableLibrary = evolvableLibrary;
        this.marketDataPort = marketDataPort;
    }

    @Override
    public EvolutionResult runEvolutionCycle(FactorEvolutionConfig config) {
        long startTime = System.currentTimeMillis();
        int generation = evolutionMemory.getCurrentGeneration();
        log.info("===== 开始第 {} 代因子进化 =====", generation);

        // 1. 构建生成上下文
        Map<String, List<StockDailyData>> universe = loadUniverseData(config);
        FactorGenerationContext context = buildGenerationContext(generation, config);

        // 2. 生成因子候选
        List<FactorCandidate> candidates;
        if (generation == 0) {
            candidates = generatorAgent.generateInitialFactors(context);
        } else {
            candidates = generateNextGeneration(context, config);
        }

        if (candidates.isEmpty()) {
            log.warn("第 {} 代未生成任何因子候选", generation);
            return buildEmptyResult(generation, startTime);
        }

        log.info("第 {} 代生成 {} 个因子候选", generation, candidates.size());

        // 3. 评估因子
        List<FactorEvaluation> evaluations = evaluator.evaluateBatch(candidates, universe);
        log.info("第 {} 代评估完成", generation);

        // 4. 记录进化历史
        for (int i = 0; i < candidates.size(); i++) {
            FactorCandidate candidate = candidates.get(i);
            FactorEvaluation evaluation = evaluations.get(i);

            FactorStatus status = determineStatus(evaluation, config);
            String failureReason = status == FactorStatus.DEPRECATED
                    ? describeFailure(evaluation, config) : null;
            List<String> failurePatterns = status == FactorStatus.DEPRECATED
                    ? extractFailurePatterns(candidate, evaluation) : null;

            FactorEvolutionRecord record = new FactorEvolutionRecord(
                    candidate.getFactorId(),
                    candidate.getFactorName(),
                    candidate.getFactorExpression(),
                    candidate.getGenerationRound(),
                    candidate.getMutationType(),
                    candidate.getParentFactorId(),
                    candidate.getSecondParentFactorId(),
                    candidate.getFactorType(),
                    candidate.getCategory(),
                    candidate.getMarketCondition(),
                    evaluation.getOverallScore(),
                    evaluation.getIc(),
                    evaluation.getIr(),
                    evaluation.getSharpeRatio(),
                    status,
                    failureReason,
                    failurePatterns,
                    candidate.getDescription()
            );
            evolutionMemory.recordEvolution(record);

            // 5. 提升通过评估的因子
            if (status == FactorStatus.VALIDATED || status == FactorStatus.PROMOTED) {
                boolean promoted = evolvableLibrary.registerFactor(candidate, evaluation);
                if (promoted) {
                    log.info("因子 {} 已注册到生产因子库", candidate.getFactorName());
                }
            }
        }

        // 6. 选择 Top 因子
        List<FactorEvaluation> topEvals = evaluations.stream()
                .sorted(Comparator.comparingDouble(FactorEvaluation::getOverallScore).reversed())
                .limit(config.getTopKSelection())
                .collect(Collectors.toList());

        int passed = (int) evaluations.stream()
                .filter(e -> e.isPassing(config.getMinIC(), config.getMinIR(), config.getMinSharpe()))
                .count();
        int promoted = (int) evaluations.stream()
                .filter(e -> e.getOverallScore() >= config.getMinOverallScore())
                .count();

        // 7. 收敛判断
        boolean converged = evolutionMemory.isConverged(
                config.getConvergenceGenerations(), config.getConvergenceThreshold());
        String convergenceReason = converged ? "连续 " + config.getConvergenceGenerations()
                + " 代 IC 无显著提升" : null;

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("===== 第 {} 代进化完成: 生成={}, 通过={}, 提升={}, 耗时={}ms =====",
                generation, candidates.size(), passed, promoted, durationMs);

        return new EvolutionResult(
                generation,
                candidates.size(),
                passed,
                promoted,
                topEvals,
                candidates,
                durationMs,
                converged,
                convergenceReason
        );
    }

    @Override
    public EvolutionResult runMultiGenerationEvolution(FactorEvolutionConfig config, int maxGenerations) {
        EvolutionResult lastResult = null;
        for (int i = 0; i < maxGenerations; i++) {
            lastResult = runEvolutionCycle(config);
            if (lastResult.isConverged()) {
                log.info("进化在第 {} 代收敛: {}", lastResult.getGeneration(), lastResult.getConvergenceReason());
                break;
            }
        }
        return lastResult;
    }

    @Override
    public boolean promoteFactor(String factorId) {
        List<FactorEvolutionRecord> history = evolutionMemory.getEvolutionHistory(factorId);
        if (history.isEmpty()) return false;

        FactorEvolutionRecord latest = history.get(history.size() - 1);
        if (!latest.isSuccessful()) return false;

        // 查找对应的候选和评估
        // 在实际实现中，需要从存储中加载，这里简化处理
        return true;
    }

    @Override
    public String getEvolutionStatusSummary() {
        int gen = evolutionMemory.getCurrentGeneration();
        List<FactorEvolutionRecord> promoted = evolutionMemory.getPromotedFactors();
        List<FailurePattern> failures = evolutionMemory.getFailurePatterns();
        List<FactorEvolutionRecord> top = evolutionMemory.getTopPerformers(1);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("进化代数: %d\n", gen));
        sb.append(String.format("已提升因子: %d\n", promoted.size()));
        sb.append(String.format("失败模式: %d\n", failures.size()));
        if (!top.isEmpty()) {
            sb.append(String.format("最优因子: %s (IC=%.4f, Score=%.1f)\n",
                    top.get(0).getFactorName(), top.get(0).getIc(), top.get(0).getEvaluationScore()));
        }
        return sb.toString();
    }

    // ===== 内部方法 =====

    private List<FactorCandidate> generateNextGeneration(FactorGenerationContext context,
                                                          FactorEvolutionConfig config) {
        List<FactorCandidate> nextGen = new ArrayList<>();

        // 获取上一代 Top 因子
        List<FactorEvolutionRecord> topRecords = evolutionMemory.getTopPerformers(config.getTopKSelection());
        List<FactorCandidate> topFactors = topRecords.stream()
                .map(this::recordToCandidate)
                .collect(Collectors.toList());

        // 变异
        if (!topFactors.isEmpty() && config.getMutationRate() > 0) {
            int mutationCount = (int) (topFactors.size() * config.getMutationRate());
            List<FactorCandidate> mutated = generatorAgent.mutateFactors(
                    topFactors.subList(0, Math.min(mutationCount, topFactors.size())), context);
            nextGen.addAll(mutated);
        }

        // 交叉繁殖
        if (topFactors.size() >= 2 && config.getCrossbreedRate() > 0) {
            int crossCount = (int) (topFactors.size() * config.getCrossbreedRate());
            List<FactorCandidate> crossed = generatorAgent.crossbreedFactors(
                    topFactors.subList(0, Math.min(crossCount + 1, topFactors.size())), context);
            nextGen.addAll(crossed);
        }

        // 反向变异
        List<FailurePattern> failures = evolutionMemory.getFailurePatterns();
        if (!failures.isEmpty() && config.getInverseMutateRate() > 0) {
            List<FactorCandidate> inversed = generatorAgent.inverseMutate(failures, context);
            nextGen.addAll(inversed);
        }

        // 补充新生成（确保候选数量）
        if (nextGen.size() < config.getCandidatesPerRound()) {
            int need = config.getCandidatesPerRound() - nextGen.size();
            List<FactorCandidate> fresh = generatorAgent.generateInitialFactors(context);
            nextGen.addAll(fresh.subList(0, Math.min(need, fresh.size())));
        }

        return nextGen;
    }

    private FactorGenerationContext buildGenerationContext(int generation, FactorEvolutionConfig config) {
        List<FactorEvolutionRecord> topPerformers = evolutionMemory.getTopPerformers(config.getTopKSelection());
        List<FailurePattern> failurePatterns = evolutionMemory.getFailurePatterns();
        String evolutionHint = evolutionMemory.buildEvolutionHint(
                new FactorGenerationContext(
                        "any", evaluationUniverse, "", "",
                        evolvableLibrary.listEvolvedFactors(),
                        evolvableLibrary.getFactorCategories(),
                        generation, topPerformers, failurePatterns, "", null
                ));

        return new FactorGenerationContext(
                "any",
                evaluationUniverse,
                LocalDate.now().minusDays(config.getBacktestPeriodDays()).toString(),
                LocalDate.now().toString(),
                evolvableLibrary.listAvailableFactors(),
                evolvableLibrary.getFactorCategories(),
                generation,
                topPerformers,
                failurePatterns,
                evolutionHint,
                null
        );
    }

    private Map<String, List<StockDailyData>> loadUniverseData(FactorEvolutionConfig config) {
        Map<String, List<StockDailyData>> universe = new LinkedHashMap<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(config.getBacktestPeriodDays());

        for (String stockCode : evaluationUniverse) {
            try {
                List<StockDailyData> data = marketDataPort.getHistoryData(stockCode, start, end);
                if (data != null && !data.isEmpty()) {
                    universe.put(stockCode, data);
                }
            } catch (Exception e) {
                log.debug("获取 {} 历史数据失败: {}", stockCode, e.getMessage());
            }
        }

        if (universe.isEmpty()) {
            log.warn("评估股票池数据为空，使用模拟数据");
            universe = generateMockUniverse(config.getBacktestPeriodDays());
        }

        return universe;
    }

    /** 生成模拟数据（当无法获取真实数据时使用） */
    private Map<String, List<StockDailyData>> generateMockUniverse(int days) {
        Map<String, List<StockDailyData>> universe = new LinkedHashMap<>();
        Random random = new Random(42);
        for (String code : evaluationUniverse) {
            List<StockDailyData> data = new ArrayList<>();
            double price = 50 + random.nextDouble() * 100;
            LocalDate start = LocalDate.now().minusDays(days);
            for (int i = 0; i < days; i++) {
                StockDailyData bar = new StockDailyData();
                bar.setStockCode(code);
                bar.setTradeDate(start.plusDays(i));
                double change = (random.nextDouble() - 0.48) * 5;
                price = Math.max(1, price * (1 + change / 100));
                bar.setClosePrice(price);
                bar.setOpenPrice(price * (1 + (random.nextDouble() - 0.5) / 100));
                bar.setHighPrice(price * 1.02);
                bar.setLowPrice(price * 0.98);
                bar.setVolume((long) (1000000 + random.nextDouble() * 5000000));
                bar.setChangePct(change);
                data.add(bar);
            }
            universe.put(code, data);
        }
        return universe;
    }

    private FactorStatus determineStatus(FactorEvaluation evaluation, FactorEvolutionConfig config) {
        if (!evaluation.isPassing(config.getMinIC(), config.getMinIR(), config.getMinSharpe())) {
            return FactorStatus.DEPRECATED;
        }
        if (evaluation.getOverallScore() >= config.getMinOverallScore()) {
            return FactorStatus.PROMOTED;
        }
        return FactorStatus.VALIDATED;
    }

    private String describeFailure(FactorEvaluation evaluation, FactorEvolutionConfig config) {
        List<String> reasons = new ArrayList<>();
        if (evaluation.getIc() < config.getMinIC())
            reasons.add(String.format("IC=%.4f 低于阈值 %.4f", evaluation.getIc(), config.getMinIC()));
        if (evaluation.getIr() < config.getMinIR())
            reasons.add(String.format("IR=%.4f 低于阈值 %.4f", evaluation.getIr(), config.getMinIR()));
        if (evaluation.getSharpeRatio() < config.getMinSharpe())
            reasons.add(String.format("Sharpe=%.2f 低于阈值 %.2f", evaluation.getSharpeRatio(), config.getMinSharpe()));
        return String.join("; ", reasons);
    }

    private List<String> extractFailurePatterns(FactorCandidate candidate, FactorEvaluation evaluation) {
        List<String> patterns = new ArrayList<>();
        if (evaluation.getIc() < 0) patterns.add("IC 为负（因子方向相反）");
        if (evaluation.getCoverageRate() < 0.5) patterns.add("覆盖率低（< 50%）");
        if (evaluation.getTurnoverRate() > 0.5) patterns.add("换手率过高（> 50%）");
        patterns.add("分类: " + candidate.getCategory());
        patterns.add("市况: " + candidate.getMarketCondition());
        return patterns;
    }

    private FactorCandidate recordToCandidate(FactorEvolutionRecord record) {
        return new FactorCandidate.Builder()
                .factorId(record.getFactorId())
                .factorName(record.getFactorName())
                .factorExpression(record.getFactorExpression())
                .factorType(record.getFactorType())
                .category(record.getCategory())
                .marketCondition(record.getMarketCondition())
                .generationRound(record.getGenerationRound())
                .mutationType(record.getMutationType())
                .parentFactorId(record.getParentFactorId())
                .secondParentFactorId(record.getSecondParentFactorId())
                .build();
    }

    private EvolutionResult buildEmptyResult(int generation, long startTime) {
        return new EvolutionResult(
                generation, 0, 0, 0,
                Collections.emptyList(), Collections.emptyList(),
                System.currentTimeMillis() - startTime, false, null
        );
    }

    /** 设置评估股票池 */
    public void setEvaluationUniverse(List<String> universe) {
        this.evaluationUniverse = universe;
    }
}
