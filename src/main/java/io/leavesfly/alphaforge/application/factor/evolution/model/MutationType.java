package io.leavesfly.alphaforge.application.factor.evolution.model;

/**
 * 因子变异类型 — 描述因子是如何从上一代进化而来的
 *
 * 对应论文 FactorMiner 中的"技能变异"和 AlphaAgentEvo 中的"进化操作"。
 */
public enum MutationType {

    /** 初始生成（第 0 代，LLM 从零创建） */
    INITIAL,

    /** 参数变异 — 调整因子内部参数（如周期、阈值） */
    PARAM_MUTATE,

    /** 表达式变异 — 修改因子计算逻辑的局部结构 */
    EXPR_MUTATE,

    /** 交叉繁殖 — 将两个父代因子的特征组合 */
    CROSSBREED,

    /** 反向变异 — 基于失败模式，生成与错误因子相反方向的因子 */
    INVERSE_MUTATE,

    /** 条件特化 — 添加市场条件约束（如仅在特定趋势/波动率下激活） */
    CONDITION_SPECIALIZE,

    /** 因子组合 — 多个因子线性/非线性组合 */
    COMBINE
}
