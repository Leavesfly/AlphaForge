/**
 * 量化策略模块：YAML 配置驱动的策略加载与三类执行引擎。
 *
 * 目录结构：
 * - catalog.yaml：策略索引（id、能力、实现状态）
 * - definitions/*.yaml：单策略完整定义
 *
 * 三类能力对应三个引擎：
 * - backtest  → BacktestSignalEngine：历史 K 线回测，输出买/卖信号
 * - screening → ScreeningScoreEngine：实时行情选股打分（AlphaSift）
 * - scoring   → CompositeScoringEngine：多策略加权综合评分（单股分析）
 *
 * 加载入口：StrategyCatalogLoader（启动时读取 classpath 下 strategies/ 目录）
 */
package io.leavesfly.stock.application.strategy;
