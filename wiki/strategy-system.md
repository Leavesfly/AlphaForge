# 策略系统详解

## 概述

AlphaForge 的策略系统基于 YAML 配置文件驱动，支持三种引擎能力（回测、选股、评分），策略按四维分类（技术面/基本面/情绪面/事件驱动）组织，通过统一的 `catalog.yaml` 注册管理。

## 策略四维分类

| 类别 | 英文标识 | 说明 |
|---|---|---|
| 技术面 | `technical` | 基于价格、成交量、技术指标的量化条件 |
| 基本面 | `fundamental` | 基于财务数据、估值指标的价值筛选 |
| 情绪面 | `sentiment` | 基于市场情绪、资金流向、龙头效应 |
| 事件驱动 | `event` | 基于公司事件、行业事件、宏观政策 |

## 策略目录结构

```
resources/strategies/
├── catalog.yaml                    # 策略总目录（19 个策略注册）
└── definitions/
    ├── technical/                  # 技术面策略（10 个）
    │   ├── ma_golden_cross.yaml    # 均线金叉
    │   ├── volume_breakout.yaml    # 量价突破
    │   ├── bull_trend.yaml         # 强势趋势
    │   ├── shrink_pullback.yaml    # 缩量回调
    │   ├── box_oscillation.yaml    # 箱体震荡
    │   ├── wave_theory.yaml        # 波浪理论
    │   ├── bottom_volume.yaml      # 底部放量
    │   ├── chan_theory.yaml         # 缠论
    │   ├── one_yang_three_yin.yaml # 一阳穿三线
    │   └── momentum.yaml           # 动量策略
    ├── fundamental/                # 基本面策略（5 个）
    │   ├── growth_quality.yaml     # 成长质量
    │   ├── expectation_repricing.yaml # 预期重估
    │   ├── dual_low.yaml           # 双低策略
    │   ├── value_growth.yaml       # 价值成长
    │   └── dividend.yaml           # 高股息
    ├── sentiment/                  # 情绪面策略（3 个）
    │   ├── dragon_head.yaml        # 龙头效应
    │   ├── emotion_cycle.yaml      # 情绪周期
    │   └── hot_theme.yaml          # 热门主题
    └── event/                      # 事件驱动策略（1 个）
        └── event_driven.yaml       # 事件驱动
```

## 策略引擎能力

每个策略通过 `capabilities` 字段声明支持的引擎：

| 能力标识 | 引擎类 | 使用场景 |
|---|---|---|
| `backtest` | `BacktestSignalEngine` | 历史 K 线回测，生成买卖信号序列 |
| `scoring` | `CompositeScoringEngine` | 综合分析时对策略命中情况加权评分 |
| `screening` | `ScreeningScoreEngine` | AlphaSift 全市场扫描选股打分 |

**当前策略能力分布：**

| 策略 | backtest | scoring | screening |
|---|---|---|---|
| ma_golden_cross | ✓ | | |
| volume_breakout | ✓ | ✓ | |
| bull_trend | ✓ | ✓ | |
| shrink_pullback | ✓ | ✓ | |
| box_oscillation | ✓ | ✓ | |
| dragon_head | ✓ | ✓ | |
| wave_theory | ✓ | ✓ | |
| emotion_cycle | ✓ | ✓ | |
| event_driven | ✓ | ✓ | |
| growth_quality | ✓ | ✓ | |
| bottom_volume | | ✓ | |
| chan_theory | | ✓ | |
| expectation_repricing | | ✓ | |
| hot_theme | | ✓ | |
| one_yang_three_yin | | ✓ | |
| dual_low | | | ✓ |
| value_growth | | | ✓ |
| momentum | | | ✓ |
| dividend | | | ✓ |

## 策略 YAML 格式

每个策略 YAML 文件包含三个可选配置块：

```yaml
id: ma_golden_cross
name: 均线金叉策略
description: MA5 与 MA20 形成金叉，短期均线上穿长期均线，信号买入
category: technical

# 回测配置块
backtest:
  position_size: 0.95          # 仓位比例（0-1）
  entry_conditions:            # 入场条件（全部满足则买入）
    - type: ma_cross
      fast: 5
      slow: 20
      direction: golden         # golden=金叉, dead=死叉
  exit_conditions:             # 出场条件（任一满足则卖出）
    - type: ma_cross
      fast: 5
      slow: 20
      direction: dead
    - type: stop_loss
      pct: 0.07                 # 7% 止损

# 评分配置块（CompositeScoringEngine）
scoring:
  conditions:
    - type: ma_cross
      fast: 5
      slow: 20
      direction: golden
      weight: 30
    - type: volume_ratio
      min: 1.5
      weight: 20

# 选股配置块（ScreeningScoreEngine）
screening:
  conditions:
    - type: momentum
      period: 20
      min_pct: 5
      weight: 40
```

## 策略加载与注册

### 启动时加载流程

```
StrategyCatalogLoader.load()  [@PostConstruct]
  │
  ├── 读取 strategies/catalog.yaml
  ├── 解析分类（categories）和能力（capabilities）
  └── 遍历 strategies 节点
        ├── 读取对应的 definitions/{category}/{id}.yaml
        ├── 解析 BacktestProfile、ScoringProfile、ScreeningProfile
        ├── 调用 BacktestConditionEvaluator 验证条件覆盖度
        └── 注册到 StrategyCatalog（内存注册表）
```

### StrategyCatalog（策略注册表）

`StrategyCatalog` 是内存中的策略索引，提供以下查询方法：

```java
catalog.find("ma_golden_cross")              // 按 ID 查找
catalog.listAll()                            // 列出所有策略
catalog.listByCapability("backtest")         // 按能力筛选
catalog.getCategories()                      // 获取分类列表
catalog.getCapabilities()                    // 获取能力说明
```

支持热更新：`StrategyCatalogLoader.reload()` 可在运行时重新加载策略文件。

## 策略引擎实现

### BacktestSignalEngine（回测信号引擎）

在历史 K 线数据上逐日生成交易信号：

```java
int signal = engine.signal(strategy, data, i, inPosition, entryPrice, entryDay);
// 返回：1=买入信号，-1=卖出信号，0=无信号
```

**支持的条件类型（BacktestConditionEvaluator）：**

| 条件类型 | 说明 |
|---|---|
| `ma_cross` | 均线交叉（金叉/死叉） |
| `ma_position` | 均线位置（价格在均线上/下） |
| `volume_ratio` | 量比（当日量/N日均量） |
| `price_change` | 涨跌幅范围 |
| `rsi` | RSI 超买超卖 |
| `macd_cross` | MACD 金叉/死叉 |
| `stop_loss` | 固定百分比止损 |
| `stop_profit` | 固定百分比止盈 |
| `holding_days` | 持仓天数触发 |

### CompositeScoringEngine（综合评分引擎）

为单只股票计算当前所有策略的综合命中评分：

```
遍历所有 scoring 类型的策略
    ├── 检查每个 scoring.conditions 是否命中当前 K 线
    └── 命中条件累加权重分
最终输出：各策略分项评分 + 综合总分（0-100）
```

### ScreeningScoreEngine（选股评分引擎）

被 `AlphaSiftScreeningEngine` 调用，对全市场股票逐一打分：

```
AlphaSiftScreeningEngine
    └── 遍历全市场股票（可配置范围）
        ├── 获取每只股票的最新 K 线数据
        ├── 调用 ScreeningScoreEngine.score()
        └── 按总分排序，返回 Top N 结果
```

## 策略参数优化

回测系统支持多种高级优化方法：

### ParameterOptimizer（参数优化器）

网格搜索策略参数空间，找到历史最优参数组合：

```
定义参数范围：ma_fast ∈ [3,5,10], ma_slow ∈ [20,30,60]
    ↓
网格遍历所有参数组合
    ↓
对每组参数运行 BacktestSimulator
    ↓
按夏普比率排序，返回最优参数集
```

### WalkForwardValidator（前向验证器）

防止过度拟合，验证参数的样本外有效性：

```
历史数据分割：
  [训练集 1]→[测试集 1] → [训练集 2]→[测试集 2] → ...
每个窗口在训练集上优化参数，在测试集上验证
最终报告：样本内外表现对比，识别过拟合
```

### MonteCarloSimulator（蒙特卡洛模拟器）

通过随机模拟评估策略的统计稳健性：

```
对历史收益序列进行 N 次随机重排（N=1000）
    ↓
对每次重排计算最大回撤、总收益等指标
    ↓
输出指标的概率分布：95th/5th 分位数区间
```

### PortfolioBacktestService（多策略组合回测）

将多个策略组合为投资组合，计算组合层面的风险收益特征（相关性分散效应）。
