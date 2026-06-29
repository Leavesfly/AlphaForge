package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.application.factor.evolution.model.FactorCandidate;
import io.leavesfly.alphaforge.application.factor.evolution.model.FactorEvaluation;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.util.List;
import java.util.Map;

/**
 * 因子评估器 — 对因子候选进行量化评估
 *
 * 评估流程（参考 AlphaAgentEvo 的 RL Reward 设计）：
 * 1. 因子表达式执行 — 在沙箱中编译运行因子表达式，计算每只股票每日的因子值
 * 2. IC 计算 — 因子值与未来收益的 Spearman Rank Correlation
 * 3. IR 计算 — IC 均值 / IC 标准差
 * 4. 回测验证 — 基于因子信号执行模拟交易，计算夏普比率、回撤等
 * 5. 综合评分 — 加权组合各维度指标
 *
 * 与现有 BacktestSimulator 的关系：
 * - FactorEvaluator 负责因子级评估（IC/IR/覆盖率）
 * - BacktestSimulator 负责策略级回测（交易明细/权益曲线）
 * - FactorEvaluator 在深度评估时委托 BacktestSimulator 执行回测
 */
public interface FactorEvaluator {

    /**
     * 评估单个因子
     *
     * @param candidate 因子候选
     * @param universe  评估股票池的历史数据（按股票代码分组）
     * @return 因子评估结果
     */
    FactorEvaluation evaluate(FactorCandidate candidate, Map<String, List<StockDailyData>> universe);

    /**
     * 批量评估多个因子（并行优化）
     *
     * @param candidates 因子候选列表
     * @param universe   评估股票池的历史数据
     * @return 因子评估结果列表（与输入顺序一致）
     */
    List<FactorEvaluation> evaluateBatch(List<FactorCandidate> candidates,
                                           Map<String, List<StockDailyData>> universe);

    /**
     * 计算因子的 IC（信息系数）序列
     *
     * @param factorExpression 因子计算表达式
     * @param universe         评估股票池
     * @param forwardDays      未来收益预测天数（通常为 5/10/20）
     * @return 每日 IC 值序列（按时间排序）
     */
    List<Double> computeICSeries(String factorExpression,
                                  Map<String, List<StockDailyData>> universe,
                                  int forwardDays);

    /**
     * 计算综合评分
     *
     * 评分公式（可配置权重）：
     * score = w1 * normalize(IC) + w2 * normalize(IR) + w3 * normalize(Sharpe)
     *         - w4 * normalize(maxDrawdown) - w5 * normalize(turnover)
     *
     * @param evaluation 因子评估结果（不含综合评分）
     * @return 综合评分（0-100）
     */
    double computeOverallScore(FactorEvaluation evaluation);
}
