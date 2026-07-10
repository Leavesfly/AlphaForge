package io.leavesfly.alphaforge.application.strategy.generator;

import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.application.simulation.BacktestSimulationConfig;
import io.leavesfly.alphaforge.application.simulation.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.strategy.simulation.BacktestRunner;
import io.leavesfly.alphaforge.application.evaluation.StrategyQualityScore;
import io.leavesfly.alphaforge.application.evaluation.StrategyQualityScorer;
import io.leavesfly.alphaforge.application.strategy.lifecycle.StrategyLifecycleService;
import io.leavesfly.alphaforge.application.strategy.lifecycle.StrategyLifecycleState;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.application.strategy.validator.StrategyValidator;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.entity.strategy.CustomStrategy;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略生成→回测→改写闭环，最多 N 轮，达标（Grade≥B）后落库 DRAFT 并可进 TESTING。
 */
@Component
public class StrategyRefineLoop {

    private static final Logger log = LoggerFactory.getLogger(StrategyRefineLoop.class);
    private static final int DEFAULT_MAX_ROUNDS = 3;

    private final StrategyGeneratorAgent generator;
    private final StrategyValidator validator;
    private final BacktestRunner simulator;
    private final StrategyQualityScorer qualityScorer;
    private final MarketDataPort marketDataPort;
    private final StrategyLifecycleService lifecycleService;
    private final AutonomyPolicy autonomyPolicy;

    public StrategyRefineLoop(StrategyGeneratorAgent generator,
                              StrategyValidator validator,
                              BacktestRunner simulator,
                              StrategyQualityScorer qualityScorer,
                              MarketDataPort marketDataPort,
                              StrategyLifecycleService lifecycleService,
                              AutonomyPolicy autonomyPolicy) {
        this.generator = generator;
        this.validator = validator;
        this.simulator = simulator;
        this.qualityScorer = qualityScorer;
        this.marketDataPort = marketDataPort;
        this.lifecycleService = lifecycleService;
        this.autonomyPolicy = autonomyPolicy;
    }

    public Map<String, Object> run(String description, String stockCode, int maxRounds, boolean promoteToTesting) {
        int rounds = maxRounds > 0 ? maxRounds : DEFAULT_MAX_ROUNDS;
        String code = stockCode != null && !stockCode.isBlank() ? stockCode : "600519";

        LocalDate end = LocalDate.now();
        List<StockDailyData> data = marketDataPort.getHistoryData(code, end.minusDays(365), end);

        StrategyGenerationContext ctx = new StrategyGenerationContext();
        ctx.setUserDescription(description);

        List<Map<String, Object>> history = new ArrayList<>();
        StrategyGenerationResult bestGen = null;
        StrategyQualityScore bestScore = null;

        for (int i = 1; i <= rounds; i++) {
            StrategyGenerationResult gen = generator.generate(ctx);
            Map<String, Object> round = new LinkedHashMap<>();
            round.put("round", i);
            if (gen == null || !gen.isValid()) {
                round.put("valid", false);
                round.put("error", gen != null ? gen.getValidationErrors() : "generation failed");
                history.add(round);
                continue;
            }

            StrategyDefinition def = validator.validateAndParse(gen.getYamlContent());
            BacktestSimulationResult bt = null;
            StrategyQualityScore qs = null;
            if (data != null && !data.isEmpty() && def.getBacktest() != null) {
                BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode(code);
                bt = simulator.simulate(data, def, 100_000, config);
                qs = qualityScorer.score(bt, null, def.getId());
                ctx.setLastBacktestSummary(summarize(bt, qs));
            }

            round.put("valid", true);
            round.put("strategy_id", gen.getStrategyId());
            round.put("label", gen.getLabel());
            if (qs != null) {
                round.put("grade", qs.getGrade().name());
                round.put("score", qs.getOverallScore());
            }
            history.add(round);

            if (qs != null && (bestScore == null || qs.getOverallScore() > bestScore.getOverallScore())) {
                bestGen = gen;
                bestScore = qs;
            }

            if (qs != null && autonomyPolicy.meetsPromoteGrade(qs.getGrade())) {
                log.info("RefineLoop 第 {} 轮达标: grade={}", i, qs.getGrade());
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rounds", history);
        result.put("stock_code", code);

        if (bestGen == null) {
            result.put("saved", false);
            result.put("reason", "no valid strategy generated");
            return result;
        }

        CustomStrategy saved = lifecycleService.create(
                bestGen.getStrategyId(),
                bestGen.getLabel(),
                bestGen.getReasoning(),
                bestGen.getCategory() != null ? bestGen.getCategory() : "technical",
                bestGen.getYamlContent(),
                "refine_loop");

        boolean movedToTesting = false;
        if (promoteToTesting && "valid".equals(saved.getValidationStatus())) {
            try {
                saved = lifecycleService.transition(saved.getStrategyId(), StrategyLifecycleState.TESTING);
                movedToTesting = true;
            } catch (Exception e) {
                log.warn("RefineLoop 转 TESTING 失败: {}", e.getMessage());
            }
        }

        autonomyPolicy.audit("strategy_refine", "strategy", saved.getStrategyId(),
                "none", saved.getLifecycleState(),
                bestScore != null ? "grade=" + bestScore.getGrade() : "no score");

        result.put("saved", true);
        result.put("strategy_id", saved.getStrategyId());
        result.put("lifecycle_state", saved.getLifecycleState());
        result.put("promoted_to_testing", movedToTesting);
        if (bestScore != null) {
            result.put("best_grade", bestScore.getGrade().name());
            result.put("best_score", bestScore.getOverallScore());
        }
        return result;
    }

    private static String summarize(BacktestSimulationResult bt, StrategyQualityScore qs) {
        return String.format(
                "回测反馈: 收益=%.2f%% 回撤=%.2f%% 胜率=%.1f%% 夏普=%.3f 质量分=%.1f 等级=%s。请据此改进入场/出场条件与参数。",
                bt.getTotalReturnPct(), bt.getMaxDrawdownPct(), bt.getWinRatePct(),
                bt.getSharpeRatio(), qs.getOverallScore(), qs.getGrade());
    }
}
