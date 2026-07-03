package io.leavesfly.alphaforge.domain.service.portfolio;

import java.util.List;

/**
 * 组合优化结果（年化口径）。
 *
 * @param symbols            资产代码（与 weights 一一对应）
 * @param weights            最优权重（长仓、和为 1）
 * @param objective          使用的优化目标
 * @param expectedReturn     组合年化预期收益
 * @param expectedVolatility 组合年化波动率
 * @param sharpeRatio        组合夏普比率
 * @param riskContributions  各资产风险贡献占比（和为 1）
 * @param diversification    分散化比率（加权平均波动 / 组合波动，越大越分散）
 */
public record PortfolioOptimizationResult(
        List<String> symbols,
        double[] weights,
        OptimizationObjective objective,
        double expectedReturn,
        double expectedVolatility,
        double sharpeRatio,
        double[] riskContributions,
        double diversification
) {
}
