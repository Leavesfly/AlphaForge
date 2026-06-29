package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.application.service.feedback.ExperienceMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 因子经验桥接器实现 — 统一信号级经验与因子级经验
 *
 * 将 ExperienceMemory（信号级）和 FactorEvolutionMemory（因子级）的经验
 * 聚合为统一的 LLM prompt 注入文本，实现双轨经验注入。
 */
@Service
public class FactorExperienceBridgeImpl implements FactorExperienceBridge {

    private static final Logger log = LoggerFactory.getLogger(FactorExperienceBridgeImpl.class);

    private final ExperienceMemory experienceMemory;
    private final FactorEvolutionMemory evolutionMemory;
    private final EvolvableFactorLibrary factorLibrary;

    public FactorExperienceBridgeImpl(ExperienceMemory experienceMemory,
                                        FactorEvolutionMemory evolutionMemory,
                                        EvolvableFactorLibrary factorLibrary) {
        this.experienceMemory = experienceMemory;
        this.evolutionMemory = evolutionMemory;
        this.factorLibrary = factorLibrary;
    }

    @Override
    public String buildUnifiedExperienceHint(String stockCode,
                                              Map<String, Object> currentConditions,
                                              String marketPhase) {
        StringBuilder sb = new StringBuilder();

        // 1. 信号级经验（来自 ExperienceMemory）
        String signalHint = experienceMemory.getExperienceHint(stockCode, currentConditions);
        if (signalHint != null && !signalHint.isBlank()) {
            sb.append(signalHint);
        }

        // 2. 因子级经验（来自 FactorEvolutionMemory）
        List<FactorRecommendation> recommendations = recommendFactors(marketPhase, 3);
        if (!recommendations.isEmpty()) {
            sb.append("\n## 进化因子推荐\n");
            sb.append("当前市场阶段（").append(marketPhase).append("）下表现最优的进化因子：\n");
            for (FactorRecommendation rec : recommendations) {
                sb.append(String.format("- %s: IC=%.4f Sharpe=%.2f — %s\n",
                        rec.factorName(), rec.ic(), rec.sharpeRatio(), rec.description()));
            }
        }

        // 3. 因子库中的进化因子列表
        List<String> evolvedFactors = factorLibrary.listEvolvedFactors();
        if (!evolvedFactors.isEmpty()) {
            sb.append("\n## 可用的进化因子\n");
            sb.append(String.join(", ", evolvedFactors)).append("\n");
        }

        return sb.toString();
    }

    @Override
    public List<FactorRecommendation> recommendFactors(String marketPhase, int limit) {
        List<FactorRecommendation> recommendations = new ArrayList<>();

        // 从进化记忆中获取指定市场条件下的 Top 因子
        var topRecords = evolutionMemory.getTopPerformersByCondition(marketPhase, limit * 2);

        // 也获取全局 Top 因子作为补充
        if (topRecords.size() < limit) {
            var globalTop = evolutionMemory.getTopPerformers(limit);
            for (var record : globalTop) {
                if (topRecords.size() >= limit) break;
                if (topRecords.stream().noneMatch(r -> r.getFactorId().equals(record.getFactorId()))) {
                    topRecords.add(record);
                }
            }
        }

        for (var record : topRecords.stream().limit(limit).toList()) {
            // 只推荐已注册到因子库的因子
            var info = factorLibrary.getEvolvedFactorInfo(record.getFactorName());
            if (info != null) {
                recommendations.add(new FactorRecommendation(
                        record.getFactorId(),
                        record.getFactorName(),
                        record.getFactorExpression(),
                        record.getIc(),
                        record.getSharpeRatio(),
                        record.getEvaluationScore(),
                        record.getMarketCondition()
                ));
            }
        }

        return recommendations;
    }

    @Override
    public void propagateSignalFeedback(String stockCode,
                                          List<String> factorIds,
                                          String signalOutcome,
                                          Double returnPct) {
        if (factorIds == null || factorIds.isEmpty()) return;

        log.debug("传播信号反馈到因子记忆: stock={} factors={} outcome={} return={}",
                stockCode, factorIds.size(), signalOutcome, returnPct);

        // 遍历涉及的因子，更新因子在实盘中的表现统计
        for (String factorId : factorIds) {
            var history = evolutionMemory.getEvolutionHistory(factorId);
            if (history.isEmpty()) continue;

            // 更新进化记录中的实盘表现统计
            // 在完整实现中，这会更新 evolved_factor_registry 表中的
            // signal_correct_count / signal_incorrect_count 字段
            // 并在信号错误次数过多时自动淘汰因子

            log.debug("因子 {} 收到信号反馈: {} (return={})",
                    factorId, signalOutcome, returnPct);
        }
    }
}
