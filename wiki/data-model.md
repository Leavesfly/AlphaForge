# 数据模型与数据库

## 数据库概述

AlphaForge 使用 **SQLite** 作为嵌入式数据库，数据库文件路径：

```
~/.alphaforge/data/stock_analysis.db
```

数据库结构在 `src/main/resources/schema.sql` 中定义，使用 `CREATE TABLE IF NOT EXISTS` 确保幂等安全，Spring 启动时自动初始化。

## 表结构总览

| 分组 | 表名 | 说明 |
|---|---|---|
| **分析系统** | `analysis_report` | AI 分析报告 |
| | `analysis_tasks` | 异步分析任务 |
| **决策信号** | `decision_signals` | AI 生成的交易信号 |
| | `decision_signal_outcomes` | 信号事后评估结果 |
| | `decision_signal_feedback` | 用户反馈 |
| **回测系统** | `backtest_records` | 策略回测记录 |
| **投资组合** | `portfolio_accounts` | 投资账户 |
| | `portfolio_positions` | 持仓明细 |
| | `portfolio_trades` | 交易记录 |
| | `cash_ledger` | 资金流水 |
| | `corporate_actions` | 公司行动 |
| **行情数据** | `stock_daily_data` | 日线行情 |
| **告警系统** | `alert_rules` | 告警规则 |
| | `alert_triggers` | 告警触发记录 |
| | `alert_notifications` | 通知发送记录 |
| **自选股** | `watchlist` | 用户自选股 |
| **AI 对话** | `chat_sessions` | 对话会话 |
| | `chat_messages` | 对话消息 |
| **LLM 用量** | `llm_usage_daily` | 每日用量统计 |
| **智能选股** | `alphasift_tasks` | 选股任务 |
| **因子进化** | `factor_candidates` | 因子候选 |
| | `factor_evaluations` | 因子评估结果 |
| | `factor_evolution_memory` | 进化记忆 |
| | `factor_failure_patterns` | 失败模式 |
| | `evolved_factor_registry` | 生产因子库 |

## 核心表详细说明

### analysis_report（分析报告）

存储每次 AI 分析的完整结果：

| 字段 | 类型 | 说明 |
|---|---|---|
| `stock_code` | VARCHAR(20) | 股票代码 |
| `analysis_date` | TIMESTAMP | 分析时间 |
| `total_score` | INTEGER | 综合评分（0-100） |
| `signal` | VARCHAR(20) | 信号：buy/sell/hold/strong_buy/strong_sell |
| `confidence` | REAL | 置信度（0-1） |
| `technical_analysis` | TEXT | 技术面分析（JSON/Markdown） |
| `fundamental_analysis` | TEXT | 基本面分析 |
| `news_analysis` | TEXT | 舆情分析 |
| `full_report` | TEXT | 完整分析报告 |
| `agent_mode` | VARCHAR(20) | Agent 模式：single/multi/debate |
| `llm_model` | VARCHAR(100) | 使用的 LLM 模型 |
| `duration_seconds` | REAL | 分析耗时（秒） |
| `token_usage` | INTEGER | Token 消耗量 |

### decision_signals（决策信号）

AI 生成的结构化交易信号，是系统最核心的输出：

| 字段 | 类型 | 说明 |
|---|---|---|
| `action` | VARCHAR(20) | 建议动作：strong_buy/buy/hold/sell/strong_sell |
| `confidence` | REAL | 置信度（0-1） |
| `score` | INTEGER | 综合评分（0-100） |
| `horizon` | VARCHAR(20) | 投资期限：short/medium/long |
| `entry_low` / `entry_high` | REAL | 建议入场价区间 |
| `stop_loss` | REAL | 止损价位 |
| `target_price` | REAL | 目标价位 |
| `invalidation` | TEXT | 信号失效条件（文字描述） |
| `evidence` | TEXT | 支撑证据（JSON 数组） |
| `plan_quality` | VARCHAR(20) | 计划质量：high/medium/low |
| `status` | VARCHAR(20) | 状态：active/expired/executed/cancelled |

### backtest_records（回测记录）

| 字段 | 类型 | 说明 |
|---|---|---|
| `strategy_name` | VARCHAR(50) | 策略名称 |
| `start_date` / `end_date` | TIMESTAMP | 回测时间区间 |
| `initial_capital` | REAL | 初始资金 |
| `total_return_pct` | REAL | 总收益率(%) |
| `annual_return_pct` | REAL | 年化收益率(%) |
| `max_drawdown_pct` | REAL | 最大回撤(%) |
| `sharpe_ratio` | REAL | 夏普比率 |
| `win_rate_pct` | REAL | 胜率(%) |
| `alpha_pct` | REAL | 超额收益 Alpha(%) |
| `trade_details` | TEXT | 交易明细（JSON 数组） |

### stock_daily_data（日线行情）

缓存的 OHLCV 数据，用于技术分析和回测：

| 字段 | 类型 | 说明 |
|---|---|---|
| `stock_code` | VARCHAR(20) | 股票代码 |
| `trade_date` | DATE | 交易日期 |
| `open_price` | REAL | 开盘价 |
| `high_price` | REAL | 最高价 |
| `low_price` | REAL | 最低价 |
| `close_price` | REAL | 收盘价 |
| `volume` | INTEGER | 成交量（股） |
| `amount` | REAL | 成交额（元） |
| `change_pct` | REAL | 涨跌幅(%) |
| `turnover_rate` | REAL | 换手率(%) |
| `data_source` | VARCHAR(30) | 数据来源：eastmoney/tushare/yahoo |

### portfolio_accounts（投资账户）

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | VARCHAR(100) | 账户名称 |
| `market` | VARCHAR(10) | 主要市场：cn/us/hk |
| `base_currency` | VARCHAR(10) | 基础货币：CNY/USD/HKD |
| `cash_balance` | REAL | 当前现金余额 |
| `loan_balance` | REAL | 融资负债 |
| `loan_limit` | REAL | 融资额度上限 |

### alert_rules（告警规则）

| 字段 | 类型 | 说明 |
|---|---|---|
| `alert_type` | VARCHAR(30) | 类型：price_above/price_below/volume_spike/ma_cross 等 |
| `target_scope` | VARCHAR(20) | 范围：specific/watchlist/market |
| `severity` | VARCHAR(20) | 级别：low/medium/high/critical |
| `threshold_value` | REAL | 触发阈值 |
| `condition_expr` | TEXT | 复合条件表达式（JSON） |
| `notify_channels` | VARCHAR(200) | 通知渠道（逗号分隔）：email/wechat/webhook/bark |
| `one_shot` | INTEGER | 一次性规则：触发后自动禁用 |

### factor_candidates（因子候选）

| 字段 | 类型 | 说明 |
|---|---|---|
| `factor_id` | VARCHAR(50) | 因子 UUID |
| `factor_expression` | TEXT | 因子 DSL 表达式 |
| `factor_type` | VARCHAR(30) | 类型：SIMPLE/COMPOSITE/CROSS_SECTIONAL/TIME_SERIES/EVENT_DRIVEN |
| `generation_round` | INTEGER | 进化代数 |
| `parent_factor_id` | VARCHAR(50) | 父代因子 ID |
| `mutation_type` | VARCHAR(30) | 变异类型 |
| `status` | VARCHAR(20) | 生命周期：CANDIDATE/EVALUATING/VALIDATED/PROMOTED/DEPRECATED |

### factor_evaluations（因子评估）

| 字段 | 类型 | 说明 |
|---|---|---|
| `ic` / `ic_mean` / `ic_std` | REAL | IC 统计量 |
| `ir` | REAL | 信息比率 |
| `sharpe_ratio` | REAL | 夏普比率 |
| `coverage_rate` | REAL | 覆盖率 |
| `ic_decay_days` | INTEGER | IC 衰减天数 |
| `overall_score` | REAL | 综合评分（0-100） |
| `is_passing` | INTEGER | 是否通过晋升阈值 |

## 领域模型关系

```
AnalysisReport (1) ─────── (N) DecisionSignal
    │                              │
    │                          (1)─┤
    │                         DecisionSignalOutcome (N)
    │                              │
    │                         DecisionSignalFeedback (1)
    │
PortfolioAccount (1) ──────── (N) PortfolioPosition
    │                              
    ├──────────────────────── (N) PortfolioTrade
    ├──────────────────────── (N) CashLedgerEntry
    └──────────────────────── (N) CorporateAction

AlertRule (1) ─────────────── (N) AlertTrigger (1) ─── (N) AlertNotification

FactorCandidate (1) ─────── (1) FactorEvaluation
    │
    └── factor_evolution_memory (记录进化历史)
    └── evolved_factor_registry（已提升因子）
```

## MyBatis Mapper 配置

所有 Mapper XML 文件位于 `src/main/resources/mapper/`，遵循以下命名规范：

| Mapper 文件 | 对应实体/Repository |
|---|---|
| `AnalysisReportMapper.xml` | `AnalysisReport` → `AnalysisReportRepository` |
| `DecisionSignalMapper.xml` | `DecisionSignal` → `DecisionSignalRepository` |
| `BacktestMapper.xml` | `BacktestRecord` → `BacktestRepository` |
| `PortfolioMapper.xml` | `PortfolioPosition` → `PortfolioRepository` |
| `TradeMapper.xml` | `PortfolioTrade` → `TradeRepository` |
| `StockDailyDataMapper.xml` | `StockDailyData` → `StockDailyDataRepository` |
| `AlertRuleMapper.xml` | `AlertRule` → `AlertRuleRepository` |

MyBatis 配置：
```yaml
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true   # 下划线转驼峰
```
