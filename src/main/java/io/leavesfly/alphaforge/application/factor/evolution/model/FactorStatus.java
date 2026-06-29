package io.leavesfly.alphaforge.application.factor.evolution.model;

/**
 * 因子生命周期状态
 */
public enum FactorStatus {

    /** 候选 — 刚生成，待评估 */
    CANDIDATE,

    /** 验证中 — 正在回测评估 */
    EVALUATING,

    /** 已验证 — 通过 IC/IR/回测检验 */
    VALIDATED,

    /** 已提升 — 进入生产因子库，参与实盘信号生成 */
    PROMOTED,

    /** 已淘汰 — 未通过评估或长期失效 */
    DEPRECATED,

    /** 已变异 — 作为父代被用于生成下一代，本体不再使用 */
    MUTATED
}
