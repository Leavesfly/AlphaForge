package io.leavesfly.alphaforge.application.strategy.lifecycle;

import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.application.simulation.BacktestSimulationConfig;
import io.leavesfly.alphaforge.application.simulation.BacktestSimulationResult;
import io.leavesfly.alphaforge.application.strategy.simulation.BacktestRunner;
import io.leavesfly.alphaforge.application.evaluation.StrategyQualityScore;
import io.leavesfly.alphaforge.application.evaluation.StrategyQualityScorer;
import io.leavesfly.alphaforge.application.strategy.engine.WalkForwardValidator;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.application.strategy.model.WalkForwardResult;
import io.leavesfly.alphaforge.application.strategy.validator.StrategyValidator;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.entity.strategy.CustomStrategy;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略晋升质量门 — TESTING→PUBLISHED 必须 Grade≥门槛 且 Walk-Forward 通过。
 */
@Component
public class StrategyPromotionGate {

    private static final Logger log = LoggerFactory.getLogger(StrategyPromotionGate.class);
    private static final String DEFAULT_BENCH_STOCK = "600519";
    private static final int DEFAULT_DAYS = 365;
    /** 无 param_space 时视为 WF 豁免通过（仅看质量分） */
    private static final double MIN_OVERFIT_RATIO = 0.3;

    private final AutonomyPolicy policy;
    private final StrategyQualityScorer qualityScorer;
    private final WalkForwardValidator walkForwardValidator;
    private final BacktestRunner simulator;
    private final StrategyValidator validator;
    private final MarketDataPort marketDataPort;
    private final Map<String, PromotionDecision> decisionCache = new ConcurrentHashMap<>();

    public StrategyPromotionGate(AutonomyPolicy policy,
                                 StrategyQualityScorer qualityScorer,
                                 WalkForwardValidator walkForwardValidator,
                                 BacktestRunner simulator,
                                 StrategyValidator validator,
                                 MarketDataPort marketDataPort) {
        this.policy = policy;
        this.qualityScorer = qualityScorer;
        this.walkForwardValidator = walkForwardValidator;
        this.simulator = simulator;
        this.validator = validator;
        this.marketDataPort = marketDataPort;
    }

    public PromotionDecision evaluate(StrategyDefinition strategy, List<StockDailyData> data) {
        if (strategy == null || strategy.getBacktest() == null) {
            return reject(strategy != null ? strategy.getId() : "unknown",
                    null, 0, false, "策略不存在或不支持回测", null, null);
        }
        if (data == null || data.isEmpty()) {
            return reject(strategy.getId(), null, 0, false, "无历史数据用于晋升评估", null, null);
        }

        BacktestSimulationConfig config = BacktestSimulationConfig.forStockCode(
                data.get(0).getStockCode() != null ? data.get(0).getStockCode() : DEFAULT_BENCH_STOCK);
        BacktestSimulationResult backtest = simulator.simulate(data, strategy, 100_000, config);

        WalkForwardResult walkForward = null;
        boolean wfPassed;
        if (strategy.getBacktest() != null && strategy.getBacktest().hasParamSpace()) {
            walkForward = walkForwardValidator.validate(strategy, data, 60, 100_000);
            wfPassed = walkForward.getWindowCount() > 0
                    && walkForward.getOverfitRatio() >= MIN_OVERFIT_RATIO;
        } else {
            // 无参数空间：不做 WF，视为通过
            wfPassed = true;
        }

        StrategyQualityScore score = qualityScorer.score(backtest, walkForward, strategy.getId());
        boolean gradeOk = policy.meetsPromoteGrade(score.getGrade());
        boolean promotable = gradeOk && wfPassed;
        String reason = promotable
                ? String.format("达标: grade=%s score=%.1f wfPassed=%s", score.getGrade(), score.getOverallScore(), wfPassed)
                : String.format("未达标: grade=%s(需≥%s) score=%.1f wfPassed=%s overfit=%.2f",
                score.getGrade(), policy.minPromoteGrade(), score.getOverallScore(), wfPassed,
                walkForward != null ? walkForward.getOverfitRatio() : -1);

        PromotionDecision decision = new PromotionDecision(
                strategy.getId(), promotable, score.getGrade(), score.getOverallScore(),
                wfPassed, reason, score, walkForward);
        decisionCache.put(strategy.getId(), decision);
        log.info("晋升评估: {} → {}", strategy.getId(), reason);
        return decision;
    }

    public PromotionDecision evaluateCustom(CustomStrategy custom, String stockCode, int days) {
        StrategyDefinition def = validator.validateAndParse(custom.getYamlContent());
        LocalDate end = LocalDate.now();
        List<StockDailyData> data = marketDataPort.getHistoryData(
                stockCode != null ? stockCode : DEFAULT_BENCH_STOCK,
                end.minusDays(days > 0 ? days : DEFAULT_DAYS), end);
        return evaluate(def, data);
    }

    public PromotionDecision evaluateOrLoad(String strategyId, CustomStrategy custom) {
        PromotionDecision cached = decisionCache.get(strategyId);
        if (cached != null) {
            return cached;
        }
        return evaluateCustom(custom, DEFAULT_BENCH_STOCK, DEFAULT_DAYS);
    }

    public void assertPromotable(PromotionDecision decision) {
        if (decision == null || !decision.isPromotable()) {
            String reason = decision != null ? decision.getReason() : "无晋升评估结果";
            throw new IllegalStateException("策略未通过晋升质量门: " + reason);
        }
    }

    public void cacheDecision(PromotionDecision decision) {
        if (decision != null && decision.getStrategyId() != null) {
            decisionCache.put(decision.getStrategyId(), decision);
        }
    }

    public Optional<PromotionDecision> getCached(String strategyId) {
        return Optional.ofNullable(decisionCache.get(strategyId));
    }

    private PromotionDecision reject(String id, StrategyQualityScore.QualityGrade grade,
                                     double score, boolean wf, String reason,
                                     StrategyQualityScore qs, WalkForwardResult wfResult) {
        PromotionDecision d = new PromotionDecision(id, false, grade, score, wf, reason, qs, wfResult);
        if (id != null) decisionCache.put(id, d);
        return d;
    }
}
