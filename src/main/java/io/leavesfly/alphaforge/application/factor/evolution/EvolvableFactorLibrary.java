package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.application.factor.evolution.model.FactorCandidate;
import io.leavesfly.alphaforge.application.factor.evolution.model.FactorEvaluation;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.FactorLibrary;

import java.util.List;

/**
 * 可进化因子库 — 扩展现有 FactorLibrary，支持动态注册进化因子
 *
 * 设计目的：
 * 现有 DefaultFactorLibrary 中的因子是硬编码的（momentum_5d, rsi_14 等）。
 * 本接口扩展 FactorLibrary，使通过进化验证的因子能动态注册到因子库，
 * 被分析 Pipeline 自动调用。
 *
 * 与 FactorLibrary 的关系：
 * - FactorLibrary 定义因子的计算与查询接口（已有）
 * - EvolvableFactorLibrary 扩展为可动态添加/移除因子（新增）
 * - DefaultFactorLibrary + EvolvableFactorLibrary 共同构成完整因子库
 *
 * 对应论文 FactorMiner 中的 "Factor Registry"：
 * 进化因子通过验证后，自动注册到因子库参与实盘信号生成。
 */
public interface EvolvableFactorLibrary extends FactorLibrary {

    /**
     * 注册一个进化因子到因子库
     *
     * @param candidate    因子候选（含表达式和参数）
     * @param evaluation   因子评估结果（需通过最低门槛）
     * @return 注册是否成功
     */
    boolean registerFactor(FactorCandidate candidate, FactorEvaluation evaluation);

    /**
     * 移除一个因子（当因子长期失效时淘汰）
     *
     * @param factorName 因子名称
     * @return 移除是否成功
     */
    boolean unregisterFactor(String factorName);

    /**
     * 获取所有进化因子的名称列表
     *
     * @return 进化因子名称列表（不含硬编码因子）
     */
    List<String> listEvolvedFactors();

    /**
     * 获取进化因子的详细信息
     *
     * @param factorName 因子名称
     * @return 因子信息（含表达式、IC、注册时间等），不存在时返回 null
     */
    EvolvedFactorInfo getEvolvedFactorInfo(String factorName);

    /**
     * 重新计算进化因子值（因子表达式变更后调用）
     *
     * @param factorName 因子名称
     * @param history    K 线历史数据
     * @return 重新计算的因子值
     */
    double recalculate(String factorName, List<StockDailyData> history);

    /**
     * 进化因子信息
     */
    record EvolvedFactorInfo(
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
