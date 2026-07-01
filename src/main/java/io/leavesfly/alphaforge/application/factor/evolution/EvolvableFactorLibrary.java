package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.application.factor.evolution.model.FactorCandidate;
import io.leavesfly.alphaforge.application.factor.evolution.model.FactorEvaluation;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.factor.DefaultFactorLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可进化因子库 — 扩展 DefaultFactorLibrary，支持动态注册进化因子
 *
 * 进化因子通过验证后自动注册到此库，被分析 Pipeline 调用。
 * 硬编码因子（momentum_5d 等）仍由 DefaultFactorLibrary 处理，
 * 进化因子通过 FactorExpressionExecutor 动态计算。
 */
@Component
public class EvolvableFactorLibrary extends DefaultFactorLibrary {

    private static final Logger log = LoggerFactory.getLogger(EvolvableFactorLibrary.class);

    /** 进化因子注册表（因子名 → 因子信息） */
    private final Map<String, EvolvedFactorInfo> evolvedFactors = new ConcurrentHashMap<>();

    /** 进化因子名 → 因子表达式 */
    private final Map<String, String> expressionMap = new ConcurrentHashMap<>();

    private final FactorExpressionExecutor expressionExecutor;

    public EvolvableFactorLibrary(FactorExpressionExecutor expressionExecutor) {
        super();
        this.expressionExecutor = expressionExecutor;
        log.info("可进化因子库初始化完成");
    }

    public boolean registerFactor(FactorCandidate candidate, FactorEvaluation evaluation) {
        if (candidate == null || evaluation == null) return false;

        // 检查重名
        if (evolvedFactors.containsKey(candidate.getFactorName())) {
            log.warn("因子名 {} 已存在，跳过注册", candidate.getFactorName());
            return false;
        }

        // 检查与硬编码因子重名
        if (super.listAvailableFactors().contains(candidate.getFactorName())) {
            log.warn("因子名 {} 与内置因子冲突", candidate.getFactorName());
            return false;
        }

        EvolvedFactorInfo info = new EvolvedFactorInfo(
                candidate.getFactorName(),
                candidate.getFactorExpression(),
                candidate.getCategory(),
                candidate.getDescription(),
                evaluation.getIc(),
                evaluation.getIr(),
                evaluation.getSharpeRatio(),
                evaluation.getOverallScore(),
                LocalDateTime.now().toString(),
                candidate.getGenerationRound()
        );

        evolvedFactors.put(candidate.getFactorName(), info);
        expressionMap.put(candidate.getFactorName(), candidate.getFactorExpression());

        log.info("注册进化因子: {} (IC={:.4f}, Score={:.1f})",
                candidate.getFactorName(), evaluation.getIc(), evaluation.getOverallScore());
        return true;
    }

    public boolean unregisterFactor(String factorName) {
        EvolvedFactorInfo removed = evolvedFactors.remove(factorName);
        expressionMap.remove(factorName);
        if (removed != null) {
            log.info("移除进化因子: {}", factorName);
            return true;
        }
        return false;
    }

    public List<String> listEvolvedFactors() {
        return new ArrayList<>(evolvedFactors.keySet());
    }

    public EvolvedFactorInfo getEvolvedFactorInfo(String factorName) {
        return evolvedFactors.get(factorName);
    }

    public double recalculate(String factorName, List<StockDailyData> history) {
        String expression = expressionMap.get(factorName);
        if (expression == null) return 0;
        return expressionExecutor.execute(expression, history);
    }

    // ===== 重写 FactorLibrary 方法，合并硬编码 + 进化因子 =====

    @Override
    public double calculate(String factorName, List<StockDailyData> history) {
        // 优先查找进化因子
        if (expressionMap.containsKey(factorName)) {
            return expressionExecutor.execute(expressionMap.get(factorName), history);
        }
        // 回退到硬编码因子
        return super.calculate(factorName, history);
    }

    @Override
    public List<String> listAvailableFactors() {
        List<String> all = new ArrayList<>(super.listAvailableFactors());
        all.addAll(evolvedFactors.keySet());
        return all;
    }

    @Override
    public Map<String, List<String>> getFactorCategories() {
        Map<String, List<String>> categories = new LinkedHashMap<>(super.getFactorCategories());

        // 将进化因子按分类归入
        for (EvolvedFactorInfo info : evolvedFactors.values()) {
            categories.computeIfAbsent(info.category(), k -> new ArrayList<>())
                    .add(info.factorName());
        }
        return categories;
    }

    /**
     * 进化因子信息
     */
    public record EvolvedFactorInfo(
            String factorName,
            String factorExpression,
            String category,
            String description,
            double ic,
            double ir,
            double sharpeRatio,
            double overallScore,
            String registeredAt,
            int generationRound
    ) {}
}
