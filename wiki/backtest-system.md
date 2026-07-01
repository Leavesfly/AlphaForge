# 回测系统详解

## 概述

AlphaForge 内置真实感回测仿真器（`BacktestSimulator`），支持 A 股特有约束（T+1、涨跌停、手续费、印花税、滑点），提供准确的历史策略绩效评估。

## 回测执行流程

```
BacktestController.runBacktest()
    ↓
BacktestSimulator.simulate(data, strategy, initialCapital, config)
    │
    ├── 计算预热期（warmupDays）= 最长均线周期
    │
    ├── 遍历每根 K 线（从 warmup 开始）
    │   ├── NEXT_OPEN 模式：先执行上一根 K 线的挂单（次日开盘成交）
    │   ├── 计算当前 portfolioValue，更新最大回撤
    │   ├── 调用 BacktestSignalEngine.signal() 获取信号（1/0/-1）
    │   └── 按执行模式处理信号
    │       ├── CLOSE 模式：当日收盘直接成交
    │       └── NEXT_OPEN 模式：挂单，次日开盘成交（T+1）
    │
    ├── 遍历结束后强制平仓（forceCloseAtEnd）
    └── finalizeMetrics() 计算最终指标
```

## 执行模式

| 模式 | 说明 | 适用场景 |
|---|---|---|
| `NEXT_OPEN` | 信号触发后次日开盘成交（**推荐**） | A 股真实交易模拟 |
| `CLOSE` | 信号触发当日收盘成交 | 理论上限测试 |

NEXT_OPEN 模式更接近 A 股实际交易情况：
- T+1 限制：当天买入的股票次日才能卖出
- 开盘价存在跳空缺口，买卖价格更真实

## 交易成本模型

### TradeCostCalculator

| 费用项 | 公式 | 典型值 |
|---|---|---|
| 买入佣金 | `shares × price × commissionRate`，不低于最低佣金 | 万分之三，最低 5 元 |
| 卖出佣金 | 同买入佣金 | 同上 |
| 印花税 | `shares × price × stampTaxRate`（**仅卖出收取**） | 千分之一 |
| 买入滑点 | `shares × price × slippageRate`（做多时成本增加） | 可配置 |
| 卖出滑点 | `shares × price × slippageRate`（做多时收益减少） | 可配置 |

### BacktestSimulationConfig 参数

```yaml
execution_mode: NEXT_OPEN       # 执行模式
commission_rate: 0.0003         # 佣金费率（万分之三）
min_commission: 5.0             # 最低佣金（元）
stamp_tax_rate: 0.001           # 印花税（千分之一）
slippage_rate: 0.0005           # 滑点（万分之五）
t1_enabled: true                # 是否启用 T+1 限制
lot_size: 100                   # 每手股数（A股100股）
limit_up_threshold: 0.099       # 涨停阈值（9.9%）
limit_down_threshold: -0.099    # 跌停阈值（-9.9%）
```

## 涨跌停与停牌约束

`BarTradability` 类负责判断每根 K 线是否可以交易：

| 场景 | 处理逻辑 |
|---|---|
| 买入时涨停 | 无法买入，记录 `skippedBuys++` |
| 卖出时跌停 | 无法卖出，记录 `skippedSells++` |
| T+1 阻断卖出 | 当天买入当天就触发卖出信号，记录 `t1BlockedSells++` |

## 仓位管理

`BacktestProfile.positionSize`（0-1）控制每次买入使用的资金比例：

- `1.0`：全仓（满仓操作）
- `0.95`：留出 5% 现金作为手续费缓冲
- `0.5`：半仓

系统自动按 A 股 100 股整数倍规整实际买入股数：

```java
int rawShares = (int)(cash * positionSize / executionPrice);
int shares = TradeCostCalculator.normalizeShares(rawShares, config);
// normalizeShares：对 lotSize(100) 取整，避免买入零股
```

## 绩效指标计算

### finalizeMetrics() 计算的指标

| 指标 | 计算公式 |
|---|---|
| 总收益率 | `(finalCapital - initialCapital) / initialCapital × 100` |
| 年化收益率 | `totalReturn × 252 / tradingDays` |
| 最大回撤 | 逐日更新 `(peakValue - currentValue) / peakValue × 100` |
| 夏普比率 | `(avgDailyReturn × 252 - 0.03) / (stdDailyReturn × √252)` |
| 胜率 | `wins / completedTrades × 100` |
| 盈亏比 | `grossProfit / grossLoss`（或 grossProfit 若无亏损） |
| 平均持仓天数 | `totalHoldDays / completedTrades` |
| Alpha | `totalReturn - benchmarkReturn` |

### 权益曲线（equityCurve）

每日记录一条 `BacktestDailySnapshot`：

| 字段 | 说明 |
|---|---|
| `date` | 日期 |
| `portfolioValue` | 投资组合市值 |
| `benchmarkValue` | 基准（买入持有）市值 |
| `drawdownPct` | 当日回撤百分比 |
| `closePrice` | 股票收盘价 |

权益曲线数据通过 `BacktestController /backtest/{id}/visualization` 接口返回，供前端绘图。

## 回测诊断信息

`finalizeMetrics` 还会在 `result.diagnostics` 中记录详细诊断：

```json
{
  "execution_mode": "NEXT_OPEN",
  "t1_enabled": true,
  "total_commission": 1234.56,
  "total_stamp_tax": 456.78,
  "total_slippage_cost": 234.56,
  "skipped_buys": 3,
  "skipped_sells": 1,
  "t1_blocked_sells": 0,
  "open_position_at_end": false,
  "equity_curve": [...]
}
```

## 回测结果存储

`BacktestRecord` 实体存储到 `backtest_records` 表，包含：

- 策略名称、股票代码、回测时间段
- 完整绩效指标（收益率、夏普、最大回撤等）
- 交易明细（JSON 数组）
- 策略参数快照（JSON）

## API 接口

详见 [api-reference.md](api-reference.md) 的回测相关接口章节：

| 接口 | 说明 |
|---|---|
| `POST /api/v1/backtest/run` | 运行回测 |
| `GET /api/v1/backtest/history` | 回测历史 |
| `GET /api/v1/backtest/{id}/visualization` | 权益曲线数据 |
| `POST /api/v1/backtest/parameter-optimize` | 参数优化 |
| `POST /api/v1/backtest/walk-forward` | 前向验证 |
| `POST /api/v1/backtest/monte-carlo` | 蒙特卡洛模拟 |
| `POST /api/v1/backtest/portfolio` | 多策略组合回测 |
