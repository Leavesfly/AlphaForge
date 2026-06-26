/**
 * 策略执行引擎包。
 *
 * BacktestSignalEngine    — 解析 backtest.entry/exit_conditions，模拟历史交易
 * ScreeningScoreEngine    — 解析 screening.scoring_rules，对实时行情打分
 * CompositeScoringEngine  — 解析 scoring.conditions，多策略加权综合评分
 * ScoringContext          — 综合评分所需的 K 线、技术指标、行情上下文
 */
package io.leavesfly.stock.application.strategy.engine;
