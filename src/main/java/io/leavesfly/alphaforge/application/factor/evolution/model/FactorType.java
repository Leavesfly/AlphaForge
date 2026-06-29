package io.leavesfly.alphaforge.application.factor.evolution.model;

/**
 * 因子类型 — 区分因子的计算复杂度和数据依赖
 */
public enum FactorType {

    /** 简单因子 — 基于单一指标的线性计算（如动量、RSI） */
    SIMPLE,

    /** 复合因子 — 多个指标的加权/条件组合（如量价背离 + 趋势确认） */
    COMPOSITE,

    /** 截面因子 — 需要跨股票横截面比较（如行业相对强弱） */
    CROSS_SECTIONAL,

    /** 时序因子 — 基于时间序列模式（如季节性、周期性） */
    TIME_SERIES,

    /** 事件因子 — 由事件触发（如财报公告、政策变化） */
    EVENT_DRIVEN
}
