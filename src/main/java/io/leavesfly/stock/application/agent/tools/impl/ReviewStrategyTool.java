package io.leavesfly.stock.application.agent.tools.impl;

import io.leavesfly.stock.application.agent.tools.Tool;
import io.leavesfly.stock.application.agent.tools.ToolException;
import io.leavesfly.stock.application.strategy.StrategyCatalog;
import io.leavesfly.stock.application.strategy.engine.StrategyPerformanceTracker;
import io.leavesfly.stock.application.strategy.model.ScoringProfile;
import io.leavesfly.stock.application.strategy.model.StrategyDefinition;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略复盘工具 — 供 LLM 审查策略运行表现并生成优化建议。
 *
 * 输出各 scoring 策略的当前命中率、有效权重、衰减状态，
 * 供 LLM 分析哪些策略表现良好、哪些已失效需要调整。
 */
@Component
public class ReviewStrategyTool implements Tool {

    private final StrategyCatalog catalog;
    private final StrategyPerformanceTracker performanceTracker;

    public ReviewStrategyTool(StrategyCatalog catalog, StrategyPerformanceTracker performanceTracker) {
        this.catalog = catalog;
        this.performanceTracker = performanceTracker;
    }

    @Override
    public String name() {
        return "review_strategies";
    }

    @Override
    public String description() {
        return "审查所有评分策略的运行表现，包括命中率、有效权重、衰减状态，辅助识别失效策略并优化策略组合";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> strategyId = new HashMap<>();
        strategyId.put("type", "string");
        strategyId.put("description", "指定策略 id 进行单独复盘，留空则复盘全部 scoring 策略");
        properties.put("strategy_id", strategyId);

        params.put("properties", properties);
        params.put("required", new String[]{});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String strategyId = (String) args.get("strategy_id");

        if (strategyId != null && !strategyId.isBlank()) {
            return reviewSingleStrategy(strategyId);
        }
        return reviewAllStrategies();
    }

    private String reviewSingleStrategy(String strategyId) {
        StrategyDefinition strategy = catalog.find(strategyId).orElse(null);
        if (strategy == null) {
            return "策略不存在: " + strategyId;
        }
        ScoringProfile profile = strategy.getScoring();
        if (profile == null) {
            return "策略 " + strategyId + " 无 scoring 段，不参与综合评分";
        }

        double matchRate = performanceTracker.getMatchRate(strategyId);
        int effectiveWeight = performanceTracker.getEffectiveWeight(
                strategyId, profile.getScoreWeight(), profile.isAutoDecay(), profile.getMinWeight());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("策略复盘: %s（%s）\n\n", strategy.getId(), strategy.getLabel()));
        sb.append(String.format("原始权重: %d\n", profile.getScoreWeight()));
        sb.append(String.format("有效权重: %d\n", effectiveWeight));
        sb.append(String.format("自动衰减: %s\n", profile.isAutoDecay() ? "启用" : "未启用"));
        sb.append(String.format("命中率: %s\n", matchRate >= 0 ? String.format("%.1f%%", matchRate * 100) : "无数据"));
        sb.append(String.format("衰减窗口: %d 次\n", profile.getDecayWindow()));
        sb.append(String.format("最小权重: %d\n", profile.getMinWeight()));
        sb.append(String.format("标签: %s\n", strategy.getTags()));
        sb.append(String.format("适用市场: %s\n", strategy.getApplicableMarket()));

        if (matchRate >= 0) {
            if (matchRate < 0.1) {
                sb.append("\n⚠ 命中率极低，策略可能已过时，建议检查条件或降低权重。\n");
            } else if (matchRate > 0.9) {
                sb.append("\n⚠ 命中率过高，策略区分度不足，建议收紧条件。\n");
            } else if (matchRate >= 0.3 && matchRate <= 0.7) {
                sb.append("\n✓ 命中率处于最佳区分区间，策略表现正常。\n");
            }
        }
        return sb.toString().trim();
    }

    private String reviewAllStrategies() {
        List<StrategyDefinition> scoringStrategies = catalog.listByCapability("scoring");
        if (scoringStrategies.isEmpty()) {
            return "当前无 scoring 策略";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("策略复盘报告（共 %d 个 scoring 策略）：\n\n", scoringStrategies.size()));
        sb.append(String.format("%-22s %8s %8s %8s %8s %s\n",
                "策略", "原始权重", "有效权重", "命中率", "衰减", "状态"));
        sb.append("-".repeat(80)).append("\n");

        int staleCount = 0;
        int lowDiscriminationCount = 0;

        for (StrategyDefinition s : scoringStrategies) {
            ScoringProfile profile = s.getScoring();
            if (profile == null || profile.getScoreWeight() <= 0) continue;

            double matchRate = performanceTracker.getMatchRate(s.getId());
            int effectiveWeight = performanceTracker.getEffectiveWeight(
                    s.getId(), profile.getScoreWeight(), profile.isAutoDecay(), profile.getMinWeight());

            String status;
            if (matchRate < 0) {
                status = "无数据";
            } else if (matchRate < 0.1) {
                status = "⚠过时";
                staleCount++;
            } else if (matchRate > 0.9) {
                status = "⚠低区分";
                lowDiscriminationCount++;
            } else if (matchRate >= 0.3 && matchRate <= 0.7) {
                status = "✓正常";
            } else {
                status = "观察中";
            }

            String matchRateStr = matchRate >= 0 ? String.format("%.0f%%", matchRate * 100) : "-";
            String decayStr = profile.isAutoDecay() ? "启用" : "-";

            sb.append(String.format("%-22s %8d %8d %8s %8s %s\n",
                    s.getId(), profile.getScoreWeight(), effectiveWeight,
                    matchRateStr, decayStr, status));
        }

        sb.append(String.format("\n汇总: %d 策略过时, %d 策略区分度低, %d 策略正常\n",
                staleCount, lowDiscriminationCount,
                scoringStrategies.size() - staleCount - lowDiscriminationCount));
        return sb.toString().trim();
    }
}
