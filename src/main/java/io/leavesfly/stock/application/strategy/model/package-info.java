/**
 * 策略领域模型：与 YAML definitions 文件结构一一对应。
 *
 * StrategyDefinition — 策略根对象，可含 backtest / screening / scoring 三个可选段
 * BacktestProfile    — 回测参数、入场/出场条件、仓位比例
 * ScreeningProfile   — 选股打分规则、推荐理由模板、兜底配置
 * ScoringProfile     — 综合评分权重与触发条件
 */
package io.leavesfly.stock.application.strategy.model;
